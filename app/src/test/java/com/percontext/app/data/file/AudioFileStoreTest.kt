package com.percontext.app.data.file

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `creates m4a output inside recordings directory`() {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val store = FileAudioFileStore(recordingsDirectory)

        val output = store.createPendingRecording("record_123")

        assertTrue(
            File(output.audioLocation).parentFile?.canonicalFile ==
                recordingsDirectory.canonicalFile,
        )
        assertTrue(File(output.audioLocation).name == "record_123.m4a")
        output.discard()
    }

    @Test
    fun `deletes file owned by recordings directory`() {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val store = FileAudioFileStore(recordingsDirectory)
        val output = store.createPendingRecording("record_123")
        File(output.audioLocation).writeBytes(byteArrayOf(1))
        output.publish()

        val deleted = store.deleteOwnedFile(output.audioLocation)

        assertTrue(deleted)
        assertFalse(File(output.audioLocation).exists())
    }

    @Test
    fun `refuses to delete file outside recordings directory`() {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val outside = temporaryFolder.newFile("outside.m4a").apply { writeBytes(byteArrayOf(1)) }
        val store = FileAudioFileStore(recordingsDirectory)

        val deleted = store.deleteOwnedFile(outside.absolutePath)

        assertFalse(deleted)
        assertTrue(outside.exists())
    }

    @Test
    fun `reads size only for an owned audio file`() {
        val recordingsDirectory = temporaryFolder.newFolder("recordings")
        val store = FileAudioFileStore(recordingsDirectory)
        val owned = store.createPendingRecording("record_123")
        File(owned.audioLocation).writeBytes(byteArrayOf(1, 2, 3))
        owned.publish()
        val outside = temporaryFolder.newFile("outside.m4a")

        assertEquals(3L, store.sizeOfOwnedFile(owned.audioLocation))
        assertNull(store.sizeOfOwnedFile(outside.absolutePath))
    }

}
