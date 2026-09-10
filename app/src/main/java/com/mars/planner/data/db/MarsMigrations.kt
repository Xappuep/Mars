package com.mars.planner.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mars.planner.sync.VersionHash
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Миграция Room 1 → 2: модель «Рубежа», архив старых данных, UUID.
 * Атомарна (внутри транзакции Room), повторный запуск невозможен — версия уже 2.
 */
object MarsMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Архив старых таблиц
            db.execSQL("ALTER TABLE tasks RENAME TO tasks_v1_legacy")
            db.execSQL("ALTER TABLE enhancements RENAME TO enhancements_v1_legacy")

            createV2Tables(db)

            val zone = ZoneId.systemDefault()
            val archive = JSONObject()
            val tasksArr = JSONArray()
            val enhArr = JSONArray()

            val oldTasks = mutableListOf<LegacyTask>()
            db.query("SELECT * FROM tasks_v1_legacy").use { c ->
                while (c.moveToNext()) {
                    val t = LegacyTask(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        description = c.getString(c.getColumnIndexOrThrow("description")) ?: "",
                        dueDateEpochDay = c.nullableLong("dueDateEpochDay"),
                        dueTimeMinutes = c.nullableInt("dueTimeMinutes"),
                        reminderAtEpochMillis = c.nullableLong("reminderAtEpochMillis"),
                        priority = c.getString(c.getColumnIndexOrThrow("priority")) ?: "normal",
                        category = c.getString(c.getColumnIndexOrThrow("category")) ?: "",
                        status = c.getString(c.getColumnIndexOrThrow("status")) ?: "new",
                        createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt")),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updatedAt")),
                        postponeCount = c.getInt(c.getColumnIndexOrThrow("postponeCount")),
                        postponeReason = c.nullableString("postponeReason"),
                        parentTaskId = c.nullableLong("parentTaskId"),
                        nestingLevel = c.getInt(c.getColumnIndexOrThrow("nestingLevel")),
                        relatedToTaskId = c.nullableLong("relatedToTaskId"),
                        isDemo = c.getInt(c.getColumnIndexOrThrow("isDemo")) != 0
                    )
                    oldTasks += t
                    tasksArr.put(t.toJson())
                }
            }
            db.query("SELECT * FROM enhancements_v1_legacy").use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    o.put("id", c.getLong(c.getColumnIndexOrThrow("id")))
                    o.put("sourceTaskId", c.getLong(c.getColumnIndexOrThrow("sourceTaskId")))
                    o.put("title", c.getString(c.getColumnIndexOrThrow("title")))
                    o.put("description", c.getString(c.getColumnIndexOrThrow("description")) ?: "")
                    o.put("status", c.getString(c.getColumnIndexOrThrow("status")))
                    o.put("priority", c.getString(c.getColumnIndexOrThrow("priority")))
                    o.put("createdAt", c.getLong(c.getColumnIndexOrThrow("createdAt")))
                    o.put("plannedDateEpochDay", c.nullableLong("plannedDateEpochDay"))
                    o.put("deferredReason", c.nullableString("deferredReason"))
                    o.put("convertedTaskId", c.nullableLong("convertedTaskId"))
                    enhArr.put(o)
                }
            }
            archive.put("tasks", tasksArr)
            archive.put("enhancements", enhArr)
            archive.put("schemaVersion", 1)

            // 2. Категории → проекты
            val categoryToUuid = linkedMapOf<String, String>()
            var projectsCreated = 0
            val now = System.currentTimeMillis()
            oldTasks.asSequence()
                .filter { !it.isDemo }
                .map { it.category.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .forEach { cat ->
                    val uuid = UUID.randomUUID().toString()
                    categoryToUuid[cat] = uuid
                    db.execSQL(
                        """
                        INSERT INTO projects (syncUuid, name, description, archived, createdAt, updatedAt, isDemo)
                        VALUES (?, ?, '', 0, ?, ?, 0)
                        """.trimIndent(),
                        arrayOf(uuid, cat, now, now)
                    )
                    projectsCreated++
                }

            val idToUuid = mutableMapOf<Long, String>()
            val idToTitle = oldTasks.associate { it.id to it.title }
            var tasksMigrated = 0
            var subtasksConverted = 0
            var cancelledToDone = 0
            var dueAt2359 = 0

            // 3. Задачи (корневые и подзадачи)
            for (t in oldTasks) {
                val uuid = UUID.randomUUID().toString()
                idToUuid[t.id] = uuid
                val projectUuid = t.category.trim().takeIf { it.isNotEmpty() }?.let { categoryToUuid[it] }
                val newStatus = when (t.status.lowercase()) {
                    "done", "cancelled" -> {
                        if (t.status.equals("cancelled", true)) cancelledToDone++
                        "done"
                    }
                    else -> "open"
                }
                var desc = t.description
                if (t.parentTaskId != null) {
                    subtasksConverted++
                    val parentTitle = idToTitle[t.parentTaskId] ?: "№${t.parentTaskId}"
                    val note = "Ранее подзадача задачи «$parentTitle»."
                    desc = if (desc.isBlank()) note else "$desc\n\n$note"
                }
                val (dueAt, used2359) = dueAtMillis(t.dueDateEpochDay, t.dueTimeMinutes, zone)
                if (used2359) dueAt2359++

                db.execSQL(
                    """
                    INSERT INTO tasks
                    (syncUuid, title, description, projectSyncUuid, priority, dueAtEpochMillis, status, createdAt, updatedAt, isDemo)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf(
                        uuid, t.title, desc, projectUuid, t.priority,
                        dueAt, newStatus, t.createdAt, t.updatedAt, if (t.isDemo) 1 else 0
                    )
                )
                tasksMigrated++
            }

            // 4. Дополнения → задачи
            var enhancementsConverted = 0
            db.query("SELECT * FROM enhancements_v1_legacy").use { c ->
                while (c.moveToNext()) {
                    val sourceId = c.getLong(c.getColumnIndexOrThrow("sourceTaskId"))
                    val source = oldTasks.find { it.id == sourceId }
                    if (source?.isDemo == true) continue
                    val title = c.getString(c.getColumnIndexOrThrow("title"))
                    var desc = c.getString(c.getColumnIndexOrThrow("description")) ?: ""
                    val note = "Ранее дополнение к задаче «${source?.title ?: "№$sourceId"}»."
                    desc = if (desc.isBlank()) note else "$desc\n\n$note"
                    val priority = c.getString(c.getColumnIndexOrThrow("priority")) ?: "normal"
                    val planned = c.nullableLong("plannedDateEpochDay")
                    val (dueAt, used2359) = dueAtMillis(planned, null, zone)
                    if (used2359) dueAt2359++
                    val createdAt = c.getLong(c.getColumnIndexOrThrow("createdAt"))
                    val projectUuid = source?.category?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { categoryToUuid[it] }
                    val enhStatus = c.getString(c.getColumnIndexOrThrow("status")) ?: "idea"
                    val status = if (enhStatus in listOf("realized", "cancelled")) "done" else "open"
                    db.execSQL(
                        """
                        INSERT INTO tasks
                        (syncUuid, title, description, projectSyncUuid, priority, dueAtEpochMillis, status, createdAt, updatedAt, isDemo)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """.trimIndent(),
                        arrayOf(
                            UUID.randomUUID().toString(), title, desc, projectUuid, priority,
                            dueAt, status, createdAt, createdAt
                        )
                    )
                    enhancementsConverted++
                }
            }

            val report = JSONObject()
            report.put("projectsCreated", projectsCreated)
            report.put("tasksMigrated", tasksMigrated)
            report.put("subtasksConverted", subtasksConverted)
            report.put("enhancementsConverted", enhancementsConverted)
            report.put("cancelledToDone", cancelledToDone)
            report.put("dueAt2359Count", dueAt2359)
            report.put("archiveInternal", true)

            db.execSQL(
                """
                INSERT INTO migration_archive (archiveJson, createdAt, reportJson, reportShown)
                VALUES (?, ?, ?, 0)
                """.trimIndent(),
                arrayOf(archive.toString(), now, report.toString())
            )

            db.execSQL("DROP TABLE tasks_v1_legacy")
            db.execSQL("DROP TABLE enhancements_v1_legacy")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE projects ADD COLUMN createdAtRawUtc TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tasks ADD COLUMN dueAtRawUtc TEXT")
            db.execSQL("ALTER TABLE tasks ADD COLUMN createdAtRawUtc TEXT NOT NULL DEFAULT ''")

            db.query("SELECT id, createdAt FROM projects").use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val createdAt = c.getLong(1)
                    db.execSQL(
                        "UPDATE projects SET createdAtRawUtc = ? WHERE id = ?",
                        arrayOf(VersionHash.formatUtc(createdAt), id)
                    )
                }
            }
            db.query("SELECT id, createdAt, dueAtEpochMillis FROM tasks").use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val createdAt = c.getLong(1)
                    val dueAt = if (c.isNull(2)) null else c.getLong(2)
                    db.execSQL(
                        "UPDATE tasks SET createdAtRawUtc = ?, dueAtRawUtc = ? WHERE id = ?",
                        arrayOf(
                            VersionHash.formatUtc(createdAt),
                            dueAt?.let { VersionHash.formatUtc(it) },
                            id
                        )
                    )
                }
            }
        }
    }

    private fun createV2Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                syncUuid TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                archived INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projects_syncUuid ON projects(syncUuid)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                syncUuid TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                projectSyncUuid TEXT,
                priority TEXT NOT NULL,
                dueAtEpochMillis INTEGER,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isDemo INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_syncUuid ON tasks(syncUuid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_projectSyncUuid ON tasks(projectSyncUuid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_dueAtEpochMillis ON tasks(dueAtEpochMillis)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_operations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                operationId TEXT NOT NULL,
                entityType TEXT NOT NULL,
                entityUuid TEXT NOT NULL,
                op TEXT NOT NULL,
                baseHash TEXT,
                newHash TEXT,
                payloadJson TEXT,
                deviceId TEXT NOT NULL,
                protocolVersion INTEGER NOT NULL,
                causalSeq INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                acked INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_operations_operationId ON sync_operations(operationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_causalSeq ON sync_operations(causalSeq)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_acked ON sync_operations(acked)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_entityUuid ON sync_operations(entityUuid)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_tombstones (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityType TEXT NOT NULL,
                entityUuid TEXT NOT NULL,
                deletedAt INTEGER NOT NULL,
                baseHash TEXT,
                acked INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_tombstones_entityUuid ON sync_tombstones(entityUuid)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_conflicts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                entityType TEXT NOT NULL,
                entityUuid TEXT NOT NULL,
                localPayloadJson TEXT,
                remotePayloadJson TEXT,
                localOp TEXT NOT NULL,
                remoteOp TEXT NOT NULL,
                packageId TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                resolved INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_applied_packages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packageId TEXT NOT NULL,
                senderDeviceId TEXT NOT NULL,
                appliedAt INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_applied_packages_packageId ON sync_applied_packages(packageId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS migration_archive (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                archiveJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                reportJson TEXT NOT NULL,
                reportShown INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun dueAtMillis(
        day: Long?,
        timeMinutes: Int?,
        zone: ZoneId
    ): Pair<Long?, Boolean> {
        if (day == null) return null to false
        val date = LocalDate.ofEpochDay(day)
        return if (timeMinutes != null) {
            val t = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
            date.atTime(t).atZone(zone).toInstant().toEpochMilli() to false
        } else {
            date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli() to true
        }
    }

    private data class LegacyTask(
        val id: Long,
        val title: String,
        val description: String,
        val dueDateEpochDay: Long?,
        val dueTimeMinutes: Int?,
        val reminderAtEpochMillis: Long?,
        val priority: String,
        val category: String,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long,
        val postponeCount: Int,
        val postponeReason: String?,
        val parentTaskId: Long?,
        val nestingLevel: Int,
        val relatedToTaskId: Long?,
        val isDemo: Boolean
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("title", title)
            put("description", description)
            put("dueDateEpochDay", dueDateEpochDay)
            put("dueTimeMinutes", dueTimeMinutes)
            put("reminderAtEpochMillis", reminderAtEpochMillis)
            put("priority", priority)
            put("category", category)
            put("status", status)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("postponeCount", postponeCount)
            put("postponeReason", postponeReason)
            put("parentTaskId", parentTaskId)
            put("nestingLevel", nestingLevel)
            put("relatedToTaskId", relatedToTaskId)
            put("isDemo", isDemo)
        }
    }
}

private fun android.database.Cursor.nullableLong(name: String): Long? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getLong(i)
}

private fun android.database.Cursor.nullableInt(name: String): Int? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getInt(i)
}

private fun android.database.Cursor.nullableString(name: String): String? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getString(i)
}
