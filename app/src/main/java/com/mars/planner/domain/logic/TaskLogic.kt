package com.mars.planner.domain.logic

import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.StatsSnapshot
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    fun canAutoComplete(hasIncompleteSubtasks: Boolean): Boolean = !hasIncompleteSubtasks

    fun requiresCompleteConfirmation(hasIncompleteSubtasks: Boolean): Boolean =
        hasIncompleteSubtasks

    fun canCreateNestedSubtask(parentNestingLevel: Int): Boolean =
        parentNestingLevel < 2

    fun nextNestingLevel(parentNestingLevel: Int): Int =
        (parentNestingLevel + 1).coerceAtMost(2)

    fun incrementPostpone(current: Int): Int = current + 1

    fun isOverdue(task: TaskItem, today: LocalDate = LocalDate.now()): Boolean {
        val due = task.dueDateEpochDay ?: return false
        if (task.status == TaskStatus.DONE || task.status == TaskStatus.CANCELLED) return false
        return due < today.toEpochDay()
    }

    fun isRootTask(task: TaskItem): Boolean =
        task.parentTaskId == null && task.nestingLevel == 0

    fun onlyRootTasks(tasks: List<TaskItem>): List<TaskItem> =
        tasks.filter { isRootTask(it) }
}

object DaySummaryCalculator {
    fun summarize(tasks: List<TaskItem>, today: LocalDate = LocalDate.now()): DaySummary {
        var done = 0
        var inProgress = 0
        var overdue = 0
        var postponed = 0
        var newCount = 0
        tasks.forEach { task ->
            when {
                task.status == TaskStatus.DONE -> done++
                TaskRules.isOverdue(task, today) -> overdue++
                task.status == TaskStatus.IN_PROGRESS -> inProgress++
                task.status == TaskStatus.POSTPONED -> postponed++
                task.status == TaskStatus.NEW -> newCount++
            }
        }
        return DaySummary(
            total = tasks.size,
            done = done,
            inProgress = inProgress,
            overdue = overdue,
            postponed = postponed,
            newCount = newCount
        )
    }
}

object StatsCalculator {
    fun compute(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StatsSnapshot {
        val roots = TaskRules.onlyRootTasks(tasks)
        val weekStart = today.with(DayOfWeek.MONDAY).toEpochDay()
        val monthStart = today.withDayOfMonth(1).toEpochDay()
        val todayDay = today.toEpochDay()

        val completedWeek = roots.count {
            it.status == TaskStatus.DONE &&
                dayOf(it.updatedAt, zoneId) in weekStart..todayDay
        }
        val completedMonth = roots.count {
            it.status == TaskStatus.DONE &&
                dayOf(it.updatedAt, zoneId) in monthStart..todayDay
        }
        val postponeCount = roots.sumOf { it.postponeCount }
        val overdueCount = roots.count { TaskRules.isOverdue(it, today) }

        val periodTasks = roots.filter { task ->
            val due = task.dueDateEpochDay
            due != null && due in monthStart..todayDay
        }
        val completionPercent = if (periodTasks.isEmpty()) {
            0
        } else {
            val done = periodTasks.count { it.status == TaskStatus.DONE }
            ((done.toDouble() / periodTasks.size) * 100).toInt()
        }

        return StatsSnapshot(
            completedWeek = completedWeek,
            completedMonth = completedMonth,
            postponeCount = postponeCount,
            overdueCount = overdueCount,
            completionPercent = completionPercent,
            productiveStreak = productiveStreak(roots, today, zoneId)
        )
    }

    fun productiveStreak(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val roots = TaskRules.onlyRootTasks(tasks)
        var streak = 0
        var cursor = today
        while (true) {
            val day = cursor.toEpochDay()
            val productive = roots.any { task ->
                task.status == TaskStatus.DONE &&
                    task.dueDateEpochDay == day &&
                    dayOf(task.updatedAt, zoneId) == day
            } || roots.any { task ->
                task.status == TaskStatus.DONE && dayOf(task.updatedAt, zoneId) == day
            }
            if (!productive) break
            streak++
            cursor = cursor.minusDays(1)
            if (streak > 365) break
        }
        return streak
    }

    private fun dayOf(millis: Long, zoneId: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate().toEpochDay()
}

object MoodFromDay {
    fun resolve(summary: DaySummary): MarsMood = when {
        summary.overdue >= 3 -> MarsMood.STRICT
        summary.overdue > 0 -> MarsMood.OVERDUE
        summary.done > 0 && summary.done == summary.total && summary.total > 0 -> MarsMood.DONE
        summary.inProgress > 0 -> MarsMood.WORKING
        summary.postponed > 0 -> MarsMood.POSTPONED
        else -> MarsMood.DEFAULT
    }
}
