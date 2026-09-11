package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.TASK_GROUP_NO_PROJECT
import com.mars.planner.domain.logic.TaskFiltering
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.TaskFilter
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Поведение элемента выполнения в списке «Задачи».
 * Статус меняется тем же смыслом, что [com.mars.planner.screens.AppViewModel.changeStatus]
 * → [com.mars.planner.data.PlannerRepository.setTaskStatus].
 */
class Stage9TaskDoneToggleTest {

    private val today = LocalDate.of(2026, 9, 11)
    private val zone = ZoneOffset.UTC

    private fun project(uuid: String, name: String) =
        ProjectItem(id = uuid.hashCode().toLong(), syncUuid = uuid, name = name)

    private fun task(
        id: Long,
        title: String,
        project: String? = "p1",
        status: TaskStatus = TaskStatus.OPEN,
        due: Long? = 1_000L
    ) = TaskItem(
        id = id,
        syncUuid = "t-$id",
        title = title,
        projectSyncUuid = project,
        status = status,
        dueAtEpochMillis = due,
        priority = TaskPriority.NORMAL,
        updatedAt = id
    )

    @Test
    fun openToggleMarksDone() {
        assertThat(TaskStatusToggle.nextStatus(TaskStatus.OPEN)).isEqualTo(TaskStatus.DONE)
        assertThat(TaskStatusToggle.accessibilityLabel(TaskStatus.OPEN))
            .isEqualTo("Отметить задачу выполненной")
    }

    @Test
    fun doneToggleReopens() {
        assertThat(TaskStatusToggle.nextStatus(TaskStatus.DONE)).isEqualTo(TaskStatus.OPEN)
        assertThat(TaskStatusToggle.accessibilityLabel(TaskStatus.DONE))
            .isEqualTo("Вернуть задачу в открытые")
    }

    @Test
    fun applyLocallyChangesOnlyStatus() {
        val original = task(7, "Work", project = "proj-a", status = TaskStatus.OPEN)
        val toggled = TaskStatusToggle.applyLocally(original)
        assertThat(toggled.status).isEqualTo(TaskStatus.DONE)
        assertThat(toggled.projectSyncUuid).isEqualTo("proj-a")
        assertThat(toggled.title).isEqualTo("Work")
        assertThat(toggled.description).isEqualTo(original.description)
        assertThat(toggled.dueAtEpochMillis).isEqualTo(original.dueAtEpochMillis)
        assertThat(toggled.priority).isEqualTo(original.priority)
        assertThat(toggled.syncUuid).isEqualTo(original.syncUuid)
        assertThat(TaskStatusToggle.applyLocally(toggled).status).isEqualTo(TaskStatus.OPEN)
    }

    @Test
    fun pendingGuardAcceptsOnce() {
        assertThat(TaskStatusToggle.acceptGesture("t-5", emptySet())).isTrue()
        assertThat(TaskStatusToggle.acceptGesture("t-5", setOf("t-5"))).isFalse()
        assertThat(TaskStatusToggle.acceptGesture("t-6", setOf("t-5"))).isTrue()
        assertThat(TaskStatusToggle.acceptGesture("", emptySet())).isFalse()
    }

    @Test
    fun separateGesturesDoNotMix() {
        // Модель жестов списка: toggle и open — разные коллбеки TaskCard.
        var opened = 0
        var toggled = 0
        val onOpen: () -> Unit = { opened++ }
        val onToggle: () -> Unit = { toggled++ }
        onToggle()
        assertThat(toggled).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
        onOpen()
        assertThat(opened).isEqualTo(1)
        assertThat(toggled).isEqualTo(1)
    }

    @Test
    fun singleStatusChangePerAcceptedGesture() {
        var status = TaskStatus.OPEN
        var pending = setOf<String>()
        val key = "t-42"
        fun attempt() {
            if (!TaskStatusToggle.acceptGesture(key, pending)) return
            pending = pending + key
            status = TaskStatusToggle.nextStatus(status)
            // имитация: второй жест до снятия pending
            if (!TaskStatusToggle.acceptGesture(key, pending)) {
                // ignored
            } else {
                status = TaskStatusToggle.nextStatus(status)
            }
            pending = pending - key
        }
        attempt()
        assertThat(status).isEqualTo(TaskStatus.DONE)
    }

    @Test
    fun groupCountAndProgressRecalculateAfterToggle() {
        val projects = listOf(project("p1", "Alpha"))
        val before = listOf(
            task(1, "a", status = TaskStatus.OPEN),
            task(2, "b", status = TaskStatus.OPEN),
            task(3, "c", status = TaskStatus.DONE)
        )
        val groupsBefore = TaskGroupsLogic.buildGroups(projects, before, false)
        assertThat(groupsBefore.single().count).isEqualTo(3)

        val after = before.map { if (it.id == 1L) TaskStatusToggle.applyLocally(it) else it }
        val groupsAfter = TaskGroupsLogic.buildGroups(projects, after, false)
        assertThat(groupsAfter.single().count).isEqualTo(3)

        val statsBefore = ProjectWithStats(
            project = projects[0],
            totalTasks = before.size,
            doneTasks = before.count { it.status == TaskStatus.DONE }
        )
        val statsAfter = ProjectWithStats(
            project = projects[0],
            totalTasks = after.size,
            doneTasks = after.count { it.status == TaskStatus.DONE }
        )
        assertThat(statsBefore.progressPercent).isEqualTo(33)
        assertThat(statsAfter.doneTasks).isEqualTo(2)
        assertThat(statsAfter.openTasks).isEqualTo(1)
        assertThat(statsAfter.progressPercent).isEqualTo(66)
    }

    @Test
    fun openFilterHidesCompletedAfterToggle() {
        val tasks = listOf(task(1, "a"), task(2, "b"))
        val filteredOpen = TaskFiltering.apply(tasks, TaskFilter.OPEN, today, zone)
        assertThat(filteredOpen).hasSize(2)

        val after = tasks.map { if (it.id == 1L) TaskStatusToggle.applyLocally(it) else it }
        val stillOpen = TaskFiltering.apply(after, TaskFilter.OPEN, today, zone)
        assertThat(stillOpen.map { it.id }).containsExactly(2L)
        val doneOnly = TaskFiltering.apply(after, TaskFilter.DONE, today, zone)
        assertThat(doneOnly.map { it.id }).containsExactly(1L)
    }

    @Test
    fun emptyGroupHiddenAfterFilter() {
        val projects = listOf(project("p1", "Alpha"), project("p2", "Beta"))
        val tasks = listOf(
            task(1, "only-p1", project = "p1", status = TaskStatus.OPEN),
            task(2, "p2-done", project = "p2", status = TaskStatus.DONE)
        )
        val open = TaskFiltering.apply(tasks, TaskFilter.OPEN, today, zone)
        val groups = TaskGroupsLogic.buildGroups(projects, open, false)
        assertThat(groups.map { it.key }).containsExactly("p1")
        assertThat(groups.any { it.key == "p2" }).isFalse()
    }

    @Test
    fun expandAndPinPrefsUnaffectedByStatusToggle() {
        val projects = listOf(project("p1", "Alpha"))
        val tasks = listOf(task(1, "a"), task(2, "n", project = null))
        val groups = TaskGroupsLogic.buildGroups(projects, tasks, noProjectPinnedTop = true)
        val expanded = TaskGroupsLogic.defaultExpandedKeys(groups)
        val afterStatus = tasks.map { TaskStatusToggle.applyLocally(it) }
        val groupsAfter = TaskGroupsLogic.buildGroups(projects, afterStatus, noProjectPinnedTop = true)
        assertThat(groupsAfter.first().key).isEqualTo(TASK_GROUP_NO_PROJECT)
        assertThat(
            TaskGroupsLogic.isExpanded(
                TASK_GROUP_NO_PROJECT,
                groupsAfter,
                userConfigured = true,
                storedExpandedKeys = expanded
            )
        ).isTrue()
    }

    @Test
    fun statusToggleUsesSameNextAsEditScreenSemantics() {
        // Экран редактирования вызывает changeStatus(id, OPEN|DONE) напрямую;
        // список передаёт nextStatus(...) в тот же changeStatus.
        val open = task(9, "x", status = TaskStatus.OPEN)
        val targetFromList = TaskStatusToggle.nextStatus(open.status)
        assertThat(targetFromList).isEqualTo(TaskStatus.DONE)
        val reopened = TaskStatusToggle.nextStatus(targetFromList)
        assertThat(reopened).isEqualTo(TaskStatus.OPEN)
    }
}
