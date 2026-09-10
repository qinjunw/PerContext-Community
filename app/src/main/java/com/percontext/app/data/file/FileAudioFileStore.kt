package com.percontext.app.data.file

import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile

class FileAudioFileStore(
    private val recordingsDirectory: File,
) : AudioFileStore {
    override fun createPendingRecording(recordId: String): PendingAudioRecording {
        requireSafeRecordId(recordId)
        check(recordingsDirectory.exists() || recordingsDirectory.mkdirs()) {
            "Recording directory is unavailable"
        }
        check(recordingsDirectory.isDirectory) { "Recording path is not a directory" }

        val file = File(recordingsDirectory, "$recordId.m4a")
        val descriptor = RandomAccessFile(file, "rw").apply { setLength(0L) }
        return FilePendingAudioRecording(recordId, file, descriptor)
    }

    override fun deleteOwnedFile(audioLocation: String): Boolean {
        val target = ownedFile(audioLocation) ?: return false
        return !target.exists() || target.delete()
    }

    override fun sizeOfOwnedFile(audioLocation: String): Long? {
        val target = ownedFile(audioLocation) ?: return null
        return target.takeIf(File::isFile)?.length()
    }

    private fun ownedFile(audioLocation: String): File? {
        if (audioLocation.contains(URI_SCHEME_SEPARATOR)) return null
        val root = recordingsDirectory.canonicalFile
        val target = File(audioLocation).canonicalFile
        return target.takeIf { it.parentFile == root }
    }
}

private class FilePendingAudioRecording(
    override val recordId: String,
    private val file: File,
    private val descriptor: RandomAccessFile,
) : PendingAudioRecording {
    private var descriptorClosed = false

    override val audioLocation: String = file.absolutePath
    override val fileDescriptor: FileDescriptor = descriptor.fd

    override fun publish() {
        closeDescriptor()
    }

    override fun discard() {
        closeDescriptor()
        check(!file.exists() || file.delete()) { "Cannot discard pending recording" }
    }

    private fun closeDescriptor() {
        if (descriptorClosed) return
        descriptor.close()
        descriptorClosed = true
    }
}
