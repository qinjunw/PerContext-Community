package com.percontext.app.feature.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCloseTest {
    @Test
    fun `closing settings clears transient secret before hiding the screen`() {
        val events = mutableListOf<String>()

        performSettingsClose(
            onScreenClosed = { events += "secret-cleared" },
            closeScreen = { events += "screen-closed" },
        )

        assertEquals(listOf("secret-cleared", "screen-closed"), events)
    }
}
