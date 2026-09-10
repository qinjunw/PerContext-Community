package com.percontext.app.service.recording

import java.util.concurrent.atomic.AtomicBoolean

internal class PcmInputShutdownRequester(
    private val stopInput: () -> Unit,
    private val releaseInput: () -> Unit,
    private val launchDaemon: (name: String, action: () -> Unit) -> Unit = ::launchShutdownDaemon,
) {
    private val stopRequested = AtomicBoolean(false)
    private val releaseRequested = AtomicBoolean(false)

    fun requestStop() {
        requestOnce(stopRequested, "PerContextPcmStop", stopInput)
    }

    fun requestRelease() {
        requestOnce(releaseRequested, "PerContextPcmRelease", releaseInput)
    }

    private fun requestOnce(
        requested: AtomicBoolean,
        name: String,
        action: () -> Unit,
    ) {
        if (!requested.compareAndSet(false, true)) return
        launchDaemon(name) { runCatching(action) }
    }
}

private fun launchShutdownDaemon(name: String, action: () -> Unit) {
    Thread(action, name).apply {
        isDaemon = true
        start()
    }
}
