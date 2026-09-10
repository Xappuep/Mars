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
    fun isOverdue(task: TaskItem, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val due = task.dueAtEpochMillis ?: return false
        if (task.status == TaskStatus.DONE) return false
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return dueDay.isBefore(today)
    }

    fun isDueToday(task: TaskItem, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val due = task.dueAtEpochMillis ?: return false
        if (task.status == TaskStatus.DONE) return false
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return dueDay == today
    }
}

object TodayTasksSelector {
    fun belongsOnToday(task: TaskItem, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (task.status == TaskStatus.DONE) return false
        val due = task.dueAtEpochMillis ?: return false
        val dueDay = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return !dueDay.isAfter(today)
    }

    fun select(tasks: List<TaskItem>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<TaskItem> =
        tasks.filter { belongsOnToday(it, today, zone) }
            .sortedWith(
                compareBy(
                    { if (TaskRules.isOverdue(it, today, zone)) 0 else 1 },
                    { it.dueAtEpochMillis ?: Long.MAX_VALUE },
                    { -it.priority.rank },
                    { -it.updatedAt }
                )
            )

    /**
     * Список «Сегодня» вместе с закрытыми сегодня задачами — чтобы фильтр
     * «Выполнено» и счётчик «Готово» показывали результат дня.
     */
    fun selectDayBoard(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskItem> {
        val open = select(tasks, today, zone)
        val doneToday = tasks.filter { task ->
            task.status == TaskStatus.DONE && dayOf(task.updatedAt, zone) == today
        }.sortedByDescending { it.updatedAt }
        return open + doneToday
    }

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

object DaySummaryCalculator {
    fun summarize(tasks: List<TaskItem>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): DaySummary {
        var done = 0
        var overdue = 0
        var open = 0
        tasks.forEach { task ->
            when {
                task.status == TaskStatus.DONE -> done++
                TaskRules.isOverdue(task, today, zone) -> overdue++
                else -> open++
            }
        }
        return DaySummary(
            total = tasks.size,
            done = done,
            open = open,
            overdue = overdue
        )
    }
}

object StatsCalculator {
    fun compute(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StatsSnapshot {
        val weekStart = today.with(DayOfWeek.MONDAY)
        val monthStart = today.withDayOfMonth(1)
        val completedWeek = tasks.count {
            it.status == TaskStatus.DONE &&
                dayOf(it.updatedAt, zoneId) in weekStart.toEpochDay()..today.toEpochDay()
        }
        val completedMonth = tasks.count {
            it.status == TaskStatus.DONE &&
                dayOf(it.updatedAt, zoneId) in monthStart.toEpochDay()..today.toEpochDay()
        }
        val overdueCount = tasks.count { TaskRules.isOverdue(it, today, zoneId) }
        val periodTasks = tasks.filter { task ->
            val due = task.dueAtEpochMillis ?: return@filter false
            val d = dayOf(due, zoneId)
            d in monthStart.toEpochDay()..today.toEpochDay()
        }
        val completionPercent = if (periodTasks.isEmpty()) 0 else {
            val done = periodTasks.count { it.status == TaskStatus.DONE }
            ((done.toDouble() / periodTasks.size) * 100).toInt()
        }
        return StatsSnapshot(
            completedWeek = completedWeek,
            completedMonth = completedMonth,
            openCount = tasks.count { it.status != TaskStatus.DONE },
            overdueCount = overdueCount,
            completionPercent = completionPercent,
            productiveStreak = productiveStreak(tasks, today, zoneId)
        )
    }

    fun productiveStreak(
        tasks: List<TaskItem>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        var streak = 0
        var cursor = today
        while (true) {
            val day = cursor.toEpochDay()
            val productive = tasks.any {
                it.status == TaskStatus.DONE && dayOf(it.updatedAt, zoneId) == day
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
        summary.open > 0 -> MarsMood.WORKING
        else -> MarsMood.DEFAULT
    }
}

/** Отбор задач по фильтру списка. */
object TaskFiltering {
    fun apply(
        tasks: List<TaskItem>,
        filter: com.mars.planner.domain.model.TaskFilter,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskItem> = when (filter) {
        com.mars.planner.domain.model.TaskFilter.ALL -> tasks
        com.mars.planner.domain.model.TaskFilter.OPEN -> tasks.filter { it.status == TaskStatus.OPEN }
        com.mars.planner.domain.model.TaskFilter.DONE -> tasks.filter { it.status == TaskStatus.DONE }
        com.mars.planner.domain.model.TaskFilter.OVERDUE -> tasks.filter { TaskRules.isOverdue(it, today, zone) }
    }
}
