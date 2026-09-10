package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PcPrimarySnapshotInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: MarsDatabase
    private lateinit var repo: PlannerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = MarsDatabase.buildInMemory(context)
        repo = PlannerRepository(db) { "phone-device" }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapProject(
        uuid: String = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        name: String = "Дом",
        archived: Boolean = false
    ): SnapshotProject {
        val payload = VersionHash.projectPayload(
            syncUuid = uuid,
            name = name,
            description = "",
            archived = archived,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        return SnapshotProject(
            syncUuid = uuid,
            name = name,
            description = "",
            archived = archived,
            createdAtUtc = "2026-09-01T10:00:00+00:00",
            versionHash = VersionHash.versionHash(payload)
        )
    }

    private fun snapTask(
        uuid: String,
        projectUuid: String?,
        status: String = "open",
        title: String = "Задача"
    ): SnapshotTask {
        val payload = VersionHash.taskPayload(
            syncUuid = uuid,
            title = title,
            description = "",
            projectUuid = projectUuid,
            priority = "normal",
            dueAtUtc = null,
            status = status,
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        return SnapshotTask(
            syncUuid = uuid,
            title = title,
            description = "",
            projectUuid = projectUuid,
            priority = "normal",
            dueAtUtc = null,
            status = status,
            createdAtUtc = "2026-09-01T11:00:00+00:00",
            versionHash = VersionHash.versionHash(payload)
        )
    }

    private fun snapshot(
        projects: List<SnapshotProject>,
        tasks: List<SnapshotTask>
    ) = FullSnapshot(
        snapshotKind = SNAPSHOT_KIND_PC_PRIMARY,
        protocolVersion = 1,
        createdAtUtc = "2026-09-08T12:00:00+00:00",
        projects = projects,
        tasks = tasks
    )

    @Test
    fun applyPreservesPcUuidsAndHashes() = runBlocking {
        val pUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val tOpen = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val tDone = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val tOrphan = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        val pArch = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"
        val tArch = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        val snap = snapshot(
            listOf(snapProject(pUuid), snapProject(pArch, "Архив", true)),
            listOf(
                snapTask(tOpen, pUuid, "open", "Открытая"),
                snapTask(tDone, pUuid, "done", "Готовая"),
                snapTask(tOrphan, null, "open", "Сирота"),
                snapTask(tArch, pArch, "open", "В архиве")
            )
        )
        FullSnapshotValidator.validate(snap, snap.sha256())
        repo.applyPcPrimarySnapshot(snap, "snap-1")

        val projects = repo.projectsOnce()
        val tasks = repo.tasksOnce()
        assertThat(projects.map { it.syncUuid }).containsExactly(pUuid, pArch)
        assertThat(tasks.map { it.syncUuid }).containsExactly(tOpen, tDone, tOrphan, tArch)
        assertThat(tasks.first { it.syncUuid == tDone }.status).isEqualTo(TaskStatus.DONE)
        assertThat(tasks.first { it.syncUuid == tOrphan }.projectSyncUuid).isNull()
        assertThat(projects.first { it.syncUuid == pArch }.archived).isTrue()
        projects.forEach { p ->
            val expected = snap.projects.first { it.syncUuid == p.syncUuid }.versionHash
            assertThat(repo.hashProjectEntity(db.projectDao().getByUuid(p.syncUuid)!!)).isEqualTo(expected)
        }
        tasks.forEach { t ->
            val expected = snap.tasks.first { it.syncUuid == t.syncUuid }.versionHash
            assertThat(repo.taskHashByUuid(t.syncUuid)).isEqualTo(expected)
        }
    }

    @Test
    fun reapplyDoesNotDuplicate() = runBlocking {
        val snap = snapshot(
            listOf(snapProject()),
            listOf(snapTask("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        )
        repo.applyPcPrimarySnapshot(snap, "snap-a")
        repo.applyPcPrimarySnapshot(snap, "snap-b")
        assertThat(repo.countUserProjects()).isEqualTo(1)
        assertThat(repo.countUserTasks()).isEqualTo(1)
    }

    @Test
    fun applyStoresPendingAckRecoverableFromDb() = runBlocking {
        val snap = snapshot(
            listOf(snapProject()),
            listOf(snapTask("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        )
        repo.applyPcPrimarySnapshot(snap, "snap-pending-1")
        assertThat(repo.findPendingSnapshotAckId()).isEqualTo("snap-pending-1")
        // Повторный ACK без повторного применения данных: только смена статуса.
        val projectsBefore = repo.countUserProjects()
        val tasksBefore = repo.countUserTasks()
        repo.markSnapshotAckCompleted("snap-pending-1")
        assertThat(repo.findPendingSnapshotAckId()).isNull()
        assertThat(repo.countUserProjects()).isEqualTo(projectsBefore)
        assertThat(repo.countUserTasks()).isEqualTo(tasksBefore)
    }

    @Test
    fun pendingOpsClearedAfterReplace() = runBlocking {
        repo.saveProject(
            ProjectItem(
                syncUuid = UUID.randomUUID().toString(),
                name = "Локальный",
                description = ""
            )
        )
        assertThat(repo.pendingOperations()).isNotEmpty()
        val snap = snapshot(
            listOf(snapProject()),
            listOf(snapTask("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        )
        repo.applyPcPrimarySnapshot(snap, "snap-clear")
        assertThat(repo.pendingOperations()).isEmpty()
        assertThat(repo.countUserProjects()).isEqualTo(1)
        assertThat(repo.projectsOnce().single().syncUuid)
            .isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    }

    @Test
    fun hashMismatchInsideTxnRollsBack() = runBlocking {
        repo.saveProject(
            ProjectItem(
                syncUuid = UUID.randomUUID().toString(),
                name = "Сохранить",
                description = ""
            )
        )
        val before = repo.countUserProjects()
        val p = snapProject()
        val bad = p.copy(versionHash = "0".repeat(64))
        val snap = snapshot(listOf(bad), emptyList())
        assertThrows(SyncEngineException::class.java) {
            runBlocking { repo.applyPcPrimarySnapshot(snap, "snap-bad") }
        }
        assertThat(repo.countUserProjects()).isEqualTo(before)
        assertThat(repo.projectsOnce().single().name).isEqualTo("Сохранить")
    }

    @Test
    fun backupAndRestoreRoundtrip() = runBlocking {
        val localUuid = UUID.randomUUID().toString()
        repo.saveProject(
            ProjectItem(
                syncUuid = localUuid,
                name = "Телефонный",
                description = "keep"
            )
        )
        val backup = PhoneSnapshotBackup(context)
        val file = backup.createBackup(repo)
        assertThat(file.exists()).isTrue()

        val snap = snapshot(
            listOf(snapProject()),
            listOf(snapTask("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        )
        repo.applyPcPrimarySnapshot(snap, "snap-x")
        assertThat(repo.projectsOnce().single().name).isEqualTo("Дом")

        backup.restoreLatest(repo)
        assertThat(repo.projectsOnce().single().syncUuid).isEqualTo(localUuid)
        assertThat(repo.projectsOnce().single().name).isEqualTo("Телефонный")
        assertThat(file.exists()).isTrue()
    }
}
