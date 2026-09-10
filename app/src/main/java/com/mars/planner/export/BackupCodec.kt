package com.mars.planner.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

const val BACKUP_FORMAT_VERSION = 2

data class BackupPayload(
    val version: Int = BACKUP_FORMAT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val projects: List<ProjectDto> = emptyList(),
    val tasks: List<TaskDto> = emptyList(),
    val settings: SettingsDto? = null,
    /** Только для чтения архивов Mars v1 — дополнения переносятся в задачи. */
    val enhancements: List<LegacyEnhancementDto> = emptyList()
)

data class ProjectDto(
    val syncUuid: String = "",
    val name: String = "",
    val description: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

data class TaskDto(
    val syncUuid: String = "",
    val title: String = "",
    val description: String = "",
    val projectSyncUuid: String? = null,
    val priority: String = TaskPriority.NORMAL.key,
    val dueAtEpochMillis: Long? = null,
    val status: String = TaskStatus.OPEN.key,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    // ——— Поля формата Mars v1, используются только при импорте старых копий ———
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val category: String? = null,
    val parentTaskId: Long? = null,
    val id: Long = 0
)

data class LegacyEnhancementDto(
    val id: Long = 0,
    val sourceTaskId: Long = 0,
    val title: String = "",
    val description: String = "",
    val status: String = "idea",
    val priority: String = TaskPriority.NORMAL.key,
    val createdAt: Long = 0,
    val plannedDateEpochDay: Long? = null
)

data class SettingsDto(
    val motivatorMode: String = MotivatorMode.ADAPTIVE.key,
    val morningReminderEnabled: Boolean = true,
    val morningReminderHour: Int = 9,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val defaultSnoozeMinutes: Int = 30,
    val userName: String = "",
    val theme: String = AppTheme.ORBIT.key,
    val effectIntensity: String = EffectIntensity.NORMAL.key,
    val reduceAnimations: Boolean = false
)

/** Результат разбора резервной копии, готовый к записи в базу. */
data class ImportModels(
    val projects: List<ProjectItem>,
    val tasks: List<TaskItem>,
    val migratedFromV1: Boolean
)

object BackupCodec {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(
        tasks: List<TaskItem>,
        projects: List<ProjectItem>,
        settings: AppSettings?
    ): String {
        val payload = BackupPayload(
            projects = projects.map { it.toDto() },
            tasks = tasks.map { it.toDto() },
            settings = settings?.toDto()
        )
        return gson.toJson(payload)
    }

    fun fromJson(json: String): BackupPayload =
        gson.fromJson(json, BackupPayload::class.java) ?: BackupPayload()

    fun toCsv(projects: List<ProjectItem>, tasks: List<TaskItem>): String {
        val projectNames = projects.associate { it.syncUuid to it.name }
        val header = listOf(
            "sync_uuid", "title", "description", "project", "priority",
            "due_at", "status", "created_at", "updated_at"
        ).joinToString(",")
        val rows = tasks.map { t ->
            listOf(
                t.syncUuid,
                escape(t.title),
                escape(t.description),
                escape(t.projectSyncUuid?.let { projectNames[it] } ?: ""),
                t.priority.key,
                t.dueAtEpochMillis?.toString() ?: "",
                t.status.key,
                t.createdAt.toString(),
                t.updatedAt.toString()
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun parseTaskCount(json: String): Int = fromJson(json).tasks.size

    /**
     * Приводит любую поддерживаемую копию к текущей модели.
     * Копии Mars v1 (категории, подзадачи, дополнения) преобразуются:
     * категория → проект, подзадача и дополнение → отдельная задача с пометкой в описании.
     */
    fun toModels(
        payload: BackupPayload,
        zone: ZoneId = ZoneId.systemDefault()
    ): ImportModels {
        val legacy = payload.version < BACKUP_FORMAT_VERSION ||
            payload.tasks.any { it.syncUuid.isBlank() } ||
            payload.enhancements.isNotEmpty()

        val projects = payload.projects.map { it.toDomain() }.toMutableList()
        val projectByName = projects.associateBy { it.name }.toMutableMap()
        val now = System.currentTimeMillis()

        fun projectUuidForCategory(category: String?): String? {
            val name = category?.trim().orEmpty()
            if (name.isEmpty()) return null
            projectByName[name]?.let { return it.syncUuid }
            val created = ProjectItem(
                syncUuid = UUID.randomUUID().toString(),
                name = name,
                createdAt = now,
                updatedAt = now
            )
            projects += created
            projectByName[name] = created
            return created.syncUuid
        }

        val titleById = payload.tasks.associate { it.id to it.title }
        val tasks = mutableListOf<TaskItem>()

        payload.tasks.forEach { dto ->
            val dueAt = dto.dueAtEpochMillis ?: dueAtFromLegacy(dto.dueDateEpochDay, dto.dueTimeMinutes, zone)
            var description = dto.description
            if (dto.parentTaskId != null) {
                val parentTitle = titleById[dto.parentTaskId] ?: "№${dto.parentTaskId}"
                val note = "Ранее подзадача задачи «$parentTitle»."
                description = if (description.isBlank()) note else "$description\n\n$note"
            }
            tasks += TaskItem(
                syncUuid = dto.syncUuid.ifBlank { UUID.randomUUID().toString() },
                title = dto.title,
                description = description,
                projectSyncUuid = dto.projectSyncUuid ?: projectUuidForCategory(dto.category),
                priority = TaskPriority.fromKey(dto.priority),
                dueAtEpochMillis = dueAt,
                status = TaskStatus.fromLegacyKey(dto.status),
                createdAt = if (dto.createdAt == 0L) now else dto.createdAt,
                updatedAt = if (dto.updatedAt == 0L) now else dto.updatedAt
            )
        }

        payload.enhancements.forEach { dto ->
            val sourceTitle = titleById[dto.sourceTaskId] ?: "№${dto.sourceTaskId}"
            val note = "Ранее дополнение к задаче «$sourceTitle»."
            val description = if (dto.description.isBlank()) note else "${dto.description}\n\n$note"
            tasks += TaskItem(
                syncUuid = UUID.randomUUID().toString(),
                title = dto.title,
                description = description,
                projectSyncUuid = null,
                priority = TaskPriority.fromKey(dto.priority),
                dueAtEpochMillis = dueAtFromLegacy(dto.plannedDateEpochDay, null, zone),
                status = if (dto.status in listOf("realized", "cancelled")) TaskStatus.DONE else TaskStatus.OPEN,
                createdAt = if (dto.createdAt == 0L) now else dto.createdAt,
                updatedAt = now
            )
        }

        return ImportModels(projects = projects, tasks = tasks, migratedFromV1 = legacy)
    }

    private fun dueAtFromLegacy(day: Long?, timeMinutes: Int?, zone: ZoneId): Long? {
        if (day == null) return null
        val date = LocalDate.ofEpochDay(day)
        val time = if (timeMinutes != null) {
            LocalTime.of(timeMinutes / 60, timeMinutes % 60)
        } else {
            LocalTime.of(23, 59)
        }
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun escape(value: String): String {
        val flat = value.replace("\n", " ")
        val needsQuotes = flat.contains(',') || flat.contains('"')
        val escaped = flat.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}

fun ProjectItem.toDto() = ProjectDto(
    syncUuid = syncUuid,
    name = name,
    description = description,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ProjectDto.toDomain() = ProjectItem(
    syncUuid = syncUuid.ifBlank { UUID.randomUUID().toString() },
    name = name,
    description = description,
    archived = archived,
    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
    updatedAt = if (updatedAt == 0L) System.currentTimeMillis() else updatedAt
)

fun TaskItem.toDto() = TaskDto(
    syncUuid = syncUuid,
    title = title,
    description = description,
    projectSyncUuid = projectSyncUuid,
    priority = priority.key,
    dueAtEpochMillis = dueAtEpochMillis,
    status = status.key,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AppSettings.toDto() = SettingsDto(
    motivatorMode = motivatorMode.key,
    morningReminderEnabled = morningReminderEnabled,
    morningReminderHour = morningReminderHour,
    morningReminderMinute = morningReminderMinute,
    eveningReminderEnabled = eveningReminderEnabled,
    eveningReminderHour = eveningReminderHour,
    eveningReminderMinute = eveningReminderMinute,
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    userName = userName,
    theme = AppTheme.fromKey(themeId).key,
    effectIntensity = EffectIntensity.fromFactor(effectIntensity).key,
    reduceAnimations = reduceAnimations
)

fun SettingsDto.toAppSettings(base: AppSettings = AppSettings()): AppSettings = base.copy(
    motivatorMode = MotivatorMode.fromKey(motivatorMode),
    morningReminderEnabled = morningReminderEnabled,
    morningReminderHour = morningReminderHour,
    morningReminderMinute = morningReminderMinute,
    eveningReminderEnabled = eveningReminderEnabled,
    eveningReminderHour = eveningReminderHour,
    eveningReminderMinute = eveningReminderMinute,
    defaultSnoozeMinutes = defaultSnoozeMinutes,
    userName = userName,
    themeId = AppTheme.fromKey(theme).key,
    effectIntensity = EffectIntensity.fromKey(effectIntensity).factor,
    reduceAnimations = reduceAnimations
)
