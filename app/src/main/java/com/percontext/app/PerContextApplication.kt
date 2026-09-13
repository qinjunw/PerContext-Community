package com.percontext.app

import android.app.Application
import android.content.Context
import com.percontext.app.core.media.AndroidPcmAudioDecoder
import com.percontext.app.data.db.PerContextDatabase
import com.percontext.app.data.file.createAudioFileStore
import com.percontext.app.data.llm.OpenAiCompatibleLlmProvider
import com.percontext.app.data.llm.createLlmHttpClient
import com.percontext.app.data.provider.SenseVoiceAsrProvider
import com.percontext.app.data.provider.SenseVoiceModelStore
import com.percontext.app.data.repository.RoomDailyContextRepository
import com.percontext.app.data.repository.RoomDailyContextSourceRepository
import com.percontext.app.data.repository.RoomTranscriptRepository
import com.percontext.app.data.repository.RoomVoiceRecordRepository
import com.percontext.app.data.settings.createAppearanceSettingsRepository
import com.percontext.app.data.settings.createLlmSettingsRepository
import com.percontext.app.domain.dailycontext.GenerateDailyContextUseCase
import com.percontext.app.domain.dailycontext.RequestDailyContextUseCase
import com.percontext.app.domain.asr.RequestTranscriptionUseCase
import com.percontext.app.domain.asr.SingleSessionAsrProvider
import com.percontext.app.domain.asr.TranscribeRecordUseCase
import com.percontext.app.domain.recording.DeleteRecordingUseCase
import com.percontext.app.domain.recording.RecordingFeatureGateway
import com.percontext.app.domain.llm.LlmProviderPresets
import com.percontext.app.domain.repository.TranscriptRepository
import com.percontext.app.feature.review.ReviewFeatureGateway
import com.percontext.app.feature.review.ReviewRecordingSummary
import com.percontext.app.feature.settings.ProviderSettingsGateway
import com.percontext.app.service.dailycontext.WorkManagerDailyContextScheduler
import com.percontext.app.service.asr.WorkManagerTranscriptionScheduler
import com.percontext.app.service.recording.RecordingServiceController
import com.percontext.app.service.recording.RecordingSessionStore
import kotlinx.coroutines.flow.map

class PerContextApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(
    context: Context,
) : RecordingFeatureGateway, ReviewFeatureGateway, ProviderSettingsGateway {
    val recordingSessionStore = RecordingSessionStore()
    val audioFileStore = createAudioFileStore(context)
    override val recordingSession = recordingSessionStore.session
    override val recordingInputLevel = recordingSessionStore.inputLevel

    private val database = PerContextDatabase.create(context)
    val voiceRecordRepository = RoomVoiceRecordRepository(
        recordDao = database.recordDao(),
        audioFileStore = audioFileStore,
    )
    override val records = voiceRecordRepository.records
    internal val transcriptRepository: TranscriptRepository =
        RoomTranscriptRepository(database.transcriptDao())
    private val senseVoiceModelStore by lazy { SenseVoiceModelStore.create(context) }
    private val transcribeRecordUseCase by lazy {
        val asrProvider = SingleSessionAsrProvider(
            delegate = SenseVoiceAsrProvider(
                audioDecoder = AndroidPcmAudioDecoder(context),
                modelStore = senseVoiceModelStore,
            ),
            recordingSession = recordingSessionStore.session,
        )
        TranscribeRecordUseCase(
            voiceRecordRepository = voiceRecordRepository,
            transcriptRepository = transcriptRepository,
            asrProvider = asrProvider,
        )
    }
    private val transcriptionScheduler = WorkManagerTranscriptionScheduler(context)
    private val deleteRecordingUseCase = DeleteRecordingUseCase(
        voiceRecordRepository = voiceRecordRepository,
        transcriptionCanceller = transcriptionScheduler,
    )
    private val requestTranscriptionUseCase = RequestTranscriptionUseCase(
        transcriptRepository = transcriptRepository,
        transcriptionScheduler = transcriptionScheduler,
    )
    private val recordingServiceController = RecordingServiceController(context)
    private val dailyContextRepository = RoomDailyContextRepository(database.dailyContextDao())
    private val dailyContextSourceRepository =
        RoomDailyContextSourceRepository(database.dailyContextSourceDao())
    private val llmSettingsRepository = createLlmSettingsRepository(context)
    val appearanceSettingsRepository = createAppearanceSettingsRepository(context)
    private val dailyContextScheduler = WorkManagerDailyContextScheduler(context)
    private val generateDailyContextUseCase by lazy {
        GenerateDailyContextUseCase(
            contextRepository = dailyContextRepository,
            sourceRepository = dailyContextSourceRepository,
            llmProvider = OpenAiCompatibleLlmProvider(
                httpClient = createLlmHttpClient(),
                settingsRepository = llmSettingsRepository,
            ),
        )
    }
    private val requestDailyContextUseCase = RequestDailyContextUseCase(
        contextRepository = dailyContextRepository,
        sourceRepository = dailyContextSourceRepository,
        settingsRepository = llmSettingsRepository,
        scheduler = dailyContextScheduler,
    )

    override val dailyContexts = dailyContextRepository.contexts
    override val transcribedRecords = dailyContextSourceRepository.transcribedRecords
    override val recordingSummaries = voiceRecordRepository.records.map { records ->
        records.map { record ->
            ReviewRecordingSummary(
                recordId = record.id,
                recordedAtMillis = record.createdAtMillis,
                durationMillis = record.durationMillis,
                transcriptStatus = record.transcriptStatus,
            )
        }
    }
    override val providerPresets = LlmProviderPresets.all
    override val providerSettings = llmSettingsRepository.settings

    override fun startRecording() {
        recordingServiceController.start()
    }

    override fun stopRecording() {
        recordingServiceController.stop()
    }

    override fun isTranscriptionAvailable(): Boolean =
        runCatching { senseVoiceModelStore.isReady() }.getOrDefault(false)

    override suspend fun requestTranscription(recordId: String): Boolean =
        requestTranscriptionUseCase(recordId)

    override suspend fun deleteRecording(recordId: String): Boolean =
        deleteRecordingUseCase(recordId)

    override suspend fun requestDailyContext(
        dayKey: String,
        zoneId: String,
        allowChangedInput: Boolean,
    ) = requestDailyContextUseCase(dayKey, zoneId, allowChangedInput)

    override suspend fun saveProviderSettings(providerId: String, apiKey: String?, baseUrl: String, model: String) =
        llmSettingsRepository.save(providerId, apiKey, baseUrl, model)

    override suspend fun providerApiKey(providerId: String): String? =
        llmSettingsRepository.apiKey(providerId)

    override suspend fun clearProviderApiKey(providerId: String) =
        llmSettingsRepository.clearApiKey(providerId)

    suspend fun transcribeRecord(recordId: String) = transcribeRecordUseCase(recordId)

    suspend fun generateDailyContext(dayKey: String, zoneId: String) =
        generateDailyContextUseCase(dayKey, zoneId)
}
