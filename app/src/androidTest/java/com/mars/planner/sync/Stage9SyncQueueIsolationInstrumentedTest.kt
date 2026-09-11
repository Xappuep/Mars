package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.logic.TASK_GROUP_NO_PROJECT
import com.mars.planner.domain.logic.TaskGroupsLogic
import com.mars.planner.domain.logic.TaskStatusToggle
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Изолированная проверка: UI-логика этапа 9 (группы, pin, debounce)
 * не должна раздувать исходящую очередь sync_operations.
 *
 * Не трогает рабочую базу телефона. Требует устройство/эмулятор.
 */
@RunWith(AndroidJUnit4::class)
class Stage9SyncQueueIsolationInstrumentedTest {

    private lateinit var db: MarsDatabase
    private lateinit var repo: PlannerRepository

    private data class QueueSnapshot(
        val total: Int,
        val pending: Int,
        val acked: Int,
        val pendingByOp: Map<String, Int>,
        val pendingByType: Map<String, Int>,
        val pendingUniqueUuids: Int
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MarsDatabase.buildInMemory(context)
        repo = PlannerRepository(db) { "11111111-1111-4111-8111-111111111111" }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun snapshot(): QueueSnapshot {
        val all = db.query("SELECT acked, op, entityType, entityUuid FROM sync_operations", null).use { c ->
            val rows = mutableListOf<Quad>()
            while (c.moveToNext()) {
                rows += Quad(
                    acked = c.getInt(0) != 0,
                    op = c.getString(1),
                    type = c.getString(2),
                    uuid = c.getString(3)
                )
            }
            rows
        }
        val pending = all.filter { !it.acked }
        return QueueSnapshot(
            total = all.size,
            pending = pending.size,
            acked = all.count { it.acked },
            pendingByOp = pending.groupingBy { it.op }.eachCount(),
            pendingByType = pending.groupingBy { it.type }.eachCount(),
            pendingUniqueUuids = pending.map { it.uuid }.toSet().size
        )
    }

    private data class Quad(val acked: Boolean, val op: String, val type: String, val uuid: String)

    private suspend fun seedProjectsAndTasks(): List<TaskItem> {
        val projects = (1..3).map { i ->
            ProjectItem(
                syncUuid = UUID.randomUUID().toString(),
                name = "Proj $i",
                description = "d$i"
            )
        }
        projects.forEach { repo.saveProject(it) }
        val tasks = mutableListOf<TaskItem>()
        repeat(22) { i ->
            val projectUuid = when {
                i < 7 -> projects[0].syncUuid
                i < 14 -> projects[1].syncUuid
                i < 20 -> projects[2].syncUuid
                else -> null
            }
            val id = repo.saveTask(
                TaskItem(
                    syncUuid = UUID.randomUUID().toString(),
                    title = "Task $i",
                    projectSyncUuid = projectUuid,
                    priority = TaskPriority.NORMAL,
                    status = if (i % 9 == 0) TaskStatus.DONE else TaskStatus.OPEN
                )
            )
            tasks += repo.getTask(id)!!
        }
        return tasks
    }

    private suspend fun confirmEmptyOutgoingQueue() {
        val pending = repo.pendingOperations()
        if (pending.isNotEmpty()) {
            repo.ackOperations(pending.map { it.operationId })
        }
        assertThat(repo.pendingOperations()).isEmpty()
        assertThat(snapshot().pending).isEqualTo(0)
    }

    /** Имитация UI SettingsRepository.update для локальных ключей групп — без Room. */
    private fun applyLocalGroupPrefs(
        settings: AppSettings,
        transform: (AppSettings) -> AppSettings
    ): AppSettings = transform(settings)

    @Test
    fun stage9UiActionsDoNotCreateOutgoingOps_andOneToggleCreatesOne() = runBlocking {
        val tasks = seedProjectsAndTasks()
        val afterSeed = snapshot()
        assertThat(afterSeed.pending).isGreaterThan(0) // CREATE от сидирования

        confirmEmptyOutgoingQueue()
        val baseline = snapshot()
        assertThat(baseline.pending).isEqualTo(0)
        assertThat(baseline.acked).isEqualTo(afterSeed.total)

        val projects = repo.projectsOnce()
        val allTasks = repo.tasksOnce()
        val taskUpdatedAtBefore = allTasks.associate { it.syncUuid to it.updatedAt }

        // «Перезапуск / Сегодня / Задачи»: только чтение + построение групп
        TaskGroupsLogic.buildGroups(projects, allTasks, noProjectPinnedTop = false)
        assertThat(snapshot().pending).isEqualTo(0)

        // Раскрыть / свернуть группы (DataStore-логика)
        var settings = AppSettings()
        val groups = TaskGroupsLogic.buildGroups(projects, allTasks, false)
        val (configured, expanded) = TaskGroupsLogic.toggleExpanded(
            key = groups.first().key,
            groups = groups,
            userConfigured = settings.tasksGroupsUserConfigured,
            storedExpandedKeys = emptySet()
        )
        settings = applyLocalGroupPrefs(settings) {
            it.copy(
                tasksGroupsUserConfigured = configured,
                tasksGroupExpandedKeys = TaskGroupsLogic.encodeExpandedKeys(expanded)
            )
        }
        val (configured2, expanded2) = TaskGroupsLogic.toggleExpanded(
            key = groups.first().key,
            groups = groups,
            userConfigured = settings.tasksGroupsUserConfigured,
            storedExpandedKeys = TaskGroupsLogic.decodeExpandedKeys(settings.tasksGroupExpandedKeys)
        )
        settings = applyLocalGroupPrefs(settings) {
            it.copy(
                tasksGroupsUserConfigured = configured2,
                tasksGroupExpandedKeys = TaskGroupsLogic.encodeExpandedKeys(expanded2)
            )
        }
        assertThat(snapshot().pending).isEqualTo(0)

        // Pin / unpin «Без проекта»
        settings = applyLocalGroupPrefs(settings) { it.copy(tasksNoProjectPinnedTop = true) }
        TaskGroupsLogic.buildGroups(projects, allTasks, noProjectPinnedTop = true)
        settings = applyLocalGroupPrefs(settings) { it.copy(tasksNoProjectPinnedTop = false) }
        TaskGroupsLogic.buildGroups(projects, allTasks, noProjectPinnedTop = false)
        assertThat(snapshot().pending).isEqualTo(0)

        // Повторное «открытие» приложения: только чтение
        repo.tasksOnce()
        repo.projectsOnce()
        repo.pendingOperations()
        assertThat(snapshot().pending).isEqualTo(0)

        // Сущности не изменились
        val afterUi = repo.tasksOnce()
        afterUi.forEach { t ->
            assertThat(t.updatedAt).isEqualTo(taskUpdatedAtBefore.getValue(t.syncUuid))
        }
        assertThat(afterUi.map { it.syncUuid }.toSet()).isEqualTo(taskUpdatedAtBefore.keys)

        // openTasks / progressPercent — чистые геттеры
        val stats = ProjectWithStats(projects.first(), totalTasks = 10, doneTasks = 3)
        assertThat(stats.openTasks).isEqualTo(7)
        assertThat(stats.progressPercent).isEqualTo(30)
        assertThat(snapshot().pending).isEqualTo(0)

        // Одно нажатие круга → ровно одна исходящая UPDATE одной задачи
        val target = afterUi.first { it.status == TaskStatus.OPEN }
        val beforeToggle = snapshot()
        repo.setTaskStatus(target.id, TaskStatusToggle.nextStatus(target.status))
        val afterOne = snapshot()
        assertThat(afterOne.pending - beforeToggle.pending).isEqualTo(1)
        assertThat(afterOne.pendingUniqueUuids - beforeToggle.pendingUniqueUuids).isAtMost(1)
        val pendingOps = repo.pendingOperations()
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.single().entityUuid).isEqualTo(target.syncUuid)
        assertThat(pendingOps.single().op).isEqualTo("update")
        val changed = repo.getTask(target.id)!!
        assertThat(changed.status).isEqualTo(TaskStatus.DONE)
        afterUi.filter { it.id != target.id }.forEach { other ->
            assertThat(repo.getTask(other.id)!!.updatedAt).isEqualTo(other.updatedAt)
            assertThat(repo.getTask(other.id)!!.status).isEqualTo(other.status)
        }

        // Двойное/тройное в пределах debounce: логика гейта не зовёт setTaskStatus повторно
        var pendingKeys = setOf<String>()
        var lastAccepted = mapOf<String, Long>()
        var clock = 10_000L
        var changeCalls = 0
        fun uiToggle(uuid: String, id: Long, status: TaskStatus) {
            if (!TaskStatusToggle.acceptGesture(uuid, pendingKeys, lastAccepted, clock)) return
            lastAccepted = TaskStatusToggle.recordAccepted(lastAccepted, uuid, clock)
            pendingKeys = pendingKeys + uuid
            changeCalls++
            runBlocking { repo.setTaskStatus(id, TaskStatusToggle.nextStatus(status)) }
            pendingKeys = pendingKeys - uuid
        }
        // Сначала ack единственную op, чтобы считать только debounce-сценарий
        repo.ackOperations(repo.pendingOperations().map { it.operationId })
        assertThat(snapshot().pending).isEqualTo(0)

        val reopen = repo.getTask(target.id)!!
        uiToggle(reopen.syncUuid, reopen.id, reopen.status) // DONE -> OPEN
        clock += 100
        uiToggle(reopen.syncUuid, reopen.id, TaskStatus.OPEN) // ignored (status arg stale OK — gate blocks)
        clock += 50
        uiToggle(reopen.syncUuid, reopen.id, TaskStatus.OPEN)
        assertThat(changeCalls).isEqualTo(1)
        assertThat(snapshot().pending).isEqualTo(1)

        // Группа «Без проекта» присутствует в модели без записи
        val withPin = TaskGroupsLogic.buildGroups(projects, repo.tasksOnce(), true)
        assertThat(withPin.any { it.key == TASK_GROUP_NO_PROJECT || it.isNoProject }).isTrue()
    }
}
