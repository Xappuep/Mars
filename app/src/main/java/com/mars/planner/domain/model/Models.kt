package com.mars.planner.domain.model

data class TaskItem(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtEpochMillis: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val category: String = "",
    val status: TaskStatus = TaskStatus.NEW,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val postponeCount: Int = 0,
    val postponeReason: String? = null,
    val parentTaskId: Long? = null,
    /** Глубина вложенности подзадач: 0 — корневая, 1 — подзадача, 2 — вложенная подзадача (макс.) */
    val nestingLevel: Int = 0,
    val relatedToTaskId: Long? = null,
    val isDemo: Boolean = false
)

data class EnhancementIdea(
    val id: Long = 0L,
    val sourceTaskId: Long,
    val title: String,
    val description: String = "",
    val status: EnhancementStatus = EnhancementStatus.IDEA,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val createdAt: Long = System.currentTimeMillis(),
    val plannedDateEpochDay: Long? = null,
    val deferredReason: String? = null,
    val convertedTaskId: Long? = null
)

data class TaskWithDetails(
    val task: TaskItem,
    val subtasks: List<TaskItem> = emptyList(),
    val enhancements: List<EnhancementIdea> = emptyList()
) {
    val completedSubtasks: Int get() = subtasks.count { it.status == TaskStatus.DONE }
    val totalSubtasks: Int get() = subtasks.size
    val hasIncompleteSubtasks: Boolean get() =
        subtasks.any { it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }

    fun subtaskProgressLabel(): String {
        return "Подзадачи: $completedSubtasks из $totalSubtasks выполнено"
    }
}

data class DaySummary(
    val total: Int = 0,
    val done: Int = 0,
    val inProgress: Int = 0,
    val overdue: Int = 0,
    val postponed: Int = 0,
    val newCount: Int = 0
)

data class StatsSnapshot(
    val completedWeek: Int,
    val completedMonth: Int,
    val postponeCount: Int,
    val overdueCount: Int,
    val completionPercent: Int,
    val productiveStreak: Int
)
