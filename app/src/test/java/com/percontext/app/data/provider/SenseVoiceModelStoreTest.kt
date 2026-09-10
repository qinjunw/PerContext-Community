package com.percontext.app.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SenseVoiceModelStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `model pack is ready only when model and tokens are present`() {
        val directory = temporaryFolder.newFolder("sensevoice")
        val store = SenseVoiceModelStore(directory)

        assertFalse(store.isReady())
        directory.resolve("model.int8.onnx").writeBytes(byteArrayOf(1))
        assertFalse(store.isReady())
        directory.resolve("tokens.txt").writeText("token")

        assertTrue(store.isReady())
    }
}
