package com.percontext.app.data.db

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerContextDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath(TEST_DATABASE_NAME),
        driver = AndroidSQLiteDriver(),
        databaseClass = PerContextDatabase::class,
    )

    @Before
    @After
    fun deleteTestDatabase() {
        instrumentation.targetContext.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun version1RecordSurvivesMigrationToVersion4() = runTest {
        helper.createDatabase(1).use { database ->
            database.execute(
                """
                INSERT INTO records (
                    id, createdAtMillis, durationMillis, audioPath, audioCodec,
                    recordStatus, transcriptStatus, contextStatus
                ) VALUES (
                    'record_v1', 10, 20, '/record_v1.m4a', 'AAC-LC/M4A',
                    'RECORDED', 'NOT_REQUESTED', 'NOT_REQUESTED'
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(4, emptyList()).use { database ->
            assertEquals("/record_v1.m4a", database.text("SELECT audioPath FROM records"))
            assertEquals(1L, database.long("SELECT COUNT(*) FROM records"))
            assertEquals(0L, database.long("SELECT COUNT(*) FROM transcripts"))
            assertEquals(0L, database.long("SELECT COUNT(*) FROM transcript_segments"))
            assertEquals(0L, database.long("SELECT COUNT(*) FROM daily_contexts"))
            assertEquals(0L, database.long("SELECT COUNT(*) FROM daily_context_sources"))
        }
    }

    @Test
    fun version2TranscriptGraphAndForeignKeysSurviveMigrationToVersion4() = runTest {
        helper.createDatabase(2).use { database ->
            database.execute(
                """
                INSERT INTO records (
                    id, createdAtMillis, durationMillis, audioPath, audioCodec,
                    recordStatus, transcriptStatus, contextStatus
                ) VALUES (
                    'record_v2', 10, 20, '/record_v2.m4a', 'AAC-LC/M4A',
                    'RECORDED', 'SUCCEEDED', 'NOT_REQUESTED'
                )
                """.trimIndent(),
            )
            database.execute(
                """
                INSERT INTO transcripts (
                    id, recordId, rawText, language, provider, model,
                    createdAtMillis, updatedAtMillis
                ) VALUES (
                    'transcript_v2', 'record_v2', '保留正文', 'zh',
                    'sherpa-onnx', 'sensevoice-int8-2024-07-17', 30, 40
                )
                """.trimIndent(),
            )
            database.execute(
                """
                INSERT INTO transcript_segments (
                    id, transcriptId, position, startMillis, endMillis, text
                ) VALUES ('segment_v2', 'transcript_v2', 0, 0, 100, '保留分段')
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(4, emptyList()).use { database ->
            assertEquals("record_v2", database.text("SELECT id FROM records"))
            assertEquals("保留正文", database.text("SELECT rawText FROM transcripts"))
            assertEquals("保留分段", database.text("SELECT text FROM transcript_segments"))
            assertForeignKey(
                database = database,
                table = "transcripts",
                parentTable = "records",
                fromColumn = "recordId",
                toColumn = "id",
            )
            assertForeignKey(
                database = database,
                table = "transcript_segments",
                parentTable = "transcripts",
                fromColumn = "transcriptId",
                toColumn = "id",
            )

            database.execute("PRAGMA foreign_keys = ON")
            database.execute("DELETE FROM records WHERE id = 'record_v2'")
            assertEquals(0L, database.long("SELECT COUNT(*) FROM transcripts"))
            assertEquals(0L, database.long("SELECT COUNT(*) FROM transcript_segments"))
        }
    }

    @Test
    fun version3AddsDailyContextWithSourceForeignKeys() = runTest {
        helper.createDatabase(3).use { database ->
            database.execute(
                """
                INSERT INTO records (
                    id, createdAtMillis, durationMillis, audioPath, audioCodec,
                    recordStatus, transcriptStatus, contextStatus
                ) VALUES (
                    'record_v3', 10, 20, '/record_v3.m4a', 'AAC-LC/M4A',
                    'RECORDED', 'SUCCEEDED', 'NOT_REQUESTED'
                )
                """.trimIndent(),
            )
            database.execute(
                """
                INSERT INTO transcripts (
                    id, recordId, rawText, language, provider, model,
                    createdAtMillis, updatedAtMillis
                ) VALUES (
                    'transcript_v3', 'record_v3', '当天原文', 'zh',
                    'sherpa-onnx', 'sensevoice-int8-2024-07-17', 30, 40
                )
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(4, emptyList()).use { database ->
            database.execute(
                """
                INSERT INTO daily_contexts (
                    dayKey, zoneId, title, summary, structuredJson, schemaVersion,
                    providerId, model, status, inputFingerprint, generatedAtMillis,
                    updatedAtMillis, failureCode
                ) VALUES (
                    '2026-08-27', 'Asia/Hong_Kong', '当天', '摘要', '{}',
                    'daily_context.v1', 'deepseek', 'deepseek-v4-flash', 'SUCCEEDED',
                    'hash', 50, 50, NULL
                )
                """.trimIndent(),
            )
            database.execute(
                """
                INSERT INTO daily_context_sources (
                    dayKey, recordId, transcriptId, position, recordedAtMillis
                ) VALUES (
                    '2026-08-27', 'record_v3', 'transcript_v3', 0, 10
                )
                """.trimIndent(),
            )
            assertEquals(1L, database.long("SELECT COUNT(*) FROM daily_context_sources"))
            assertForeignKey(database, "daily_context_sources", "daily_contexts", "dayKey", "dayKey")

            database.execute("PRAGMA foreign_keys = ON")
            database.execute("DELETE FROM records WHERE id = 'record_v3'")
            assertEquals(0L, database.long("SELECT COUNT(*) FROM daily_context_sources"))
            assertEquals(1L, database.long("SELECT COUNT(*) FROM daily_contexts"))
        }
    }

    private fun assertForeignKey(
        database: SQLiteConnection,
        table: String,
        parentTable: String,
        fromColumn: String,
        toColumn: String,
    ) {
        database.prepare("PRAGMA foreign_key_list('$table')").use { statement ->
            var found = false
            while (statement.step()) {
                if (statement.getText(2) == parentTable &&
                    statement.getText(3) == fromColumn &&
                    statement.getText(4) == toColumn &&
                    statement.getText(6) == "CASCADE"
                ) {
                    found = true
                }
            }
            assertTrue(
                "Missing CASCADE foreign key $table.$fromColumn -> $parentTable.$toColumn",
                found,
            )
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { statement -> statement.step() }
    }

    private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { statement ->
        check(statement.step()) { "Query returned no rows" }
        statement.getText(0)
    }

    private fun SQLiteConnection.long(sql: String): Long = prepare(sql).use { statement ->
        check(statement.step()) { "Query returned no rows" }
        statement.getLong(0)
    }

    private companion object {
        const val TEST_DATABASE_NAME = "percontext-migration-test.db"
    }
}
