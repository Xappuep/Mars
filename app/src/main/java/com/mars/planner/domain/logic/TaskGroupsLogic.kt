package com.mars.planner.domain.logic

import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import java.time.LocalDate
import java.time.ZoneId

/** Локальный ключ группы задач без проекта (не sync UUID). */
const val TASK_GROUP_NO_PROJECT = "__no_project__"

data class TaskGroup(
    val key: String,
    val title: String,
    val tasks: List<TaskItem>,
    val isNoProject: Boolean
) {
    val count: Int get() = tasks.size
}

/**
 * Группировка и сортировка списка «Задачи» (только для отображения).
 * Не пишет в Room и не создаёт sync-операции.
 */
object TaskGroupsLogic {

    /**
     * Порядок внутри группы:
     * 1) просроченные открытые выше остальных;
     * 2) срок по возрастанию (без срока — в конце);
     * 3) приоритет по убыванию rank;
     * 4) updatedAt по убыванию;
     * 5) id — стабильный тай-брейк.
     */
    fun sortWithinGroup(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskItem> =
        tasks.sortedWith(
            compareBy<TaskItem> {
                if (it.status != TaskStatus.DONE && TaskRules.isOverdue(it, today, zone)) 0 else 1
            }
                .thenBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                .thenByDescending { it.priority.rank }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id }
        )

    /**
     * Строит группы по проектам из уже отфильтрованного списка задач.
     * Пустые группы не создаются. Порядок проектов — как в [projects] (имя ASC у DAO).
     */
    fun buildGroups(
        projects: List<ProjectItem>,
        filteredTasks: List<TaskItem>,
        noProjectPinnedTop: Boolean
    ): List<TaskGroup> {
        val byUuid = projects.associateBy { it.syncUuid }
        val sorted = filteredTasks // caller may pre-sort; we sort per group below
        val noProjectTasks = mutableListOf<TaskItem>()
        val byProject = linkedMapOf<String, MutableList<TaskItem>>()

        // Preserve project order from repository list (active+archived as provided).
        projects.forEach { byProject[it.syncUuid] = mutableListOf() }

        sorted.forEach { task ->
            val uuid = task.projectSyncUuid?.takeIf { it.isNotBlank() }
            if (uuid == null) {
                noProjectTasks.add(task)
            } else {
                val bucket = byProject.getOrPut(uuid) { mutableListOf() }
                bucket.add(task)
            }
        }

        val projectGroups = mutableListOf<TaskGroup>()
        // First: known projects in list order
        projects.forEach { project ->
            val tasks = byProject[project.syncUuid].orEmpty()
            if (tasks.isNotEmpty()) {
                projectGroups.add(
                    TaskGroup(
                        key = project.syncUuid,
                        title = project.name,
                        tasks = sortWithinGroup(tasks),
                        isNoProject = false
                    )
                )
            }
        }
        // Orphans: tasks pointing at deleted projects
        byProject.forEach { (uuid, tasks) ->
            if (uuid !in byUuid && tasks.isNotEmpty()) {
                projectGroups.add(
                    TaskGroup(
                        key = uuid,
                        title = "Проект недоступен",
                        tasks = sortWithinGroup(tasks),
                        isNoProject = false
                    )
                )
            }
        }

        val noProjectGroup = if (noProjectTasks.isEmpty()) {
            null
        } else {
            TaskGroup(
                key = TASK_GROUP_NO_PROJECT,
                title = "Без проекта",
                tasks = sortWithinGroup(noProjectTasks),
                isNoProject = true
            )
        }

        return when {
            noProjectGroup == null -> projectGroups
            noProjectPinnedTop -> listOf(noProjectGroup) + projectGroups
            else -> projectGroups + noProjectGroup
        }
    }

    /** Первое открытие: «Без проекта» и первая проектная группа раскрыты. */
    fun defaultExpandedKeys(groups: List<TaskGroup>): Set<String> {
        val keys = linkedSetOf<String>()
        groups.firstOrNull { it.isNoProject }?.let { keys.add(it.key) }
        groups.firstOrNull { !it.isNoProject }?.let { keys.add(it.key) }
        return keys
    }

    fun isExpanded(
        key: String,
        groups: List<TaskGroup>,
        userConfigured: Boolean,
        storedExpandedKeys: Set<String>
    ): Boolean {
        val effective = if (userConfigured) storedExpandedKeys else defaultExpandedKeys(groups)
        return key in effective
    }

    /** После первого жеста пользователя — зафиксировать текущие defaults + toggle. */
    fun toggleExpanded(
        key: String,
        groups: List<TaskGroup>,
        userConfigured: Boolean,
        storedExpandedKeys: Set<String>
    ): Pair<Boolean, Set<String>> {
        val base = if (userConfigured) storedExpandedKeys else defaultExpandedKeys(groups)
        val next = base.toMutableSet()
        if (!next.add(key)) next.remove(key)
        return true to pruneExpandedKeys(next, groups)
    }

    fun pruneExpandedKeys(expanded: Set<String>, groups: List<TaskGroup>): Set<String> {
        val alive = groups.map { it.key }.toSet()
        return expanded.filter { it in alive }.toSet()
    }

    fun encodeExpandedKeys(keys: Set<String>): String =
        keys.sorted().joinToString("\n")

    fun decodeExpandedKeys(raw: String): Set<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun openTaskCount(stats: ProjectWithStats): Int =
        (stats.totalTasks - stats.doneTasks).coerceAtLeast(0)

    fun progressPercent(open: Int, done: Int): Int {
        val total = open + done
        if (total <= 0) return 0
        return ((done.toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
    }
}
