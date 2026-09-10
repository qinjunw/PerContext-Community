package com.percontext.app.data.file

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreAudioFileStoreTest {
    @Test
    fun pendingRecordingPublishesInsidePerContextDownloadsDirectory() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = MediaStoreAudioFileStore(context.contentResolver)
        val recordId = "record_${UUID.randomUUID()}"
        val pending = store.createPendingRecording(recordId)
        var deleted = false

        try {
            val duplicate = ParcelFileDescriptor.dup(pending.fileDescriptor)
            ParcelFileDescriptor.AutoCloseOutputStream(duplicate).use { output ->
                output.write(byteArrayOf(1, 2, 3, 4))
            }
            pending.publish()

            assertTrue(pending.audioLocation.startsWith("content://media/"))
            assertEquals(4L, store.sizeOfOwnedFile(pending.audioLocation))
            context.contentResolver.query(
                android.net.Uri.parse(pending.audioLocation),
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.IS_PENDING,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("$recordId.m4a", cursor.getString(0))
                assertEquals(
                    "${Environment.DIRECTORY_DOWNLOADS}/PerContext/Recordings/",
                    cursor.getString(1),
                )
                assertEquals(0, cursor.getInt(2))
            } ?: error("Published recording is missing from MediaStore")
            assertTrue(store.deleteOwnedFile(pending.audioLocation))
            assertTrue(store.deleteOwnedFile(pending.audioLocation))
            deleted = true
        } finally {
            if (!deleted) store.deleteOwnedFile(pending.audioLocation)
        }
    }
}
