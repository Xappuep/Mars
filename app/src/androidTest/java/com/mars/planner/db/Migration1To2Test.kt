package com.mars.planner.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.db.MarsMigrations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Миграция v1→v2→v3 без зависимости от Room schema assets
 * (`exportSchema = false` в [MarsDatabase]).
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val dbName = "migration-1-2-test"
    private lateinit var context: Context
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayOnly = LocalDate.of(2026, 9, 7).toEpochDay()
    private val withTime = LocalDate.of(2026, 9, 8).toEpochDay()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrateLegacyV1DataWithoutLoss() = runBlocking {
        seedLegacyV1Database()

        val db = Room.databaseBuilder(context, MarsDatabase::class.java, dbName)
            .addMigrations(MarsMigrations.MIGRATION_1_2, MarsMigrations.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val projects = db.projectDao().getAllOnce()
            val tasks = db.taskDao().getAllOnce()
            val archive = db.syncDao().latestArchive()
            val opCount = db.syncDao().countOperations()

            assertThat(projects).isNotEmpty()
            assertThat(projects.map { p -> p.syncUuid }.distinct()).hasSize(projects.size)

            // 5 legacy tasks + 1 enhancement → отдельная задача
            assertThat(tasks).hasSize(6)
            assertThat(tasks.map { t -> t.syncUuid }.distinct()).hasSize(tasks.size)

            val dayOnlyTask = tasks.first { it.title == "Корневая" }
            val expected2359 = LocalDate.ofEpochDay(dayOnly)
                .atTime(LocalTime.of(23, 59))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            assertThat(dayOnlyTask.dueAtEpochMillis).isEqualTo(expected2359)

            val timedTask = tasks.first { it.title == "Со временем" }
            val expectedTimed = LocalDate.ofEpochDay(withTime)
                .atTime(LocalTime.of(9, 0))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            assertThat(timedTask.dueAtEpochMillis).isEqualTo(expectedTimed)

            val enhancement = tasks.first { it.title == "Дополнение" }
            assertThat(enhancement.description).contains("Ранее дополнение")

            val subtask = tasks.first { it.title == "Подзадача" }
            assertThat(subtask.description).contains("Ранее подзадача")
            assertThat(subtask.description).contains("Корневая")

            val cancelled = tasks.first { it.title == "Отменённая" }
            assertThat(cancelled.status).isEqualTo("done")

            assertThat(tasks.count { task -> task.status == "done" }).isAtLeast(2)
            assertThat(tasks.any { task -> task.title == "Демо" && task.isDemo }).isTrue()

            assertThat(opCount).isEqualTo(0)
            assertThat(tasks.none { task -> task.isDemo && opCount > 0 }).isTrue()

            assertThat(tasks.all { task -> task.createdAtRawUtc.isNotBlank() }).isTrue()
            assertThat(dayOnlyTask.dueAtRawUtc).isNotNull()
            assertThat(dayOnlyTask.dueAtRawUtc!!.isNotBlank()).isTrue()
            assertThat(timedTask.dueAtRawUtc).isNotNull()
            assertThat(timedTask.dueAtRawUtc!!.isNotBlank()).isTrue()
            assertThat(projects.all { p -> p.createdAtRawUtc.isNotBlank() }).isTrue()

            assertThat(archive).isNotNull()
            assertThat(archive!!.archiveJson).contains("\"enhancements\"")
            assertThat(archive.archiveJson).contains("\"cancelled\"")
            assertThat(archive.archiveJson).contains("Корневая")
            assertThat(archive.archiveJson).contains("Дополнение")
            assertThat(archive.archiveJson).contains("Подзадача")
            assertThat(archive.archiveJson).contains("Демо")
        } finally {
            db.close()
        }
    }

    private fun seedLegacyV1Database() {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV1Schema(db)
                        insertV1Rows(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        openHelper.writableDatabase.close()
    }

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                dueDateEpochDay INTEGER,
                dueTimeMinutes INTEGER,
                reminderAtEpochMillis INTEGER,
                priority TEXT NOT NULL,
                category TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                postponeCount INTEGER NOT NULL,
                postponeReason TEXT,
                parentTaskId INTEGER,
                nestingLevel INTEGER NOT NULL,
                relatedToTaskId INTEGER,
                isDemo INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS enhancements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceTaskId INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                status TEXT NOT NULL,
                priority TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                plannedDateEpochDay INTEGER,
                deferredReason TEXT,
                convertedTaskId INTEGER
            )
            """.trimIndent()
        )
    }

    private fun insertV1Rows(db: SupportSQLiteDatabase) {
        val created = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val updated = LocalDate.of(2026, 9, 2).atStartOfDay(zone).toInstant().toEpochMilli()

        db.execSQL(
            """
            INSERT INTO tasks
            (id,title,description,dueDateEpochDay,dueTimeMinutes,reminderAtEpochMillis,priority,category,status,createdAt,updatedAt,postponeCount,postponeReason,parentTaskId,nestingLevel,relatedToTaskId,isDemo)
            VALUES
            (1,'Корневая','Описание',?,?,NULL,'normal','Дом','new',?,?,0,NULL,NULL,0,NULL,0),
            (2,'Со временем','Описание',?,?,NULL,'high','Работа','in_progress',?,?,0,NULL,NULL,0,NULL,0),
            (3,'Подзадача','Вложенная',NULL,NULL,NULL,'low','Дом','done',?,?,0,NULL,1,1,NULL,0),
            (4,'Отменённая','Отмена',NULL,NULL,NULL,'normal','Дом','cancelled',?,?,0,NULL,NULL,0,NULL,0),
            (5,'Демо','Демо',NULL,NULL,NULL,'normal','Демо','new',?,?,0,NULL,NULL,0,NULL,1)
            """.trimIndent(),
            arrayOf(
                dayOnly, null, created, updated,
                withTime, 540, created, updated,
                created, updated,
                created, updated,
                created, updated
            )
        )
        db.execSQL(
            """
            INSERT INTO enhancements
            (id,sourceTaskId,title,description,status,priority,createdAt,plannedDateEpochDay,deferredReason,convertedTaskId)
            VALUES (10,1,'Дополнение','Описание идеи','idea','normal',?,?,NULL,NULL)
            """.trimIndent(),
            arrayOf(created, dayOnly)
        )
    }
}
