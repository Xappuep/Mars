package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.FullSnapshotCodec
import com.mars.planner.sync.MemorySecureTokenStore
import com.mars.planner.sync.RubezhSyncClient
import com.mars.planner.sync.SyncCallResult
import com.mars.planner.sync.SyncEndpoint
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.runWithBusyFlag
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Test

class Corr7SnapshotAckTest {

    private val cert = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val snapId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"

    @Test
    fun parseAckRequiresOkTrueAndMatchingId() {
        val ok = FullSnapshotCodec.parseAck(
            JSONObject()
                .put("ok", true)
                .put("snapshot_id", snapId)
                .put("packages_acked", 2)
                .put("already_acked", false),
            snapId
        )
        assertThat(ok.packagesAcked).isEqualTo(2)
        assertThat(ok.alreadyAcked).isFalse()
    }

    @Test
    fun parseAckEmptyObjectIsCorrupt() {
        val ex = runCatching { FullSnapshotCodec.parseAck(JSONObject("{}"), snapId) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(SyncEngineException::class.java)
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun parseAckOkTrueWithoutSnapshotIdIsCorrupt() {
        val ex = runCatching {
            FullSnapshotCodec.parseAck(JSONObject().put("ok", true).put("packages_acked", 0), snapId)
        }.exceptionOrNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun parseAckWrongOkTypeIsCorrupt() {
        val ex = runCatching {
            FullSnapshotCodec.parseAck(
                JSONObject().put("ok", "true").put("snapshot_id", snapId).put("packages_acked", 0),
                snapId
            )
        }.exceptionOrNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun parseAckWrongPackagesAckedTypeIsCorrupt() {
        val ex = runCatching {
            FullSnapshotCodec.parseAck(
                JSONObject().put("ok", true).put("snapshot_id", snapId).put("packages_acked", "0"),
                snapId
            )
        }.exceptionOrNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun parseAckMismatchedSnapshotIdIsCorrupt() {
        val ex = runCatching {
            FullSnapshotCodec.parseAck(
                JSONObject().put("ok", true).put("snapshot_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
                    .put("packages_acked", 0),
                snapId
            )
        }.exceptionOrNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun parseAckAlreadyAckedMustBeBoolean() {
        val ex = runCatching {
            FullSnapshotCodec.parseAck(
                JSONObject().put("ok", true).put("snapshot_id", snapId)
                    .put("packages_acked", 0).put("already_acked", "yes"),
                snapId
            )
        }.exceptionOrNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun httpSnapshotAckEmptyBodyIsCorrupt() {
        withServer(MockResponse().setBody("{}")) { server ->
            val result = runBlocking { client(server).snapshotAck(snapId, endpoint(server)) }
            assertThat((result as SyncCallResult.Failure).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
        }
    }

    @Test
    fun httpSnapshotAckOkTrueWithoutIdIsCorrupt() {
        withServer(MockResponse().setBody("""{"ok":true,"packages_acked":0}""")) { server ->
            val result = runBlocking { client(server).snapshotAck(snapId, endpoint(server)) }
            assertThat((result as SyncCallResult.Failure).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
        }
    }

    @Test
    fun httpSnapshotAckWrongIdIsCorrupt() {
        withServer(
            MockResponse().setBody(
                """{"ok":true,"snapshot_id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","packages_acked":0}"""
            )
        ) { server ->
            val result = runBlocking { client(server).snapshotAck(snapId, endpoint(server)) }
            assertThat((result as SyncCallResult.Failure).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
        }
    }

    @Test
    fun httpSnapshotAckAlreadyAckedSucceeds() {
        withServer(
            MockResponse().setBody(
                """{"ok":true,"snapshot_id":"$snapId","packages_acked":0,"already_acked":true}"""
            )
        ) { server ->
            val result = runBlocking { client(server).snapshotAck(snapId, endpoint(server)) }
            val ok = result as SyncCallResult.Ok
            assertThat(ok.value.alreadyAcked).isTrue()
            assertThat(ok.value.snapshotId).isEqualTo(snapId)
        }
    }

    @Test
    fun httpSnapshotAckCorruptJsonIsCorrupt() {
        withServer(MockResponse().setBody("{not-json")) { server ->
            val result = runBlocking { client(server).snapshotAck(snapId, endpoint(server)) }
            assertThat((result as SyncCallResult.Failure).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
        }
    }

    @Test
    fun busyFlagStillClearedOnException() = runBlocking {
        var busy = false
        runCatching {
            runWithBusyFlag({ busy = it }) { error("boom") }
        }
        assertThat(busy).isFalse()
    }

    private fun client(server: MockWebServer): RubezhSyncClient {
        val store = MemorySecureTokenStore("install-1")
        store.savePairing("dev-1", "token-1")
        return RubezhSyncClient(store, { endpoint(server) })
    }

    private fun endpoint(server: MockWebServer): SyncEndpoint =
        SyncEndpoint(host = server.hostName, port = server.port, certSha256 = cert, scheme = "http")

    private fun withServer(vararg responses: MockResponse, block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            responses.forEach(server::enqueue)
            server.start()
            block(server)
        }
    }
}
