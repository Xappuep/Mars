package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.MoodFromDay
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TaskFiltering
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.logic.TodayTasksSelector
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskFilter
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.motivator.MarsMotivator
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DomainLogicTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 9, 9)

    private fun dueAt(date: LocalDate, minutes: Int = 23 * 60 + 59): Long =
        date.atStartOfDay(zone).plusMinutes(minutes.toLong()).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        title: String = "Задача $id",
        status: TaskStatus = TaskStatus.OPEN,
        due: LocalDate? = today,
        priority: TaskPriority = TaskPriority.NORMAL,
        updatedAt: Long = dueAt(today)
    ) = TaskItem(
        id = id,
        syncUuid = "uuid-$id",
        title = title,
        status = status,
        priority = priority,
        dueAtEpochMillis = due?.let { dueAt(it) },
        createdAt = updatedAt,
        updatedAt = updatedAt
    )

    @Test
    fun overdueOnlyForOpenTasksWithPastDue() {
        assertThat(TaskRules.isOverdue(task(1, due = today.minusDays(1)), today)).isTrue()
        assertThat(TaskRules.isOverdue(task(2, due = today), today)).isFalse()
        assertThat(TaskRules.isOverdue(task(3, due = null), today)).isFalse()
        assertThat(
            TaskRules.isOverdue(task(4, status = TaskStatus.DONE, due = today.minusDays(3)), today)
        ).isFalse()
    }

    @Test
    fun todayBoardKeepsOverdueFirstAndAddsTasksClosedToday() {
        val overdue = task(1, due = today.minusDays(2))
        val dueToday = task(2, due = today)
        val future = task(3, due = today.plusDays(5))
        val doneToday = task(4, status = TaskStatus.DONE, due = today.minusDays(9))

        val board = TodayTasksSelector.selectDayBoard(
            listOf(future, dueToday, overdue, doneToday),
            today
        )

        assertThat(board.map { it.id }).containsExactly(1L, 2L, 4L).inOrder()
    }

    @Test
    fun daySummaryCountsOpenDoneAndOverdue() {
        val summary = DaySummaryCalculator.summarize(
            listOf(
                task(1, due = today),
                task(2, due = today.minusDays(1)),
                task(3, status = TaskStatus.DONE, due = today)
            ),
            today
        )

        assertThat(summary.total).isEqualTo(3)
        assertThat(summary.open).isEqualTo(1)
        assertThat(summary.overdue).isEqualTo(1)
        assertThat(summary.done).isEqualTo(1)
    }

    @Test
    fun filterSelectsOnlyRequestedStatuses() {
        val tasks = listOf(
            task(1, due = today),
            task(2, due = today.minusDays(1)),
            task(3, status = TaskStatus.DONE, due = today)
        )

        assertThat(TaskFiltering.apply(tasks, TaskFilter.ALL, today)).hasSize(3)
        assertThat(TaskFiltering.apply(tasks, TaskFilter.OPEN, today).map { it.id })
            .containsExactly(1L, 2L)
        assertThat(TaskFiltering.apply(tasks, TaskFilter.DONE, today).map { it.id })
            .containsExactly(3L)
        assertThat(TaskFiltering.apply(tasks, TaskFilter.OVERDUE, today).map { it.id })
            .containsExactly(2L)
    }

    @Test
    fun statsCountCompletionAndOverdue() {
        val stats = StatsCalculator.compute(
            listOf(
                task(1, status = TaskStatus.DONE, due = today, updatedAt = dueAt(today)),
                task(2, due = today),
                task(3, due = today.minusDays(2))
            ),
            today
        )

        assertThat(stats.completedWeek).isEqualTo(1)
        assertThat(stats.completedMonth).isEqualTo(1)
        assertThat(stats.openCount).isEqualTo(2)
        assertThat(stats.overdueCount).isEqualTo(1)
        assertThat(stats.completionPercent).isEqualTo(33)
        assertThat(stats.productiveStreak).isEqualTo(1)
    }

    @Test
    fun moodFollowsOverdueAndCompletion() {
        val allDone = DaySummaryCalculator.summarize(
            listOf(task(1, status = TaskStatus.DONE, due = today)),
            today
        )
        assertThat(MoodFromDay.resolve(allDone)).isEqualTo(MarsMood.DONE)

        val overdue = DaySummaryCalculator.summarize(
            listOf(task(1, due = today.minusDays(1))),
            today
        )
        assertThat(MoodFromDay.resolve(overdue)).isEqualTo(MarsMood.OVERDUE)

        val manyOverdue = DaySummaryCalculator.summarize(
            (1L..3L).map { task(it, due = today.minusDays(2)) },
            today
        )
        assertThat(MoodFromDay.resolve(manyOverdue)).isEqualTo(MarsMood.STRICT)
    }

    @Test
    fun motivatorReactsToDoneAndPostpone() {
        val done = MarsMotivator.reactionForStatusChange(
            newStatus = TaskStatus.DONE,
            overdueCount = 0,
            mode = MotivatorMode.ADAPTIVE
        )
        assertThat(done.mood).isEqualTo(MarsMood.DONE)
        assertThat(done.message).isNotEmpty()

        val postponed = MarsMotivator.reactionForPostpone(MotivatorMode.ADAPTIVE)
        assertThat(postponed.message).isNotEmpty()
    }

    @Test
    fun motivatorStaysSilentWhenTurnedOff() {
        val reaction = MarsMotivator.reactionForStatusChange(
            newStatus = TaskStatus.DONE,
            overdueCount = 0,
            mode = MotivatorMode.OFF
        )
        assertThat(reaction.message).isEmpty()
    }
}
