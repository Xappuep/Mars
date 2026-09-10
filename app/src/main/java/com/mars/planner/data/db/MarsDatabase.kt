package com.mars.planner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        SyncOperationEntity::class,
        SyncTombstoneEntity::class,
        SyncConflictEntity::class,
        SyncAppliedPackageEntity::class,
        MigrationArchiveEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MarsDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun syncDao(): SyncDao

    companion object {
        @Volatile private var instance: MarsDatabase? = null

        fun get(context: Context): MarsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarsDatabase::class.java,
                    "mars_planner.db"
                )
                    .addMigrations(MarsMigrations.MIGRATION_1_2, MarsMigrations.MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }

        /** Только для тестов. */
        fun buildInMemory(context: Context): MarsDatabase =
            Room.inMemoryDatabaseBuilder(context, MarsDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(MarsMigrations.MIGRATION_1_2, MarsMigrations.MIGRATION_2_3)
                .build()
    }
}
