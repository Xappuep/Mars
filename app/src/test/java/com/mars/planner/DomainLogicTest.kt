package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import com.mars.planner.motivator.MarsMotivator
import com.mars.planner.domain.model.MotivatorMode
import org.junit.Test
import java.time.LocalDate

class DomainLogicTest {

    @Test
    fun postponeCountIncrements() {
        assertThat(TaskRules.incrementPostpone(0)).isEqualTo(1)
        assertThat(TaskRules.incrementPostpone(2)).isEqualTo(3)
    }

    @Test
    fun nestedSubtaskDepthLimited() {
        assertThat(TaskRules.canCreateNestedSubtask(0)).isTrue()
        assertThat(TaskRules.nextNestingLevel(0)).isEqualTo(1)
        assertThat(TaskRules.nextNestingLevel(1)).isEqualTo(2)
        assertThat(TaskRules.nextNestingLevel(2)).isEqualTo(2)
    }

    @Test
    fun autoCompleteBlockedByIncompleteSubtasks() {
        assertThat(TaskRules.canAutoComplete(hasIncompleteSubtasks = true)).isFalse()
        assertThat(TaskRules.requiresCompleteConfirmation(true)).isTrue()
        assertThat(TaskRules.canAutoComplete(hasIncompleteSubtasks = false)).isTrue()
    }

    @Test
    fun daySummaryCountsStatuses() {
        val today = LocalDate.of(2026, 8, 21)
        val tasks = listOf(
            TaskItem(title = "a", status = TaskStatus.DONE, dueDateEpochDay = today.toEpochDay()),
            TaskItem(title = "b", status = TaskStatus.IN_PROGRESS, dueDateEpochDay = today.toEpochDay()),
            TaskItem(title = "c", status = TaskStatus.NEW, dueDateEpochDay = today.minusDays(2).toEpochDay()),
            TaskItem(title = "d", status = TaskStatus.POSTPONED, dueDateEpochDay = today.toEpochDay())
        )
        val summary = DaySummaryCalculator.summarize(tasks, today)
        assertThat(summary.total).isEqualTo(4)
        assertThat(summary.done).isEqualTo(1)
        assertThat(summary.inProgress).isEqualTo(1)
        assertThat(summary.overdue).isEqualTo(1)
        assertThat(summary.postponed).isEqualTo(1)
    }

    @Test
    fun statsCompletionPercent() {
        val today = LocalDate.of(2026, 8, 21)
        val tasks = listOf(
            TaskItem(
                title = "done",
                status = TaskStatus.DONE,
                dueDateEpochDay = today.toEpochDay(),
                updatedAt = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            ),
            TaskItem(
                title = "open",
                status = TaskStatus.NEW,
                dueDateEpochDay = today.toEpochDay()
            )
        )
        val stats = StatsCalculator.compute(tasks, today, java.time.ZoneOffset.UTC)
        assertThat(stats.completionPercent).isEqualTo(50)
        assertThat(stats.postponeCount).isEqualTo(0)
    }

    @Test
    fun exportImportRoundTripPreservesTasks() {
        val tasks = listOf(
            TaskItem(id = 1, title = "Тест", status = TaskStatus.DONE, postponeCount = 2)
        )
        val json = BackupCodec.toJson(tasks, emptyList(), null)
        val parsed = BackupCodec.fromJson(json)
        assertThat(parsed.tasks).hasSize(1)
        assertThat(parsed.tasks.first().title).isEqualTo("Тест")
        assertThat(parsed.tasks.first().postponeCount).isEqualTo(2)
        assertThat(BackupCodec.parseTaskCount(json)).isEqualTo(1)
        val csv = BackupCodec.toCsv(tasks)
        assertThat(csv).contains("title")
        assertThat(csv).contains("Тест")
    }

    @Test
    fun jsonKeepsSubtasksAndEnhancementsLinkedNotAsRootOnlyPayload() {
        val today = LocalDate.of(2026, 8, 21).toEpochDay()
        val tasks = listOf(
            TaskItem(
                id = 1,
                title = "Основная А",
                status = TaskStatus.IN_PROGRESS,
                priority = com.mars.planner.domain.model.TaskPriority.HIGH,
                dueDateEpochDay = today,
                reminderAtEpochMillis = 1_725_000_000_000L
            ),
            TaskItem(
                id = 2,
                title = "Основная Б",
                status = TaskStatus.DONE,
                priority = com.mars.planner.domain.model.TaskPriority.LOW,
                dueDateEpochDay = today + 1
            ),
            TaskItem(
                id = 3,
                title = "Подзадача А1",
                status = TaskStatus.NEW,
                parentTaskId = 1,
                nestingLevel = 1,
                dueDateEpochDay = today
            )
        )
        val enhancements = listOf(
            com.mars.planner.domain.model.EnhancementIdea(
                id = 10,
                sourceTaskId = 1,
                title = "Идея к А",
                description = "Улучшить формулировку"
            )
        )
        val json = BackupCodec.toJson(tasks, enhancements, com.mars.planner.data.prefs.AppSettings(userName = "Михаил"))
        val parsed = BackupCodec.fromJson(json)
        assertThat(parsed.tasks).hasSize(3)
        assertThat(parsed.enhancements).hasSize(1)
        assertThat(parsed.settings?.userName).isEqualTo("Михаил")
        val roots = parsed.tasks.filter { it.parentTaskId == null && it.nestingLevel == 0 }
        val subs = parsed.tasks.filter { it.parentTaskId != null }
        assertThat(roots).hasSize(2)
        assertThat(subs).hasSize(1)
        assertThat(subs.first().parentTaskId).isEqualTo(1)
        assertThat(parsed.enhancements.first().sourceTaskId).isEqualTo(1)
        assertThat(roots.any { it.reminderAtEpochMillis != null }).isTrue()
        val csv = BackupCodec.toCsv(TaskRules.onlyRootTasks(tasks))
        assertThat(csv.lines().size).isEqualTo(3) // header + 2 roots
        assertThat(csv).contains("Основная А")
        assertThat(csv).contains("Основная Б")
        assertThat(csv).doesNotContain("Подзадача А1")
    }

    @Test
    fun motivatorSoftThenStrictOnRepeatedPostpone() {
        val soft = MarsMotivator.reactionForStatusChange(
            TaskStatus.POSTPONED, postponeCount = 1, mode = MotivatorMode.ADAPTIVE
        )
        val strict = MarsMotivator.reactionForStatusChange(
            TaskStatus.POSTPONED, postponeCount = 3, mode = MotivatorMode.ADAPTIVE
        )
        assertThat(soft.message).contains("реальное")
        assertThat(strict.message).contains("раз")
    }

    @Test
    fun statsIgnoreSubtasksAsSeparateItems() {
        val today = LocalDate.of(2026, 8, 21)
        val tasks = listOf(
            TaskItem(id = 1, title = "root", status = TaskStatus.DONE, dueDateEpochDay = today.toEpochDay(),
                updatedAt = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()),
            TaskItem(id = 2, title = "sub", status = TaskStatus.DONE, parentTaskId = 1, nestingLevel = 1,
                dueDateEpochDay = today.toEpochDay(),
                updatedAt = today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        )
        val stats = StatsCalculator.compute(tasks, today, java.time.ZoneOffset.UTC)
        assertThat(stats.completedMonth).isEqualTo(1)
        assertThat(TaskRules.onlyRootTasks(tasks)).hasSize(1)
    }
}
