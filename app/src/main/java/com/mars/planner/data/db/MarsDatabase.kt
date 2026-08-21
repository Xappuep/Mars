package com.mars.planner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, EnhancementEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MarsDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun enhancementDao(): EnhancementDao

    companion object {
        @Volatile private var instance: MarsDatabase? = null

        fun get(context: Context): MarsDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarsDatabase::class.java,
                    "mars_planner.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
