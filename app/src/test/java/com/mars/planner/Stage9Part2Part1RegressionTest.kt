package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.MoodFromDay
import com.mars.planner.domain.logic.TASK_GROUP_NO_PROJECT
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import org.junit.Test

/**
 * Регрессии части 1 и визуальный путь Марса без текстовых реакций мотиватора.
 */
class Stage9Part2Part1RegressionTest {

    @Test
    fun moodFromDayStillResolvesMultipleVisualStates() {
        assertThat(MoodFromDay.resolve(DaySummary()))
            .isEqualTo(MarsMood.DEFAULT)
        assertThat(MoodFromDay.resolve(DaySummary(total = 2, done = 2, open = 0, overdue = 0)))
            .isEqualTo(MarsMood.DONE)
        assertThat(MoodFromDay.resolve(DaySummary(total = 3, done = 0, open = 3, overdue = 0)))
            .isEqualTo(MarsMood.WORKING)
        assertThat(MoodFromDay.resolve(DaySummary(total = 2, done = 0, open = 1, overdue = 1)))
            .isEqualTo(MarsMood.OVERDUE)
        assertThat(MoodFromDay.resolve(DaySummary(total = 3, done = 0, open = 0, overdue = 3)))
            .isEqualTo(MarsMood.STRICT)
    }

    @Test
    fun part1GroupAndToggleLogicUnchanged() {
        val projects = listOf(ProjectItem(id = 1, syncUuid = "p1", name = "A"))
        val tasks = listOf(
            TaskItem(id = 1, syncUuid = "t1", title = "1", projectSyncUuid = "p1", status = TaskStatus.OPEN),
            TaskItem(id = 2, syncUuid = "t2", title = "2", projectSyncUuid = null, status = TaskStatus.OPEN)
        )
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, noProjectPinnedTop = true)
        assertThat(groups.first().key).isEqualTo(TASK_GROUP_NO_PROJECT)
        assertThat(TaskStatusToggle.acceptGesture("t1", emptySet())).isTrue()
        assertThat(TaskStatusToggle.acceptGesture("t1", setOf("t1"))).isFalse()
    }
}
