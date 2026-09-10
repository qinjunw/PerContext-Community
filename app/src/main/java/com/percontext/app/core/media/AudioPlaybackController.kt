package com.percontext.app.core.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.percontext.app.domain.model.VoiceRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val recordId: String? = null,
    val isPlaying: Boolean = false,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val error: String? = null,
)

interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun toggle(record: VoiceRecord)

    fun seekTo(recordId: String, fraction: Float)

    fun stop(recordId: String)

    fun release()
}

class AudioPlaybackController(context: Context) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private val mutableState = MutableStateFlow(PlaybackState())
    private var progressJob: Job? = null
    private var selectedDurationMillis: Long = 0L

    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState(isPlaying = isPlaying)
                    if (isPlaying) startProgressUpdates() else progressJob?.cancel()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    mutableState.value = mutableState.value.copy(
                        isPlaying = false,
                        error = "无法播放这段录音",
                    )
                }
            },
        )
    }

    override fun toggle(record: VoiceRecord) {
        if (mutableState.value.recordId != record.id) {
            selectedDurationMillis = record.durationMillis
            mutableState.value = PlaybackState(
                recordId = record.id,
                durationMillis = record.durationMillis,
            )
            player.setMediaItem(MediaItem.fromUri(record.audioLocation.toUri()))
            player.prepare()
            player.play()
            return
        }

        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }

    override fun seekTo(recordId: String, fraction: Float) {
        if (mutableState.value.recordId != recordId) return
        val duration = resolvedDuration()
        if (duration <= 0L) return

        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
        updateState()
    }

    override fun stop(recordId: String) {
        if (mutableState.value.recordId != recordId) return
        player.stop()
        player.clearMediaItems()
        mutableState.value = PlaybackState()
    }

    override fun release() {
        progressJob?.cancel()
        player.release()
        scope.cancel()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                updateState()
                delay(250L)
            }
        }
    }

    private fun updateState(isPlaying: Boolean = player.isPlaying) {
        val current = mutableState.value
        if (current.recordId == null) return
        mutableState.value = current.copy(
            isPlaying = isPlaying,
            positionMillis = player.currentPosition.coerceAtLeast(0L),
            durationMillis = resolvedDuration(),
        )
    }

    private fun resolvedDuration(): Long = player.duration
        .takeIf { it > 0L }
        ?: selectedDurationMillis
}
