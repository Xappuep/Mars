package com.mars.planner.domain.model

data class ProjectItem(
    val id: Long = 0L,
    val syncUuid: String,
    val name: String,
    val description: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Исходная строка `created_at` для совместимого `version_hash`. */
    val createdAtRawUtc: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

data class TaskItem(
    val id: Long = 0L,
    val syncUuid: String,
    val title: String,
    val description: String = "",
    val projectSyncUuid: String? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    /** Срок как epoch millis UTC, либо null. */
    val dueAtEpochMillis: Long? = null,
    /** Исходная строка `due_at` для совместимого `version_hash`. */
    val dueAtRawUtc: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val createdAt: Long = System.currentTimeMillis(),
    /** Исходная строка `created_at` для совместимого `version_hash`. */
    val createdAtRawUtc: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

data class ProjectWithStats(
    val project: ProjectItem,
    val totalTasks: Int,
    val doneTasks: Int
) {
    val openTasks: Int
        get() = (totalTasks - doneTasks).coerceAtLeast(0)

    val progressPercent: Int
        get() = if (totalTasks == 0) 0 else ((doneTasks.toDouble() / totalTasks) * 100).toInt().coerceIn(0, 100)
}

data class DaySummary(
    val total: Int = 0,
    val done: Int = 0,
    val open: Int = 0,
    val overdue: Int = 0
)

data class StatsSnapshot(
    val completedWeek: Int = 0,
    val completedMonth: Int = 0,
    val openCount: Int = 0,
    val overdueCount: Int = 0,
    val completionPercent: Int = 0,
    val productiveStreak: Int = 0
)

data class SyncConflictItem(
    val id: Long = 0L,
    val entityType: SyncEntityType,
    val entityUuid: String,
    val localPayloadJson: String?,
    val remotePayloadJson: String?,
    val localOp: String,
    val remoteOp: String,
    val packageId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

data class MigrationReport(
    val projectsCreated: Int,
    val tasksMigrated: Int,
    val subtasksConverted: Int,
    val enhancementsConverted: Int,
    val cancelledToDone: Int,
    val dueAt2359Count: Int,
    val archivePath: String,
    val shown: Boolean = false
)
