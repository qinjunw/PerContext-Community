package com.percontext.app.data.file

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.FileDescriptor

@RequiresApi(Build.VERSION_CODES.Q)
class MediaStoreAudioFileStore(
    private val resolver: ContentResolver,
) : AudioFileStore {
    private val collection = MediaStore.Downloads.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY,
    )

    override fun createPendingRecording(recordId: String): PendingAudioRecording {
        requireSafeRecordId(recordId)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$recordId.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, M4A_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RECORDINGS_RELATIVE_DIRECTORY)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values)) {
            "Cannot create pending recording"
        }
        val descriptor = try {
            checkNotNull(resolver.openFileDescriptor(uri, "rw")) {
                "Cannot open pending recording"
            }
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return MediaStorePendingAudioRecording(
            recordId = recordId,
            uri = uri,
            descriptor = descriptor,
            resolver = resolver,
        )
    }

    override fun deleteOwnedFile(audioLocation: String): Boolean =
        when (val lookup = lookupOwnedRecording(audioLocation)) {
            is OwnedRecordingLookup.Owned -> resolver.delete(lookup.uri, null, null) == 1
            OwnedRecordingLookup.Missing -> true
            OwnedRecordingLookup.Rejected -> false
        }

    override fun sizeOfOwnedFile(audioLocation: String): Long? {
        val uri = (lookupOwnedRecording(audioLocation) as? OwnedRecordingLookup.Owned)?.uri
            ?: return null
        directSize(uri)?.let { return it }
        return resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }
    }

    private fun directSize(uri: Uri): Long? = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.statSize.takeIf { it >= 0L }
        }
    }.getOrNull()

    private fun lookupOwnedRecording(audioLocation: String): OwnedRecordingLookup {
        val uri = Uri.parse(audioLocation)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT ||
            uri.authority != MediaStore.AUTHORITY ||
            !uri.toString().startsWith("$collection/") ||
            runCatching { ContentUris.parseId(uri) }.isFailure
        ) {
            return OwnedRecordingLookup.Rejected
        }
        val queryArguments = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        return resolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
            ),
            queryArguments,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use OwnedRecordingLookup.Missing
            val displayName = cursor.getString(0)
            val relativePath = cursor.getString(1).trimEnd('/')
            val recordId = displayName.removeSuffix(".m4a")
            if (displayName == "$recordId.m4a" &&
                recordId.startsWith(RECORD_ID_PREFIX) &&
                recordId.matches(SAFE_RECORD_ID) &&
                relativePath == RECORDINGS_RELATIVE_DIRECTORY
            ) {
                OwnedRecordingLookup.Owned(uri)
            } else {
                OwnedRecordingLookup.Rejected
            }
        } ?: OwnedRecordingLookup.Rejected
    }
}

private sealed interface OwnedRecordingLookup {
    data class Owned(val uri: Uri) : OwnedRecordingLookup

    data object Missing : OwnedRecordingLookup

    data object Rejected : OwnedRecordingLookup
}

@RequiresApi(Build.VERSION_CODES.Q)
private class MediaStorePendingAudioRecording(
    override val recordId: String,
    private val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
    private val resolver: ContentResolver,
) : PendingAudioRecording {
    private var descriptorClosed = false
    private var published = false

    override val audioLocation: String = uri.toString()
    override val fileDescriptor: FileDescriptor = descriptor.fileDescriptor

    override fun publish() {
        closeDescriptor()
        if (published) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        check(resolver.update(uri, values, null, null) == 1) {
            "Cannot publish pending recording"
        }
        published = true
    }

    override fun discard() {
        closeDescriptor()
        check(resolver.delete(uri, null, null) == 1) {
            "Cannot discard pending recording"
        }
    }

    private fun closeDescriptor() {
        if (descriptorClosed) return
        descriptor.close()
        descriptorClosed = true
    }
}
