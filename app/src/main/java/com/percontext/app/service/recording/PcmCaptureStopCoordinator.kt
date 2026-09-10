package com.percontext.app.service.recording

internal class PcmCaptureStopCoordinator(
    private val requestStop: () -> Unit,
    private val awaitCompletion: (timeoutMillis: Long) -> Boolean,
    private val forceRelease: () -> Unit,
    private val relinquishOwnership: () -> Unit,
    private val gracefulTimeoutMillis: Long,
    private val forcedTimeoutMillis: Long,
) {
    fun stop() {
        try {
            val completedGracefully = runCatching {
                requestStop()
                awaitCompletion(gracefulTimeoutMillis)
            }.getOrDefault(false)
            if (!completedGracefully) {
                val forceFailure = runCatching { forceRelease() }.exceptionOrNull()
                check(runCatching { awaitCompletion(forcedTimeoutMillis) }.getOrDefault(false)) {
                    "PCM recording capture did not terminate after forced release"
                }
                forceFailure?.let { throw it }
            }
        } finally {
            relinquishOwnership()
        }
    }
}
