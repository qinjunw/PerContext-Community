package com.percontext.app.data.provider

import android.content.Context
import java.io.File

class SenseVoiceModelStore internal constructor(
    private val modelDirectory: File,
    private val installer: SenseVoiceModelInstaller? = null,
) {
    fun requireReady(): SenseVoiceModelFiles {
        installer?.let { return it.prepare() }
        val model = File(modelDirectory, MODEL_FILE_NAME)
        val tokens = File(modelDirectory, TOKENS_FILE_NAME)
        check(model.isFile && model.length() > 0L) {
            "SenseVoice model pack is missing $MODEL_FILE_NAME"
        }
        check(tokens.isFile && tokens.length() > 0L) {
            "SenseVoice model pack is missing $TOKENS_FILE_NAME"
        }
        return SenseVoiceModelFiles(model = model, tokens = tokens)
    }

    fun isReady(): Boolean = installer?.let {
        it.isBundleAvailable() || it.isInstalledPackReady()
    } ?: runCatching(::requireReady).isSuccess

    companion object {
        const val PACK_DIRECTORY = SenseVoiceModelContract.PACK_DIRECTORY
        private const val MODEL_FILE_NAME = SenseVoiceModelContract.MODEL_FILE_NAME
        private const val TOKENS_FILE_NAME = SenseVoiceModelContract.TOKENS_FILE_NAME

        fun create(context: Context): SenseVoiceModelStore {
            val modelsRoot = File(context.filesDir, "models")
            val pack = SenseVoiceModelContract.pack
            val installer = SenseVoiceModelInstaller(
                installationRoot = modelsRoot,
                bundle = AssetSenseVoiceModelBundle(
                    assetManager = context.assets,
                    assetDirectory = pack.assetDirectory,
                ),
                pack = pack,
                publisher = AndroidAtomicDirectoryPublisher,
            )
            return SenseVoiceModelStore(
                modelDirectory = File(modelsRoot, PACK_DIRECTORY),
                installer = installer,
            )
        }
    }
}

data class SenseVoiceModelFiles(
    val model: File,
    val tokens: File,
)
