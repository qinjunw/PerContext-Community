package com.percontext.app.data.provider

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class SenseVoiceModelInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `matching installed pack is reused without reading bundle`() {
        val installationRoot = temporaryFolder.newFolder("models")
        writePack(installationRoot.resolve(PACK.directoryName), VALID_FILES)
        val bundle = FakeModelBundle(emptyMap())
        val installer = installer(installationRoot, bundle)

        val ready = installer.prepare()

        assertEquals(0, bundle.openCount)
        assertArrayEquals(VALID_FILES.getValue(MODEL), ready.model.readBytes())
        assertArrayEquals(VALID_FILES.getValue(TOKENS), ready.tokens.readBytes())
    }

    @Test
    fun `missing bundled file fails without publishing pack`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val bundle = FakeModelBundle(VALID_FILES - TOKENS)
        val installer = installer(installationRoot, bundle)

        val result = runCatching(installer::prepare)

        assertTrue(result.isFailure)
        assertFalse(installationRoot.resolve(PACK.directoryName).exists())
    }

    @Test
    fun `bundled hash mismatch fails without publishing pack`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val invalidFiles = VALID_FILES + (MODEL to "modem".toByteArray())
        val installer = installer(installationRoot, FakeModelBundle(invalidFiles))

        val result = runCatching(installer::prepare)

        assertTrue(result.isFailure)
        assertFalse(installationRoot.resolve(PACK.directoryName).exists())
    }

    @Test
    fun `corrupt installed pack is replaced from verified bundle`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val installed = installationRoot.resolve(PACK.directoryName)
        writePack(installed, VALID_FILES + (MODEL to "modem".toByteArray()))
        val installer = installer(installationRoot, FakeModelBundle(VALID_FILES))

        val ready = installer.prepare()

        assertArrayEquals(VALID_FILES.getValue(MODEL), ready.model.readBytes())
        assertTrue(installer.isInstalledPackReady())
    }

    @Test
    fun `interrupted temporary copy is discarded before retry`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val temporary = installationRoot.resolve(".${PACK.directoryName}.installing")
        writePack(temporary, mapOf(MODEL to VALID_FILES.getValue(MODEL)))
        val installer = installer(installationRoot, FakeModelBundle(VALID_FILES))

        installer.prepare()

        assertFalse(temporary.exists())
        assertTrue(installer.isInstalledPackReady())
    }

    @Test
    fun `verified temporary pack is published only after all files are complete`() {
        val installationRoot = temporaryFolder.newFolder("models")
        var publishCount = 0
        val publisher = AtomicDirectoryPublisher { source, target ->
            publishCount += 1
            assertFalse(target.exists())
            assertPackEquals(source, VALID_FILES)
            check(source.renameTo(target))
        }
        val installer = SenseVoiceModelInstaller(
            installationRoot = installationRoot,
            bundle = FakeModelBundle(VALID_FILES),
            pack = PACK,
            publisher = publisher,
        )

        val ready = installer.prepare()

        assertEquals(1, publishCount)
        assertTrue(installer.isInstalledPackReady())
        assertArrayEquals(VALID_FILES.getValue(MODEL), ready.model.readBytes())
        assertArrayEquals(VALID_FILES.getValue(TOKENS), ready.tokens.readBytes())
    }

    @Test
    fun `same length corruption is replaced before returning model files`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val bundle = FakeModelBundle(VALID_FILES)
        val installer = installer(installationRoot, bundle)

        installer.prepare()
        val openCountAfterInstall = bundle.openCount
        assertEquals(PACK.artifacts.size, openCountAfterInstall)
        installationRoot.resolve(PACK.directoryName).resolve(MODEL)
            .writeBytes("modem".toByteArray())

        val ready = installer.prepare()

        assertEquals(openCountAfterInstall + PACK.artifacts.size, bundle.openCount)
        assertArrayEquals(VALID_FILES.getValue(MODEL), ready.model.readBytes())
        assertTrue(installer.isInstalledPackReady())
    }

    @Test
    fun `input failure during copy removes partial temporary pack`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val bundle = SenseVoiceModelBundle { fileName ->
            if (fileName == MODEL) FailingInputStream(VALID_FILES.getValue(MODEL))
            else VALID_FILES.getValue(fileName).inputStream()
        }
        val installer = installer(installationRoot, bundle)

        val result = runCatching(installer::prepare)

        assertTrue(result.isFailure)
        assertFalse(installationRoot.resolve(PACK.directoryName).exists())
        assertFalse(installationRoot.resolve(".${PACK.directoryName}.installing").exists())
    }

    @Test
    fun `publisher failure removes partial target and temporary pack`() {
        val installationRoot = temporaryFolder.newFolder("models")
        val installer = SenseVoiceModelInstaller(
            installationRoot = installationRoot,
            bundle = FakeModelBundle(VALID_FILES),
            pack = PACK,
            publisher = AtomicDirectoryPublisher { source, target ->
                check(target.mkdir())
                source.resolve(MODEL).copyTo(target.resolve(MODEL))
                throw IOException("publish failed")
            },
        )

        val result = runCatching(installer::prepare)

        assertTrue(result.isFailure)
        assertFalse(installationRoot.resolve(PACK.directoryName).exists())
        assertFalse(installationRoot.resolve(".${PACK.directoryName}.installing").exists())
    }

    @Test
    fun `production pack pins complete model license and notice`() {
        val legalArtifacts = SenseVoiceModelContract.pack.artifacts
            .filter { it.fileName == "MODEL_LICENSE" || it.fileName == "NOTICE" }

        assertEquals(
            listOf(
                SenseVoiceModelArtifact(
                    fileName = "MODEL_LICENSE",
                    length = 5_306L,
                    sha256 = "7dba975a2069691db4992b0592d70828b330d2f8a30a71450f4e152a554e84f8",
                ),
                SenseVoiceModelArtifact(
                    fileName = "NOTICE",
                    length = 744L,
                    sha256 = "7e469c226b755d6ac46e3397023dd5950ca06e13bfae5ac831d07f4b855c18e5",
                ),
            ),
            legalArtifacts,
        )
    }

    private fun installer(
        installationRoot: File,
        bundle: SenseVoiceModelBundle,
    ) = SenseVoiceModelInstaller(
        installationRoot = installationRoot,
        bundle = bundle,
        pack = PACK,
        publisher = AtomicDirectoryPublisher { source, target ->
            check(source.renameTo(target))
        },
    )

    private fun writePack(directory: File, files: Map<String, ByteArray>) {
        check(directory.mkdirs() || directory.isDirectory)
        files.forEach { (name, content) -> directory.resolve(name).writeBytes(content) }
    }

    private fun assertPackEquals(directory: File, expected: Map<String, ByteArray>) {
        assertEquals(expected.keys, directory.listFiles().orEmpty().map(File::getName).toSet())
        expected.forEach { (name, content) ->
            assertArrayEquals(content, directory.resolve(name).readBytes())
        }
    }

    private class FakeModelBundle(
        private val files: Map<String, ByteArray>,
    ) : SenseVoiceModelBundle {
        var openCount: Int = 0

        override fun open(fileName: String): InputStream {
            openCount += 1
            return files[fileName]?.inputStream() ?: throw FileNotFoundException(fileName)
        }
    }

    private class FailingInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {
        private var index = 0

        override fun read(): Int {
            if (index >= bytes.size / 2) throw IOException("interrupted read")
            return bytes[index++].toInt() and 0xff
        }
    }

    private companion object {
        const val MODEL = "model.int8.onnx"
        const val TOKENS = "tokens.txt"
        const val LICENSE = "LICENSE"

        val VALID_FILES = mapOf(
            MODEL to "model".toByteArray(),
            TOKENS to "tokens".toByteArray(),
            LICENSE to "license".toByteArray(),
        )
        val PACK = SenseVoiceModelPack(
            directoryName = "fixture-pack",
            assetDirectory = "sensevoice/fixture-pack",
            artifacts = VALID_FILES.map { (name, content) ->
                SenseVoiceModelArtifact(
                    fileName = name,
                    length = content.size.toLong(),
                    sha256 = sha256(content),
                )
            },
        )

        fun sha256(content: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(content)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
