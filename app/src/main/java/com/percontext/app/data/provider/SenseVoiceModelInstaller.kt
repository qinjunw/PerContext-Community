package com.percontext.app.data.provider

import android.content.res.AssetManager
import android.system.Os
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class SenseVoiceModelArtifact(
    val fileName: String,
    val length: Long,
    val sha256: String,
)

internal data class SenseVoiceModelPack(
    val directoryName: String,
    val assetDirectory: String,
    val artifacts: List<SenseVoiceModelArtifact>,
)

internal fun interface SenseVoiceModelBundle {
    fun open(fileName: String): InputStream
}

internal fun interface AtomicDirectoryPublisher {
    fun publish(source: File, target: File)
}

internal class SenseVoiceModelInstaller(
    private val installationRoot: File,
    private val bundle: SenseVoiceModelBundle,
    private val pack: SenseVoiceModelPack,
    private val publisher: AtomicDirectoryPublisher,
) {
    private val installedDirectory = installationRoot.resolve(pack.directoryName)
    private val temporaryDirectory = installationRoot.resolve(".${pack.directoryName}.installing")

    @Synchronized
    fun prepare(): SenseVoiceModelFiles {
        if (isInstalledPackReady()) return installedFiles()

        check(installationRoot.mkdirs() || installationRoot.isDirectory) {
            "Cannot prepare SenseVoice model storage"
        }
        removeDirectory(temporaryDirectory)
        check(temporaryDirectory.mkdir()) {
            "Cannot create SenseVoice model temporary storage"
        }

        var publishAttempted = false
        var published = false
        try {
            pack.artifacts.forEach(::copyBundledArtifact)
            check(isPackShapeReady(temporaryDirectory)) {
                "SenseVoice model temporary pack failed verification"
            }

            removeDirectory(installedDirectory)
            publishAttempted = true
            publisher.publish(temporaryDirectory, installedDirectory)
            check(isPackShapeReady(installedDirectory)) {
                "Published SenseVoice model pack failed verification"
            }
            published = true
            return installedFiles()
        } finally {
            if (!published) {
                temporaryDirectory.deleteRecursively()
                if (publishAttempted) installedDirectory.deleteRecursively()
            }
        }
    }

    @Synchronized
    fun isInstalledPackReady(): Boolean = isPackReady(installedDirectory)

    fun isBundleAvailable(): Boolean = pack.artifacts.all { artifact ->
        runCatching { bundle.open(artifact.fileName).use { } }.isSuccess
    }

    private fun copyBundledArtifact(artifact: SenseVoiceModelArtifact) {
        val destination = temporaryDirectory.resolve(artifact.fileName)
        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        try {
            bundle.open(artifact.fileName).buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copiedBytes += count
                    }
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException(
                "SenseVoice bundle is missing or unreadable: ${artifact.fileName}",
                error,
            )
        }
        val copiedSha256 = digest.digest().toHexString()
        check(copiedBytes == artifact.length && copiedSha256 == artifact.sha256) {
            "SenseVoice bundle verification failed: ${artifact.fileName}"
        }
    }

    private fun isPackReady(directory: File): Boolean = directory.isDirectory &&
        pack.artifacts.all { artifact ->
            isArtifactReady(directory.resolve(artifact.fileName), artifact)
        }

    private fun isPackShapeReady(directory: File): Boolean = directory.isDirectory &&
        pack.artifacts.all { artifact ->
            val file = directory.resolve(artifact.fileName)
            file.isFile && file.length() == artifact.length
        }

    private fun isArtifactReady(file: File, artifact: SenseVoiceModelArtifact): Boolean =
        file.isFile &&
            file.length() == artifact.length &&
            runCatching { file.sha256() == artifact.sha256 }.getOrDefault(false)

    private fun installedFiles(): SenseVoiceModelFiles = SenseVoiceModelFiles(
        model = installedDirectory.resolve(SenseVoiceModelContract.MODEL_FILE_NAME),
        tokens = installedDirectory.resolve(SenseVoiceModelContract.TOKENS_FILE_NAME),
    )

    private fun removeDirectory(directory: File) {
        check(!directory.exists() || directory.deleteRecursively()) {
            "Cannot replace existing SenseVoice model storage"
        }
    }
}

internal class AssetSenseVoiceModelBundle(
    private val assetManager: AssetManager,
    private val assetDirectory: String,
) : SenseVoiceModelBundle {
    override fun open(fileName: String): InputStream = assetManager.open(
        "$assetDirectory/$fileName",
        AssetManager.ACCESS_STREAMING,
    )
}

internal object AndroidAtomicDirectoryPublisher : AtomicDirectoryPublisher {
    override fun publish(source: File, target: File) {
        Os.rename(source.absolutePath, target.absolutePath)
    }
}

internal object SenseVoiceModelContract {
    const val PACK_DIRECTORY =
        "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17"
    const val ASSET_DIRECTORY = "sensevoice/$PACK_DIRECTORY"
    const val MODEL_FILE_NAME = "model.int8.onnx"
    const val TOKENS_FILE_NAME = "tokens.txt"
    const val MODEL_LICENSE_FILE_NAME = "MODEL_LICENSE"
    const val NOTICE_FILE_NAME = "NOTICE"

    val pack = SenseVoiceModelPack(
        directoryName = PACK_DIRECTORY,
        assetDirectory = ASSET_DIRECTORY,
        artifacts = listOf(
            SenseVoiceModelArtifact(
                fileName = MODEL_FILE_NAME,
                length = 239_233_841L,
                sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            ),
            SenseVoiceModelArtifact(
                fileName = TOKENS_FILE_NAME,
                length = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            ),
            SenseVoiceModelArtifact(
                fileName = MODEL_LICENSE_FILE_NAME,
                length = 5_306L,
                sha256 = "7dba975a2069691db4992b0592d70828b330d2f8a30a71450f4e152a554e84f8",
            ),
            SenseVoiceModelArtifact(
                fileName = NOTICE_FILE_NAME,
                length = 744L,
                sha256 = "7e469c226b755d6ac46e3397023dd5950ca06e13bfae5ac831d07f4b855c18e5",
            ),
        ),
    )
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte) }
