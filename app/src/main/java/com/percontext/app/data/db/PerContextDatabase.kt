package com.percontext.app.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        RecordEntity::class,
        TranscriptEntity::class,
        TranscriptSegmentEntity::class,
        DailyContextEntity::class,
        DailyContextSourceEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class PerContextDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun transcriptDao(): TranscriptDao
    abstract fun dailyContextDao(): DailyContextDao
    abstract fun dailyContextSourceDao(): DailyContextSourceDao

    companion object {
        fun create(context: Context): PerContextDatabase =
            Room.databaseBuilder<PerContextDatabase>(
                context = context.applicationContext,
                name = "percontext.db",
            )
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
