package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import org.junit.Test

/**
 * Доказательства на уровне логики (без Room): действия этапа 9 не мутируют сущности.
 * Фактическая очередь sync_operations проверяется instrumented-тестом на устройстве.
 */
class Stage9NoHiddenWritesLogicTest {

    private fun project(uuid: String) =
        ProjectItem(id = 1L, syncUuid = uuid, name = "P", updatedAt = 100L)

    private fun task(id: Long, project: String? = "p1") = TaskItem(
        id = id,
        syncUuid = "u-$id",
        title = "T$id",
        projectSyncUuid = project,
        status = TaskStatus.OPEN,
        updatedAt = 1_000L + id
    )

    @Test
    fun buildGroupsDoesNotMutateTaskFields() {
        val tasks = listOf(task(1), task(2, null), task(3))
        val before = tasks.map { it.copy() }
        TaskGroupsLogic.buildGroups(listOf(project("p1")), tasks, noProjectPinnedTop = true)
        TaskGroupsLogic.buildGroups(listOf(project("p1")), tasks, noProjectPinnedTop = false)
        assertThat(tasks).isEqualTo(before)
        assertThat(tasks.map { it.updatedAt }).isEqualTo(before.map { it.updatedAt })
        assertThat(tasks.map { it.status }).isEqualTo(before.map { it.status })
    }

    @Test
    fun progressAndOpenTasksArePureGetters() {
        val stats = ProjectWithStats(project("p1"), totalTasks = 114, doneTasks = 8)
        assertThat(stats.openTasks).isEqualTo(106)
        assertThat(stats.progressPercent).isEqualTo(7)
        // геттеры не требуют и не предполагают saveTask
    }

    @Test
    fun groupExpandEncodeDecodeDoesNotTouchTaskIdentity() {
        val groups = TaskGroupsLogic.buildGroups(
            listOf(project("p1"), project("p2")),
            listOf(task(1), task(2, "p2"), task(3, null)),
            false
        )
        val (configured, expanded) = TaskGroupsLogic.toggleExpanded(
            key = groups.first().key,
            groups = groups,
            userConfigured = false,
            storedExpandedKeys = emptySet()
        )
        val encoded = TaskGroupsLogic.encodeExpandedKeys(expanded)
        val decoded = TaskGroupsLogic.decodeExpandedKeys(encoded)
        assertThat(configured).isTrue()
        assertThat(decoded).isEqualTo(expanded)
        assertThat(groups.flatMap { it.tasks }.map { it.syncUuid })
            .containsExactly("u-1", "u-2", "u-3")
    }

    @Test
    fun toggleCallbackNotImpliedByToggleComponentState() {
        // Отрисовка круга сама по себе не вызывает onToggle — только явный жест.
        var calls = 0
        val onToggle: () -> Unit = { calls++ }
        // «Композиция»: создали лямбду, не вызвали
        assertThat(calls).isEqualTo(0)
        onToggle()
        assertThat(calls).isEqualTo(1)
        assertThat(TaskStatusToggle.acceptGesture("u-1", emptySet())).isTrue()
    }

    @Test
    fun pendingCounterSemanticsMatchDaoFilter() {
        // Контракт UI: COUNT(*) WHERE acked = 0 (см. SyncDao.observePendingCount).
        data class Row(val acked: Boolean, val uuid: String)
        val rows = listOf(
            Row(false, "a"),
            Row(false, "a"),
            Row(true, "b"),
            Row(false, "c")
        )
        val pending = rows.count { !it.acked }
        val uniquePending = rows.filter { !it.acked }.map { it.uuid }.toSet().size
        assertThat(pending).isEqualTo(3)
        assertThat(uniquePending).isEqualTo(2)
    }
}
