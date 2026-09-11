package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.TASK_GROUP_NO_PROJECT
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class Stage9ProjectsTasksLogicTest {

    private val today = LocalDate.of(2026, 9, 11)
    private val zone = ZoneOffset.UTC

    private fun project(uuid: String, name: String) = ProjectItem(
        id = uuid.hashCode().toLong(),
        syncUuid = uuid,
        name = name
    )

    private fun task(
        id: Long,
        title: String,
        project: String? = null,
        due: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        status: TaskStatus = TaskStatus.OPEN,
        updatedAt: Long = id
    ) = TaskItem(
        id = id,
        syncUuid = "t-$id",
        title = title,
        projectSyncUuid = project,
        dueAtEpochMillis = due,
        priority = priority,
        status = status,
        updatedAt = updatedAt
    )

    @Test
    fun progressHandlesZeroTasks() {
        val stats = ProjectWithStats(project("p1", "A"), totalTasks = 0, doneTasks = 0)
        assertThat(stats.openTasks).isEqualTo(0)
        assertThat(stats.progressPercent).isEqualTo(0)
        assertThat(TaskGroupsLogic.progressPercent(0, 0)).isEqualTo(0)
    }

    @Test
    fun progressUsesOpenAndDone() {
        val stats = ProjectWithStats(project("p1", "A"), totalTasks = 4, doneTasks = 1)
        assertThat(stats.openTasks).isEqualTo(3)
        assertThat(stats.progressPercent).isEqualTo(25)
        assertThat(TaskGroupsLogic.progressPercent(3, 1)).isEqualTo(25)
    }

    @Test
    fun groupsOnePerProjectNoDuplicates() {
        val projects = listOf(project("a", "Alpha"), project("b", "Beta"))
        val tasks = listOf(
            task(1, "t1", "a"),
            task(2, "t2", "a"),
            task(3, "t3", "b")
        )
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, noProjectPinnedTop = false)
        assertThat(groups.map { it.key }).containsExactly("a", "b").inOrder()
        assertThat(groups.map { it.key }.toSet().size).isEqualTo(groups.size)
        assertThat(groups.find { it.key == "a" }!!.count).isEqualTo(2)
    }

    @Test
    fun noProjectGroupSeparateAndHiddenWhenEmpty() {
        val projects = listOf(project("a", "Alpha"))
        val withNone = TaskGroupsLogic.buildGroups(
            projects,
            listOf(task(1, "x", null), task(2, "y", "a")),
            noProjectPinnedTop = false
        )
        assertThat(withNone.last().key).isEqualTo(TASK_GROUP_NO_PROJECT)
        assertThat(withNone.last().title).isEqualTo("Без проекта")

        val without = TaskGroupsLogic.buildGroups(
            projects,
            listOf(task(2, "y", "a")),
            noProjectPinnedTop = false
        )
        assertThat(without.any { it.isNoProject }).isFalse()
    }

    @Test
    fun pinNoProjectTopAndBottom() {
        val projects = listOf(project("a", "Alpha"))
        val tasks = listOf(task(1, "x", null), task(2, "y", "a"))
        val bottom = TaskGroupsLogic.buildGroups(projects, tasks, noProjectPinnedTop = false)
        assertThat(bottom.first().key).isEqualTo("a")
        assertThat(bottom.last().key).isEqualTo(TASK_GROUP_NO_PROJECT)
        val top = TaskGroupsLogic.buildGroups(projects, tasks, noProjectPinnedTop = true)
        assertThat(top.first().key).isEqualTo(TASK_GROUP_NO_PROJECT)
        assertThat(top.last().key).isEqualTo("a")
    }

    @Test
    fun defaultExpandedIsNoProjectAndFirstProject() {
        val projects = listOf(project("a", "Alpha"), project("b", "Beta"))
        val tasks = listOf(
            task(1, "n", null),
            task(2, "a1", "a"),
            task(3, "b1", "b")
        )
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, false)
        val expanded = TaskGroupsLogic.defaultExpandedKeys(groups)
        assertThat(expanded).containsExactly(TASK_GROUP_NO_PROJECT, "a")
        assertThat(TaskGroupsLogic.isExpanded("b", groups, false, emptySet())).isFalse()
        assertThat(TaskGroupsLogic.isExpanded("a", groups, false, emptySet())).isTrue()
    }

    @Test
    fun togglePersistsAndPrunesDeletedProject() {
        val projects = listOf(project("a", "Alpha"), project("b", "Beta"))
        val tasks = listOf(task(1, "a1", "a"), task(2, "b1", "b"), task(3, "n", null))
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, false)
        val (configured, afterToggle) = TaskGroupsLogic.toggleExpanded(
            "b", groups, userConfigured = false, storedExpandedKeys = emptySet()
        )
        assertThat(configured).isTrue()
        assertThat(afterToggle).contains("b")
        assertThat(afterToggle).contains(TASK_GROUP_NO_PROJECT)
        assertThat(afterToggle).contains("a")

        val smaller = TaskGroupsLogic.buildGroups(listOf(project("a", "Alpha")), listOf(task(1, "a1", "a")), false)
        val pruned = TaskGroupsLogic.pruneExpandedKeys(afterToggle, smaller)
        assertThat(pruned).containsExactly("a")
        assertThat(pruned).doesNotContain("b")
        assertThat(pruned).doesNotContain(TASK_GROUP_NO_PROJECT)
    }

    @Test
    fun encodeDecodeExpandedKeysStable() {
        val raw = TaskGroupsLogic.encodeExpandedKeys(setOf("b", "a", TASK_GROUP_NO_PROJECT))
        assertThat(TaskGroupsLogic.decodeExpandedKeys(raw))
            .containsExactly("a", "b", TASK_GROUP_NO_PROJECT)
    }

    @Test
    fun sortWithinGroupDueThenPriorityStable() {
        val early = 1_000L
        val late = 2_000L
        val tasks = listOf(
            task(1, "low-late", due = late, priority = TaskPriority.LOW, updatedAt = 10),
            task(2, "high-early", due = early, priority = TaskPriority.HIGH, updatedAt = 1),
            task(3, "normal-early", due = early, priority = TaskPriority.NORMAL, updatedAt = 5),
            task(4, "no-due-high", due = null, priority = TaskPriority.HIGH, updatedAt = 9),
            task(5, "same", due = late, priority = TaskPriority.LOW, updatedAt = 10)
        )
        val sorted = TaskGroupsLogic.sortWithinGroup(tasks, today, zone)
        assertThat(sorted.map { it.title }).containsExactly(
            "high-early",
            "normal-early",
            "low-late",
            "same",
            "no-due-high"
        ).inOrder()
        // Stable tie-break by id when due/priority/updatedAt equal
        assertThat(sorted[2].id).isLessThan(sorted[3].id)
    }

    @Test
    fun orphanDeletedProjectFormsSingleGroup() {
        val projects = listOf(project("a", "Alpha"))
        val tasks = listOf(task(1, "orphan", "gone"), task(2, "ok", "a"))
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, false)
        assertThat(groups.map { it.key }).containsExactly("a", "gone").inOrder()
        assertThat(groups.find { it.key == "gone" }!!.title).isEqualTo("Проект недоступен")
    }

    @Test
    fun emptyFilterHidesGroups() {
        val projects = listOf(project("a", "Alpha"))
        val groups = TaskGroupsLogic.buildGroups(projects, emptyList(), false)
        assertThat(groups).isEmpty()
    }
}
