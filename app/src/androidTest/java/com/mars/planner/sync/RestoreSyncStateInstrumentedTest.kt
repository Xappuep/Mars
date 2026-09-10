package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.domain.model.ProjectItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RestoreSyncStateInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: MarsDatabase
    private lateinit var repo: PlannerRepository
    private lateinit var settings: SettingsRepository
    private lateinit var tokenStore: MemorySecureTokenStore
    private lateinit var server: MockWebServer
    private lateinit var sync: SyncCoordinator
    private val cert = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = MarsDatabase.buildInMemory(context)
        repo = PlannerRepository(db) { "phone-device" }
        settings = SettingsRepository(context)
        tokenStore = MemorySecureTokenStore("install-restore-test")
        tokenStore.savePairing("phone-device", "token-1")
        server = MockWebServer().also { it.start() }
        val endpoint = SyncEndpoint(
            host = server.hostName,
            port = server.port,
            certSha256 = cert,
            scheme = "http"
        )
        val client = RubezhSyncClient(
            tokenStore = tokenStore,
            endpointProvider = { endpoint }
        )
        val packages = PackageEngine(
            repo = repo,
            deviceIdProvider = { "phone-device" }
        )
        sync = SyncCoordinator(
            context = context,
            planner = repo,
            packages = packages,
            client = client,
            tokenStore = tokenStore,
            settingsRepo = settings,
            deviceNameProvider = { "TestPhone" },
            onEndpointChanged = {}
        )
        runBlocking {
            settings.update {
                it.copy(
                    syncPaired = true,
                    pairedHost = server.hostName,
                    pairedPort = server.port,
                    certSha256 = cert,
                    pendingSnapshotAckId = "",
                    requiresPcPrimarySnapshot = false,
                    lastSyncError = ""
                )
            }
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        runBlocking {
            settings.update {
                it.copy(
                    pendingSnapshotAckId = "",
                    requiresPcPrimarySnapshot = false,
                    lastSyncError = ""
                )
            }
        }
    }

    private fun snapProject(
        uuid: String = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        name: String = "Дом"
    ): SnapshotProject {
        val payload = VersionHash.projectPayload(
            syncUuid = uuid,
            name = name,
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        return SnapshotProject(
            syncUuid = uuid,
            name = name,
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00",
            versionHash = VersionHash.versionHash(payload)
        )
    }

    private fun snapTask(
        uuid: String = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        projectUuid: String? = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    ): SnapshotTask {
        val payload = VersionHash.taskPayload(
            syncUuid = uuid,
            title = "Задача",
            description = "",
            projectUuid = projectUuid,
            priority = "normal",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        return SnapshotTask(
            syncUuid = uuid,
            title = "Задача",
            description = "",
            projectUuid = projectUuid,
            priority = "normal",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00",
            versionHash = VersionHash.versionHash(payload)
        )
    }

    private fun fullSnapshot(
        projects: List<SnapshotProject> = listOf(snapProject()),
        tasks: List<SnapshotTask> = listOf(snapTask())
    ) = FullSnapshot(
        snapshotKind = SNAPSHOT_KIND_PC_PRIMARY,
        protocolVersion = 1,
        createdAtUtc = "2026-09-08T12:00:00+00:00",
        projects = projects,
        tasks = tasks
    )

    private fun projectJson(p: SnapshotProject) = JSONObject()
        .put("sync_uuid", p.syncUuid)
        .put("name", p.name)
        .put("description", p.description)
        .put("archived", p.archived)
        .put("created_at", p.createdAtUtc)
        .put("version_hash", p.versionHash)

    private fun taskJson(t: SnapshotTask) = JSONObject()
        .put("sync_uuid", t.syncUuid)
        .put("title", t.title)
        .put("description", t.description)
        .put("project_uuid", t.projectUuid ?: JSONObject.NULL)
        .put("priority", t.priority)
        .put("due_at", JSONObject.NULL)
        .put("status", t.status)
        .put("created_at", t.createdAtUtc)
        .put("version_hash", t.versionHash)

    private fun enqueueHealth() {
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("ok", true)
                    .put("protocol_version", 1)
                    .put("features", JSONArray().put(FEATURE_FULL_SNAPSHOT))
                    .toString()
            )
        )
    }

    private fun enqueueInfo(snapshotId: String, snap: FullSnapshot) {
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("ok", true)
                    .put("snapshot_id", snapshotId)
                    .put("created_at", snap.createdAtUtc)
                    .put("project_count", snap.projects.size)
                    .put("task_count", snap.tasks.size)
                    .put("byte_size", VersionHash.canonicalJson(snap.contentForHash()).toByteArray(Charsets.UTF_8).size)
                    .put("sha256", snap.sha256())
                    .put("chunk_count", 1)
                    .toString()
            )
        )
    }

    private fun enqueueChunkAndAck(snapshotId: String, snap: FullSnapshot) {
        val payload = JSONObject()
            .put("snapshot_kind", snap.snapshotKind)
            .put("protocol_version", snap.protocolVersion)
            .put("created_at", snap.createdAtUtc)
            .put("projects", JSONArray().put(projectJson(snap.projects[0])))
            .put("tasks", JSONArray().put(taskJson(snap.tasks[0])))
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("ok", true)
                    .put("protocol_version", 1)
                    .put("snapshot_id", snapshotId)
                    .put("chunk_index", 0)
                    .put("chunk_count", 1)
                    .put("sha256", snap.sha256())
                    .put("payload", payload)
                    .toString()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("ok", true)
                    .put("snapshot_id", snapshotId)
                    .put("packages_acked", 1)
                    .toString()
            )
        )
    }

    @Test
    fun restoreAfterPendingAckBlocksDeltaUntilNewSnapshotAcked() = runBlocking {
        val localUuid = UUID.randomUUID().toString()
        repo.saveProject(ProjectItem(syncUuid = localUuid, name = "Телефонный", description = ""))
        PhoneSnapshotBackup(context).createBackup(repo)

        repo.applyPcPrimarySnapshot(fullSnapshot(), "snap-pending-old")
        settings.update {
            it.copy(
                pendingSnapshotAckId = "snap-pending-old",
                lastSyncError = SyncErrorCodes.SNAPSHOT_ACK_PENDING
            )
        }
        assertThat(repo.findPendingSnapshotAckId()).isEqualTo("snap-pending-old")

        val restored = sync.restorePhoneBackup()
        assertThat(restored.ok).isTrue()
        assertThat(restored.message).contains("полную копию с ПК")

        val after = settings.settings.first()
        assertThat(after.pendingSnapshotAckId).isEmpty()
        assertThat(after.requiresPcPrimarySnapshot).isTrue()
        assertThat(sync.resolvePendingSnapshotAckState())
            .isEqualTo(SyncCoordinator.PendingSnapshotAckState.RequiresFullSnapshot)
        assertThat(sync.hydratePendingSnapshotAck()).isNull()
        assertThat(repo.findPendingSnapshotAckId()).isNull()
        assertThat(repo.projectsOnce().single().syncUuid).isEqualTo(localUuid)

        assertThat(sync.pushChanges().ok).isFalse()
        assertThat(sync.pullChanges().ok).isFalse()
        assertThat(sync.retryPendingSnapshotAck().ok).isFalse()

        val snap2Id = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val snap2 = fullSnapshot(projects = listOf(snapProject(name = "ПК")))
        enqueueHealth()
        enqueueInfo(snap2Id, snap2)
        val preview = sync.preparePcPrimarySnapshot()
        assertThat(preview).isInstanceOf(SyncCallResult.Ok::class.java)

        enqueueChunkAndAck(snap2Id, snap2)
        val applied = sync.applyPcPrimarySnapshot((preview as SyncCallResult.Ok).value)
        assertThat(applied.ok).isTrue()

        val cleared = settings.settings.first()
        assertThat(cleared.requiresPcPrimarySnapshot).isFalse()
        assertThat(cleared.pendingSnapshotAckId).isEmpty()
        assertThat(repo.findPendingSnapshotAckId()).isNull()

        // Дельта снова разрешена (нет новых пакетов на mock — пустой pull).
        server.enqueue(MockResponse().setBody("""{"ok":true,"packages":[]}"""))
        val pull = sync.pullChanges()
        assertThat(pull.ok).isTrue()
    }

    @Test
    fun restoreAfterSuccessfulAckStillRequiresNewSnapshot() = runBlocking {
        val localUuid = UUID.randomUUID().toString()
        repo.saveProject(ProjectItem(syncUuid = localUuid, name = "Старый", description = ""))
        PhoneSnapshotBackup(context).createBackup(repo)

        repo.applyPcPrimarySnapshot(fullSnapshot(), "snap-acked")
        repo.markSnapshotAckCompleted("snap-acked")
        settings.update {
            it.copy(pendingSnapshotAckId = "", requiresPcPrimarySnapshot = false, lastSyncError = "")
        }

        val restored = sync.restorePhoneBackup()
        assertThat(restored.ok).isTrue()
        val st = settings.settings.first()
        assertThat(st.requiresPcPrimarySnapshot).isTrue()
        assertThat(st.pendingSnapshotAckId).isEmpty()
        assertThat(sync.pushChanges().ok).isFalse()
        assertThat(sync.pullChanges().ok).isFalse()
        assertThat(repo.projectsOnce().single().syncUuid).isEqualTo(localUuid)
    }

    @Test
    fun failedRestoreLeavesSyncMarkersUnchanged() = runBlocking {
        // Нет файлов бэкапа — restore падает до изменения базы/маркеров.
        context.filesDir.resolve("backups").listFiles()?.forEach { it.delete() }
        settings.update {
            it.copy(
                pendingSnapshotAckId = "keep-me",
                requiresPcPrimarySnapshot = false,
                lastSyncError = SyncErrorCodes.SNAPSHOT_ACK_PENDING
            )
        }
        repo.applyPcPrimarySnapshot(fullSnapshot(), "keep-me")
        val beforeProjects = repo.countUserProjects()

        val failed = sync.restorePhoneBackup()
        assertThat(failed.ok).isFalse()
        val st = settings.settings.first()
        assertThat(st.pendingSnapshotAckId).isEqualTo("keep-me")
        assertThat(st.requiresPcPrimarySnapshot).isFalse()
        assertThat(repo.findPendingSnapshotAckId()).isEqualTo("keep-me")
        assertThat(repo.countUserProjects()).isEqualTo(beforeProjects)
    }
}
