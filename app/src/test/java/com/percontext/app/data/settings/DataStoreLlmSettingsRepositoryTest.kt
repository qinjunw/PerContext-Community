package com.percontext.app.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLlmSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `custom endpoint model and encrypted key survive a new repository instance`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(temporaryFolder.root, "custom.preferences_pb") },
        )
        val first = DataStoreLlmSettingsRepository(dataStore, ReversingSecretCipher)
        first.save("custom", "private-test-key", "https://example.com/v1/", " model-one ")
        val restarted = DataStoreLlmSettingsRepository(dataStore, ReversingSecretCipher)
        val connection = restarted.connection()
        assertEquals("https://example.com/v1", connection.settings.baseUrl)
        assertEquals("model-one", connection.settings.model)
        assertEquals("private-test-key", connection.apiKey)
        assertFalse(File(temporaryFolder.root, "custom.preferences_pb").readText().contains("private-test-key"))
    }

    @Test
    fun `endpoint changes cannot silently reuse an old key and failed save preserves configuration`() = runTest {
        val repository = DataStoreLlmSettingsRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "binding.preferences_pb") }),
            ReversingSecretCipher,
        )
        repository.save("custom", "first-key", "https://first.example/v1", "one")
        val failure = runCatching { repository.save("custom", null, "https://second.example/v1", "two") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("https://first.example/v1", repository.connection().settings.baseUrl)
        assertEquals("first-key", repository.connection().apiKey)
        repository.save("custom", null, "https://first.example/v1/", "two")
        assertEquals("two", repository.current().model)
        assertEquals("first-key", repository.connection().apiKey)
        repository.save("custom", "second-key", "https://second.example/v1", "three")
        assertEquals("second-key", repository.connection().apiKey)
    }

    @Test
    fun `provider is plain setting while api key is recoverable only through cipher`() = runTest {
        val dataStoreFile = File(temporaryFolder.root, "settings.preferences_pb")
        val repository = DataStoreLlmSettingsRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { dataStoreFile },
            ),
            secretCipher = ReversingSecretCipher,
        )

        assertFalse(repository.current().hasApiKey)
        repository.save("deepseek", "  secret-key  ")

        assertTrue(repository.current().hasApiKey)
        assertEquals("deepseek", repository.current().providerId)
        assertEquals("secret-key", repository.apiKey("deepseek"))
        assertFalse(dataStoreFile.readBytes().toString(Charsets.UTF_8).contains("secret-key"))
    }

    @Test
    fun `blank key keeps the previously encrypted provider key`() = runTest {
        val repository = DataStoreLlmSettingsRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "keep.preferences_pb") },
            ),
            secretCipher = ReversingSecretCipher,
        )
        repository.save("deepseek", "first-key")

        repository.save("deepseek", " ")

        assertEquals("first-key", repository.apiKey("deepseek"))
    }

    @Test
    fun `clearing provider key removes the encrypted local secret`() = runTest {
        val repository = DataStoreLlmSettingsRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(temporaryFolder.root, "clear.preferences_pb") },
            ),
            secretCipher = ReversingSecretCipher,
        )
        repository.save("deepseek", "secret-key")

        repository.clearApiKey("deepseek")

        assertFalse(repository.current().hasApiKey)
        assertEquals(null, repository.apiKey("deepseek"))
    }
}

private object ReversingSecretCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray) = EncryptedSecret(
        ciphertext = plaintext.reversedArray(),
        iv = byteArrayOf(1, 2, 3),
    )

    override fun decrypt(secret: EncryptedSecret): ByteArray = secret.ciphertext.reversedArray()
}
