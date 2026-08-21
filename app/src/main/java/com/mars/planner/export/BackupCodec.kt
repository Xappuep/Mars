package com.mars.planner.export

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskDto> = emptyList(),
    val enhancements: List<EnhancementDto> = emptyList(),
    val settings: SettingsDto? = null
)

data class TaskDto(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtEpochMillis: Long? = null,
    val priority: String = "normal",
    val category: String = "",
    val status: String = "new",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val postponeCount: Int = 0,
    val postponeReason: String? = null,
    val parentTaskId: Long? = null,
    val nestingLevel: Int = 0,
    val relatedToTaskId: Long? = null,
    val isDemo: Boolean = false
)

data class EnhancementDto(
    val id: Long = 0,
    val sourceTaskId: Long,
    val title: String,
    val description: String = "",
    val status: String = "idea",
    val priority: String = "normal",
    val createdAt: Long = 0,
    val plannedDateEpochDay: Long? = null,
    val deferredReason: String? = null,
    val convertedTaskId: Long? = null
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
    val userName: String = ""
)

object BackupCodec {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun toJson(
        tasks: List<TaskItem>,
        enhancements: List<EnhancementIdea>,
        settings: AppSettings?
    ): String {
        val payload = BackupPayload(
            tasks = tasks.map { it.toDto() },
            enhancements = enhancements.map { it.toDto() },
            settings = settings?.toDto()
        )
        return gson.toJson(payload)
    }

    fun fromJson(json: String): BackupPayload = gson.fromJson(json, BackupPayload::class.java)

    fun toCsv(tasks: List<TaskItem>): String {
        val header = listOf(
            "id", "title", "description", "due_date", "due_time", "priority",
            "category", "status", "created_at", "updated_at", "postpone_count",
            "postpone_reason", "parent_task_id", "nesting_level", "related_to_task_id"
        ).joinToString(",")
        val rows = tasks.map { t ->
            listOf(
                t.id,
                escape(t.title),
                escape(t.description),
                t.dueDateEpochDay ?: "",
                t.dueTimeMinutes ?: "",
                t.priority.key,
                escape(t.category),
                t.status.key,
                t.createdAt,
                t.updatedAt,
                t.postponeCount,
                escape(t.postponeReason ?: ""),
                t.parentTaskId ?: "",
                t.nestingLevel,
                t.relatedToTaskId ?: ""
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    fun parseTaskCount(json: String): Int = fromJson(json).tasks.size

    private fun escape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}

fun TaskItem.toDto() = TaskDto(
    id = id,
    title = title,
    description = description,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeMinutes = dueTimeMinutes,
    reminderAtEpochMillis = reminderAtEpochMillis,
    priority = priority.key,
    category = category,
    status = status.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postponeCount = postponeCount,
    postponeReason = postponeReason,
    parentTaskId = parentTaskId,
    nestingLevel = nestingLevel,
    relatedToTaskId = relatedToTaskId,
    isDemo = isDemo
)

fun TaskDto.toDomain() = TaskItem(
    id = id,
    title = title,
    description = description,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeMinutes = dueTimeMinutes,
    reminderAtEpochMillis = reminderAtEpochMillis,
    priority = TaskPriority.fromKey(priority),
    category = category,
    status = TaskStatus.fromKey(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    postponeCount = postponeCount,
    postponeReason = postponeReason,
    parentTaskId = parentTaskId,
    nestingLevel = nestingLevel,
    relatedToTaskId = relatedToTaskId,
    isDemo = isDemo
)

fun EnhancementIdea.toDto() = EnhancementDto(
    id = id,
    sourceTaskId = sourceTaskId,
    title = title,
    description = description,
    status = status.key,
    priority = priority.key,
    createdAt = createdAt,
    plannedDateEpochDay = plannedDateEpochDay,
    deferredReason = deferredReason,
    convertedTaskId = convertedTaskId
)

fun EnhancementDto.toDomain() = EnhancementIdea(
    id = id,
    sourceTaskId = sourceTaskId,
    title = title,
    description = description,
    status = EnhancementStatus.fromKey(status),
    priority = TaskPriority.fromKey(priority),
    createdAt = createdAt,
    plannedDateEpochDay = plannedDateEpochDay,
    deferredReason = deferredReason,
    convertedTaskId = convertedTaskId
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
    userName = userName
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
    userName = userName
)
