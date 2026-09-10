package com.percontext.app.data.file

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileDescriptor

interface AudioFileStore {
    fun createPendingRecording(recordId: String): PendingAudioRecording

    fun deleteOwnedFile(audioLocation: String): Boolean

    fun sizeOfOwnedFile(audioLocation: String): Long?

}

interface PendingAudioRecording {
    val recordId: String
    val audioLocation: String
    val fileDescriptor: FileDescriptor

    fun publish()

    fun discard()
}

fun createAudioFileStore(context: Context): AudioFileStore =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStoreAudioFileStore(context.contentResolver)
    } else {
        @Suppress("DEPRECATION")
        FileAudioFileStore(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                RECORDINGS_RELATIVE_DIRECTORY.substringAfter('/'),
            ),
        )
    }

internal fun requireSafeRecordId(recordId: String) {
    require(recordId.matches(SAFE_RECORD_ID)) { "Record ID contains unsupported characters" }
}

internal const val RECORDINGS_RELATIVE_DIRECTORY = "Download/PerContext/Recordings"
internal const val M4A_MIME_TYPE = "audio/mp4"
internal const val URI_SCHEME_SEPARATOR = "://"
internal const val RECORD_ID_PREFIX = "record_"
internal val SAFE_RECORD_ID = Regex("[A-Za-z0-9_-]+")
