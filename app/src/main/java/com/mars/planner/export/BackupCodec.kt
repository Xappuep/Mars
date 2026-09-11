package com.mars.planner.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.EffectIntensity
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus

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
    // ——— Поля формата Mars v1 (совместимость чтения) ———
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

/**
 * Подмножество настроек для JSON-копии.
 * Поле `motivatorMode` намеренно отсутствует: Gson при чтении старых копий
 * безопасно игнорирует неизвестные поля JSON.
 */
data class SettingsDto(
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

/**
 * Внутренний JSON-кодек резервных копий телефона.
 * Используется [com.mars.planner.sync.PhoneSnapshotBackup] (create/restore)
 * перед применением полного снимка ПК. Ручной экспорт/импорт UI удалён.
 */
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
}

fun ProjectItem.toDto() = ProjectDto(
    syncUuid = syncUuid,
    name = name,
    description = description,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
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
