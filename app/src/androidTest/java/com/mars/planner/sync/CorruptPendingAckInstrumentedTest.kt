package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.db.SyncAppliedPackageEntity
import com.mars.planner.data.prefs.SettingsRepository
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
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CorruptPendingAckInstrumentedTest {

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
        tokenStore = MemorySecureTokenStore("install-corrupt-ack")
        tokenStore.savePairing("phone-device", "token-1")
        server = MockWebServer().also { it.start() }
        val endpoint = SyncEndpoint(
            host = server.hostName,
            port = server.port,
            certSha256 = cert,
            scheme = "http"
        )
        sync = SyncCoordinator(
            context = context,
            planner = repo,
            packages = PackageEngine(repo = repo, deviceIdProvider = { "phone-device" }),
            client = RubezhSyncClient(tokenStore = tokenStore, endpointProvider = { endpoint }),
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

    private suspend fun insertPending(packageId: String) {
        db.syncDao().insertAppliedPackage(
            SyncAppliedPackageEntity(
                packageId = packageId,
                senderDeviceId = PlannerRepository.PC_PRIMARY_SNAPSHOT_SENDER,
                status = PlannerRepository.STATUS_SNAPSHOT_PENDING_ACK
            )
        )
    }

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
                    .put(
                        "byte_size",
                        VersionHash.canonicalJson(snap.contentForHash()).toByteArray(Charsets.UTF_8).size
                    )
                    .put("sha256", snap.sha256())
                    .put("chunk_count", 1)
                    .toString()
            )
        )
    }

    private fun enqueueChunkAndAck(snapshotId: String, snap: FullSnapshot) {
        val p = snap.projects[0]
        val t = snap.tasks[0]
        val payload = JSONObject()
            .put("snapshot_kind", snap.snapshotKind)
            .put("protocol_version", snap.protocolVersion)
            .put("created_at", snap.createdAtUtc)
            .put(
                "projects",
                JSONArray().put(
                    JSONObject()
                        .put("sync_uuid", p.syncUuid)
                        .put("name", p.name)
                        .put("description", p.description)
                        .put("archived", p.archived)
                        .put("created_at", p.createdAtUtc)
                        .put("version_hash", p.versionHash)
                )
            )
            .put(
                "tasks",
                JSONArray().put(
                    JSONObject()
                        .put("sync_uuid", t.syncUuid)
                        .put("title", t.title)
                        .put("description", t.description)
                        .put("project_uuid", t.projectUuid ?: JSONObject.NULL)
                        .put("priority", t.priority)
                        .put("due_at", JSONObject.NULL)
                        .put("status", t.status)
                        .put("created_at", t.createdAtUtc)
                        .put("version_hash", t.versionHash)
                )
            )
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
    fun mismatchRoomAndDataStoreBlocksAckAndDelta() = runBlocking {
        repo.applyPcPrimarySnapshot(fullSnapshot(), "snap-room-a")
        settings.update {
            it.copy(
                pendingSnapshotAckId = "snap-store-b",
                requiresPcPrimarySnapshot = false,
                lastSyncError = SyncErrorCodes.SNAPSHOT_ACK_PENDING
            )
        }

        val beforeRequests = server.requestCount
        val retry = sync.retryPendingSnapshotAck()
        assertThat(retry.ok).isFalse()
        assertThat(retry.message).contains("полную копию")
        assertThat(server.requestCount).isEqualTo(beforeRequests)
        assertThat(server.takeRequest(50, TimeUnit.MILLISECONDS)).isNull()

        val st = settings.settings.first()
        assertThat(st.requiresPcPrimarySnapshot).isTrue()
        assertThat(st.pendingSnapshotAckId).isEmpty()
        assertThat(sync.pushChanges().ok).isFalse()
        assertThat(sync.pullChanges().ok).isFalse()

        val state = sync.resolvePendingSnapshotAckState()
        assertThat(state).isInstanceOf(SyncCoordinator.PendingSnapshotAckState.RequiresFullSnapshot::class.java)
    }

    @Test
    fun multiplePendingAcksEnterSafeModeWithoutCrash() = runBlocking {
        insertPending("snap-one")
        insertPending("snap-two")
        settings.update {
            it.copy(pendingSnapshotAckId = "snap-one", requiresPcPrimarySnapshot = false)
        }

        val before = server.requestCount
        // resolve на экране не должен бросать
        val resolved = sync.resolvePendingSnapshotAckState()
        assertThat(resolved).isInstanceOf(SyncCoordinator.PendingSnapshotAckState.Corrupt::class.java)

        val retry = sync.retryPendingSnapshotAck()
        assertThat(retry.ok).isFalse()
        assertThat(server.requestCount).isEqualTo(before)

        val st = settings.settings.first()
        assertThat(st.requiresPcPrimarySnapshot).isTrue()
        assertThat(st.pendingSnapshotAckId).isEmpty()
        assertThat(sync.pushChanges().ok).isFalse()
    }

    @Test
    fun roomPendingHydratesEmptyDataStoreAndAckSucceeds() = runBlocking {
        val snapId = "snap-hydrate-a"
        repo.applyPcPrimarySnapshot(fullSnapshot(), snapId)
        settings.update {
            it.copy(pendingSnapshotAckId = "", requiresPcPrimarySnapshot = false, lastSyncError = "")
        }

        val state = sync.resolvePendingSnapshotAckState()
        assertThat(state).isEqualTo(SyncCoordinator.PendingSnapshotAckState.Ready(snapId))
        assertThat(settings.settings.first().pendingSnapshotAckId).isEqualTo(snapId)

        server.enqueue(
            MockResponse().setBody(
                JSONObject()
                    .put("ok", true)
                    .put("snapshot_id", snapId)
                    .put("packages_acked", 0)
                    .put("already_acked", true)
                    .toString()
            )
        )
        val retry = sync.retryPendingSnapshotAck()
        assertThat(retry.ok).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
        val req = server.takeRequest()
        assertThat(req.path).contains("/v1/snapshot/ack")

        val st = settings.settings.first()
        assertThat(st.pendingSnapshotAckId).isEmpty()
        assertThat(st.requiresPcPrimarySnapshot).isFalse()
        assertThat(repo.findPendingSnapshotAckId()).isNull()
    }

    @Test
    fun corruptStateClearedByNewSnapshotAndAckUnlocksDelta() = runBlocking {
        repo.applyPcPrimarySnapshot(fullSnapshot(), "snap-room-a")
        settings.update {
            it.copy(pendingSnapshotAckId = "snap-store-b", requiresPcPrimarySnapshot = false)
        }
        assertThat(sync.retryPendingSnapshotAck().ok).isFalse()
        assertThat(settings.settings.first().requiresPcPrimarySnapshot).isTrue()

        val snap2Id = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        val snap2 = fullSnapshot(projects = listOf(snapProject(name = "ПК-2")))
        enqueueHealth()
        enqueueInfo(snap2Id, snap2)
        val preview = sync.preparePcPrimarySnapshot()
        assertThat(preview).isInstanceOf(SyncCallResult.Ok::class.java)
        enqueueChunkAndAck(snap2Id, snap2)
        val applied = sync.applyPcPrimarySnapshot((preview as SyncCallResult.Ok).value)
        assertThat(applied.ok).isTrue()

        val st = settings.settings.first()
        assertThat(st.requiresPcPrimarySnapshot).isFalse()
        assertThat(st.pendingSnapshotAckId).isEmpty()

        server.enqueue(MockResponse().setBody("""{"ok":true,"packages":[]}"""))
        assertThat(sync.pullChanges().ok).isTrue()
    }
}
