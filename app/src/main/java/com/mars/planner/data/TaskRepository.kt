package com.mars.planner.data

import com.mars.planner.data.db.EnhancementDao
import com.mars.planner.data.db.TaskDao
import com.mars.planner.data.db.toDomain
import com.mars.planner.data.db.toEntity
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.StatsSnapshot
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.domain.model.TaskWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class TaskRepository(
    private val taskDao: TaskDao,
    private val enhancementDao: EnhancementDao
) {
    fun observeRootTasks(): Flow<List<TaskItem>> =
        taskDao.observeRootTasks().map { list -> list.map { it.toDomain() } }

    fun observeDay(epochDay: Long): Flow<List<TaskItem>> =
        taskDao.observeForDay(epochDay).map { list -> list.map { it.toDomain() } }

    /** Только задачи верхнего уровня (без подзадач). */
    fun observeAll(): Flow<List<TaskItem>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Все записи задач, включая подзадачи — для прогресса и связей. */
    fun observeEveryTask(): Flow<List<TaskItem>> =
        taskDao.observeEveryTask().map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<TaskItem>> =
        taskDao.search(query).map { list -> list.map { it.toDomain() } }

    fun observeTaskDetails(taskId: Long): Flow<TaskWithDetails?> =
        combine(
            taskDao.observeById(taskId),
            taskDao.observeSubtasks(taskId),
            enhancementDao.observeForTask(taskId)
        ) { task, subtasks, enhancements ->
            task?.toDomain()?.let {
                TaskWithDetails(
                    task = it,
                    subtasks = subtasks.map { s -> s.toDomain() },
                    enhancements = enhancements.map { e -> e.toDomain() }
                )
            }
        }

    fun observeIdeas(): Flow<List<EnhancementIdea>> =
        enhancementDao.observeActiveIdeas().map { list -> list.map { it.toDomain() } }

    suspend fun getTask(id: Long): TaskItem? = taskDao.getById(id)?.toDomain()

    suspend fun saveTask(task: TaskItem): Long {
        val now = System.currentTimeMillis()
        // Подзадача всегда с родителем и nestingLevel >= 1; корневая — без родителя.
        val normalized = when {
            task.parentTaskId != null -> task.copy(
                nestingLevel = task.nestingLevel.coerceAtLeast(1)
            )
            else -> task.copy(parentTaskId = null, nestingLevel = 0)
        }
        val entity = normalized.copy(
            updatedAt = now,
            createdAt = if (normalized.id == 0L) now else normalized.createdAt
        ).toEntity()
        return if (normalized.id == 0L) {
            taskDao.insert(entity)
        } else {
            taskDao.update(entity)
            normalized.id
        }
    }

    suspend fun deleteTask(id: Long) {
        taskDao.deleteById(id)
    }

    suspend fun updateStatus(id: Long, status: TaskStatus): TaskItem? {
        val current = taskDao.getById(id) ?: return null
        val updated = current.copy(
            status = status.key,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.update(updated)
        return updated.toDomain()
    }

    suspend fun postponeTask(
        id: Long,
        newDueDay: Long,
        newDueTime: Int?,
        reason: String?
    ): TaskItem? {
        val current = taskDao.getById(id) ?: return null
        val updated = current.copy(
            dueDateEpochDay = newDueDay,
            dueTimeMinutes = newDueTime,
            postponeReason = reason,
            postponeCount = TaskRules.incrementPostpone(current.postponeCount),
            status = TaskStatus.POSTPONED.key,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.update(updated)
        return updated.toDomain()
    }

    suspend fun addSubtask(parentId: Long, title: String, description: String = ""): Long? {
        val parent = taskDao.getById(parentId) ?: return null
        // Максимум один уровень вложенности подзадач: корень(0) → подзадача(1) → вложенная(2)
        if (parent.nestingLevel >= 2) return null
        val task = TaskItem(
            title = title,
            description = description,
            parentTaskId = parentId,
            nestingLevel = parent.nestingLevel + 1,
            priority = TaskPriority.fromKey(parent.priority),
            dueDateEpochDay = parent.dueDateEpochDay
        )
        return saveTask(task)
    }

    suspend fun saveEnhancement(idea: EnhancementIdea): Long {
        val entity = idea.toEntity()
        return if (idea.id == 0L) {
            enhancementDao.insert(entity)
        } else {
            enhancementDao.update(entity)
            idea.id
        }
    }

    suspend fun deferEnhancement(id: Long, reason: String?): EnhancementIdea? {
        val current = enhancementDao.getById(id) ?: return null
        val updated = current.copy(
            status = EnhancementStatus.DEFERRED.key,
            deferredReason = reason
        )
        enhancementDao.update(updated)
        return updated.toDomain()
    }

    suspend fun convertEnhancementToTask(enhancementId: Long): TaskItem? {
        val idea = enhancementDao.getById(enhancementId) ?: return null
        val newId = saveTask(
            TaskItem(
                title = idea.title,
                description = idea.description,
                relatedToTaskId = idea.sourceTaskId,
                priority = TaskPriority.fromKey(idea.priority),
                dueDateEpochDay = idea.plannedDateEpochDay
            )
        )
        enhancementDao.update(
            idea.copy(
                convertedTaskId = newId,
                status = EnhancementStatus.PLANNED.key
            )
        )
        return getTask(newId)
    }

    suspend fun daySummary(epochDay: Long): DaySummary {
        val tasks = taskDao.observeForDay(epochDay)
        // one-shot via getBetween
        val list = taskDao.getBetweenDays(epochDay, epochDay)
            .filter { it.parentTaskId == null && it.nestingLevel == 0 }
            .map { it.toDomain() }
        return DaySummaryCalculator.summarize(list, LocalDate.ofEpochDay(epochDay))
    }

    suspend fun stats(): StatsSnapshot {
        val roots = taskDao.getAllRootsOnce().map { it.toDomain() }
        return StatsCalculator.compute(roots)
    }

    suspend fun replaceAll(tasks: List<TaskItem>, enhancements: List<EnhancementIdea>) {
        taskDao.deleteAll()
        enhancementDao.deleteAll()
        if (tasks.isNotEmpty()) {
            taskDao.insertAll(tasks.map { it.toEntity() })
        }
        if (enhancements.isNotEmpty()) {
            enhancementDao.insertAll(enhancements.map { it.toEntity() })
        }
    }

    /**
     * Объединение без дублей: записи с тем же id обновляются (REPLACE),
     * связи parentTaskId / sourceTaskId сохраняются. Повторный импорт того же файла
     * не размножает задачи.
     */
    suspend fun mergeImport(tasks: List<TaskItem>, enhancements: List<EnhancementIdea>) {
        val now = System.currentTimeMillis()
        val orderedTasks = tasks.sortedWith(
            compareBy<TaskItem> { it.nestingLevel }.thenBy { it.parentTaskId ?: 0L }.thenBy { it.id }
        )
        orderedTasks.forEach { task ->
            if (task.id == 0L) {
                saveTask(task)
            } else {
                taskDao.insert(task.copy(updatedAt = now).toEntity())
            }
        }
        enhancements.forEach { idea ->
            if (idea.id == 0L) {
                saveEnhancement(idea)
            } else {
                enhancementDao.insert(idea.toEntity())
            }
        }
    }

    suspend fun clearDemo() {
        taskDao.deleteDemo()
    }

    suspend fun hasDemo(): Boolean = taskDao.countDemo() > 0

    suspend fun loadDemoIfNeeded(): Boolean {
        if (taskDao.countDemo() > 0) return false
        val today = LocalDate.now().toEpochDay()
        val demos = listOf(
            TaskItem(
                title = "Утренний обзор дня",
                description = "Демо: посмотреть задачи и выбрать главное",
                dueDateEpochDay = today,
                priority = TaskPriority.HIGH,
                status = TaskStatus.IN_PROGRESS,
                category = "Демо",
                isDemo = true
            ),
            TaskItem(
                title = "Прогулка с Марсом",
                description = "Демо-задача",
                dueDateEpochDay = today,
                priority = TaskPriority.NORMAL,
                status = TaskStatus.NEW,
                category = "Демо",
                isDemo = true
            ),
            TaskItem(
                title = "Разобрать старые заметки",
                dueDateEpochDay = today - 2,
                priority = TaskPriority.LOW,
                status = TaskStatus.POSTPONED,
                postponeCount = 2,
                category = "Демо",
                isDemo = true
            )
        )
        demos.forEach { saveTask(it) }
        return true
    }

    suspend fun exportSnapshot(): Pair<List<TaskItem>, List<EnhancementIdea>> {
        val tasks = taskDao.getAllOnce().map { it.toDomain() }
        val ideas = enhancementDao.getAllOnce().map { it.toDomain() }
        return tasks to ideas
    }

    suspend fun countsForCalendar(fromDay: Long, toDay: Long): Map<Long, Int> {
        return taskDao.getBetweenDays(fromDay, toDay)
            .filter { it.dueDateEpochDay != null }
            .groupBy { it.dueDateEpochDay!! }
            .mapValues { it.value.size }
    }
}
