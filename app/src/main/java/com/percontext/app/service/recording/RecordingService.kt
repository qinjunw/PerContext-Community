package com.percontext.app.service.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.percontext.app.BuildConfig
import com.percontext.app.PerContextApplication
import com.percontext.app.MainActivity
import com.percontext.app.R
import com.percontext.app.data.file.PendingAudioRecording
import com.percontext.app.domain.recording.RecordingEvent
import com.percontext.app.core.util.formatDuration
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var applicationContainer: com.percontext.app.AppContainer
    private var mediaRecorder: MediaRecorder? = null
    private var pcmRecordingEngine: PcmAacRecordingEngine? = null
    private val pcmRecordingSessions = PcmRecordingSessionRegistry()
    private var pendingRecording: PendingAudioRecording? = null
    private var startedAtMillis: Long = 0L
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        applicationContainer = (application as PerContextApplication).container
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        val pcmSession = pcmRecordingSessions.active
        if (pcmSession != null) {
            pcmSession.requestDestroy()
        } else if (mediaRecorder != null) {
            releaseRecorder()
            applicationContainer.recordingSessionStore.dispatch(
                RecordingEvent.Failed("录音被系统中断，原始文件已保留"),
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (mediaRecorder != null || pcmRecordingEngine != null) return

        applicationContainer.recordingSessionStore.dispatch(RecordingEvent.StartRequested)
        startMicrophoneForeground(buildNotification("正在准备录音"))

        val recordId = "record_${UUID.randomUUID()}"
        val target = runCatching {
            applicationContainer.audioFileStore.createPendingRecording(recordId)
        }.getOrElse { error ->
            failStart(error)
            return
        }

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startPcmRecording(target)
        } else {
            startMediaRecorder(target)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun startPcmRecording(target: PendingAudioRecording) {
        val engine = PcmAacRecordingEngine(
            outputFileDescriptor = target.fileDescriptor,
            onInputLevel = applicationContainer.recordingSessionStore::updateInputLevel,
        )
        runCatching { engine.start() }
            .onSuccess {
                pcmRecordingEngine = engine
                recordingStarted(target)
                val recordingStartedAt = startedAtMillis
                lateinit var session: PcmRecordingSessionOrchestrator
                session = PcmRecordingSessionOrchestrator(
                    engine = engine,
                    target = target,
                    scope = serviceScope,
                    ioDispatcher = Dispatchers.IO,
                    onSettling = { pcmRecordingSettling(session) },
                    persistStoppedRecording = { pending, stoppedAt ->
                        persistCompletedRecording(
                            target = pending,
                            startedAt = recordingStartedAt,
                            stoppedAt = stoppedAt,
                        )
                    },
                    onOutcome = { outcome ->
                        handlePcmRecordingOutcome(outcome)
                    },
                )
                pcmRecordingSessions.activate(session)
            }
            .onFailure { error ->
                engine.release()
                runCatching { target.discard() }
                failStart(error)
            }
    }

    private fun startMediaRecorder(target: PendingAudioRecording) {
        val recorder = createRecorder()
        runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(64_000)
                setOutputFile(target.fileDescriptor)
                prepare()
                start()
            }
        }.onSuccess {
            mediaRecorder = recorder
            recordingStarted(target)
        }.onFailure { error ->
            runCatching { recorder.release() }
            runCatching { target.discard() }
            failStart(error)
        }
    }

    private fun recordingStarted(target: PendingAudioRecording) {
        pendingRecording = target
        startedAtMillis = System.currentTimeMillis()
        applicationContainer.recordingSessionStore.dispatch(
            RecordingEvent.Started(startedAtMillis),
        )
        startNotificationTicker()
    }

    private fun stopRecording() {
        val pcmSession = pcmRecordingSessions.active
        if (pcmSession != null) {
            val stoppedAt = System.currentTimeMillis()
            if (pcmSession.requestStop(stoppedAt)) {
                applicationContainer.recordingSessionStore.dispatch(RecordingEvent.StopRequested)
            }
            return
        }

        val target = pendingRecording ?: return
        val recorder = mediaRecorder
        if (recorder == null) return
        val startedAt = startedAtMillis
        val stoppedAt = System.currentTimeMillis()

        applicationContainer.recordingSessionStore.dispatch(RecordingEvent.StopRequested)
        notificationJob?.cancel()
        mediaRecorder = null
        pendingRecording = null

        val stopResult = runCatching {
            val activeRecorder = checkNotNull(recorder)
            activeRecorder.stop()
            activeRecorder.release()
        }
        if (stopResult.isFailure) {
            runCatching { recorder?.release() }
            runCatching { target.publish() }
            finishWithFailure("录音未能正常结束，原始文件已保留")
            return
        }

        storeCompletedRecording(target, startedAt, stoppedAt)
    }

    private fun pcmRecordingSettling(session: PcmRecordingSession) {
        if (!pcmRecordingSessions.clear(session)) return
        notificationJob?.cancel()
        pcmRecordingEngine = null
        pendingRecording = null
    }

    private fun handlePcmRecordingOutcome(outcome: PcmRecordingSessionOutcome) {
        when (outcome) {
            is PcmRecordingSessionOutcome.Stopped -> {
                applicationContainer.recordingSessionStore.dispatch(RecordingEvent.Stored)
                finishService()
            }

            is PcmRecordingSessionOutcome.Failed -> {
                finishWithFailure("录音采集失败，未保存不完整文件")
            }

            is PcmRecordingSessionOutcome.PublishFailed -> {
                finishWithFailure("录音文件未能完成保存")
            }

            is PcmRecordingSessionOutcome.RepositoryFailed -> {
                finishWithFailure("录音文件已保留，但本地记录写入失败")
            }

            is PcmRecordingSessionOutcome.Interrupted -> {
                finishWithFailure("录音被系统中断，原始文件已保留")
            }
        }
    }

    private fun storeCompletedRecording(
        target: PendingAudioRecording,
        startedAt: Long,
        stoppedAt: Long,
    ) {
        serviceScope.launch {
            storeCompletedRecordingNow(target, startedAt, stoppedAt)
        }
    }

    private suspend fun storeCompletedRecordingNow(
        target: PendingAudioRecording,
        startedAt: Long,
        stoppedAt: Long,
    ) {
        when (persistCompletedRecording(target, startedAt, stoppedAt)) {
            CompletedRecordingWriteResult.Stored -> {
                applicationContainer.recordingSessionStore.dispatch(RecordingEvent.Stored)
                finishService()
            }

            CompletedRecordingWriteResult.PublishFailed -> {
                finishWithFailure("录音文件未能完成保存")
            }

            CompletedRecordingWriteResult.RepositoryFailed -> {
                finishWithFailure("录音文件已保留，但本地记录写入失败")
            }
        }
    }

    private suspend fun persistCompletedRecording(
        target: PendingAudioRecording,
        startedAt: Long,
        stoppedAt: Long,
    ): CompletedRecordingWriteResult =
        CompletedRecordingWriter(applicationContainer.voiceRecordRepository).store(
            target = target,
            startedAtMillis = startedAt,
            stoppedAtMillis = stoppedAt,
        )

    private fun startNotificationTicker() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startedAtMillis
                notificationManager().notify(
                    NOTIFICATION_ID,
                    buildNotification("正在录音 · ${formatDuration(elapsed)}"),
                )
                delay(1_000L)
            }
        }
    }

    private fun failStart(error: Throwable) {
        applicationContainer.recordingSessionStore.dispatch(
            RecordingEvent.Failed(error.toUserSafeRecordingError()),
        )
        finishService()
    }

    private fun finishWithFailure(reason: String) {
        applicationContainer.recordingSessionStore.dispatch(RecordingEvent.Failed(reason))
        finishService()
    }

    private fun finishService() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseRecorder() {
        val recorder = mediaRecorder
        val target = pendingRecording
        mediaRecorder = null
        pendingRecording = null
        runCatching { recorder?.release() }
        runCatching { target?.publish() }
    }

    private fun startMicrophoneForeground(notification: Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun buildNotification(content: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopRecording = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop_recording),
                stopRecording,
            )
            .build()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.recording_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.recording_channel_description)
            setSound(null, null)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(this)
    } else {
        MediaRecorder()
    }

    companion object {
        const val ACTION_START = "com.percontext.app.action.START_RECORDING"
        const val ACTION_STOP = "com.percontext.app.action.STOP_RECORDING"

        private const val NOTIFICATION_CHANNEL_ID = "active_recording"
        private const val NOTIFICATION_ID = 1001
    }
}

private fun Throwable.toUserSafeRecordingError(): String = when (this) {
    is SecurityException -> "没有麦克风权限，无法开始录音"
    is IllegalStateException -> "麦克风当前不可用"
    else -> "无法开始录音，请稍后重试"
}
