package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.CertificateFingerprintException
import com.mars.planner.sync.MemorySecureTokenStore
import com.mars.planner.sync.RubezhSyncClient
import com.mars.planner.sync.SyncCallResult
import com.mars.planner.sync.SyncEndpoint
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.SyncPackage
import com.mars.planner.sync.VersionHash
import com.mars.planner.sync.classifyTransportError
import com.mars.planner.sync.defaultClientFor
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException

class RubezhSyncClientTest {
    private val cert = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun pairRequestAcceptsPendingConfirm() {
        withServer(MockResponse().setBody("""{"ok":true,"request_id":"req-1","status":"pending_confirm"}""")) { server ->
            val result = runBlocking { client(server, paired = false).pairRequest(endpoint(server), "one-time", "Pixel 8") }
            assertThat((result as SyncCallResult.Ok).value.status).isEqualTo("pending_confirm")
        }
    }

    @Test
    fun pairStatusAcceptsApprovedAndDeniedResponses() {
        withServer(
            MockResponse().setBody("""{"ok":true,"status":"approved","device_id":"dev-1","device_token":"secret"}"""),
            MockResponse().setResponseCode(403).setBody("""{"ok":false,"error":"pairing_denied"}""")
        ) { server ->
            val client = client(server, paired = false)
            val approved = runBlocking { client.pairStatus(endpoint(server), "req-1") }
            assertThat((approved as SyncCallResult.Ok).value.isApproved).isTrue()

            val denied = runBlocking { client.pairStatus(endpoint(server), "req-1") }.failureOrNull()!!
            assertThat(denied.code).isEqualTo(SyncErrorCodes.PAIRING_DENIED)
            assertThat(denied.messageRu).doesNotContain("secret")
        }
    }

    @Test
    fun pairRequestMapsExpiredAndUsedTokens() {
        withServer(
            MockResponse().setResponseCode(401).setBody("""{"ok":false,"error":"pairing_token_expired"}"""),
            MockResponse().setResponseCode(409).setBody("""{"ok":false,"error":"pairing_token_used"}""")
        ) { server ->
            val client = client(server, paired = false)
            assertThat(runBlocking { client.pairRequest(endpoint(server), "expired", "Pixel") }.failureOrNull()!!.code)
                .isEqualTo(SyncErrorCodes.PAIRING_TOKEN_EXPIRED)
            assertThat(runBlocking { client.pairRequest(endpoint(server), "used", "Pixel") }.failureOrNull()!!.code)
                .isEqualTo(SyncErrorCodes.PAIRING_TOKEN_USED)
        }
    }

    @Test
    fun authorizedEndpointsMapDocumentedErrors() {
        val codes = listOf(
            SyncErrorCodes.UNAUTHORIZED,
            SyncErrorCodes.DEVICE_REVOKED,
            SyncErrorCodes.SENDER_MISMATCH,
            SyncErrorCodes.PACKAGE_REJECTED,
            SyncErrorCodes.UNRESOLVED_DEPENDENCY,
            SyncErrorCodes.UNSUPPORTED_PROTOCOL,
            SyncErrorCodes.REQUEST_TOO_LARGE
        )
        val responses = codes.map { code ->
            MockResponse().setResponseCode(
                when (code) {
                    SyncErrorCodes.UNAUTHORIZED -> 401
                    SyncErrorCodes.DEVICE_REVOKED, SyncErrorCodes.SENDER_MISMATCH -> 403
                    SyncErrorCodes.PACKAGE_REJECTED, SyncErrorCodes.UNRESOLVED_DEPENDENCY -> 409
                    SyncErrorCodes.UNSUPPORTED_PROTOCOL -> 426
                    else -> 413
                }
            ).setBody("""{"ok":false,"error":"$code"}""")
        }.toTypedArray()
        withServer(*responses) { server ->
            val client = client(server)
            val pkg = samplePackage()
            assertThat(runBlocking { client.health() }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.UNAUTHORIZED)
            assertThat(runBlocking { client.pullPackages() }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.DEVICE_REVOKED)
            assertThat(runBlocking { client.pushPackage(pkg) }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.SENDER_MISMATCH)
            assertThat(runBlocking { client.pushPackage(pkg) }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.PACKAGE_REJECTED)
            assertThat(runBlocking { client.pushPackage(pkg) }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.UNRESOLVED_DEPENDENCY)
            assertThat(runBlocking { client.pullPackages() }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.UNSUPPORTED_PROTOCOL)
            assertThat(runBlocking { client.ackPackage(pkg.packageId) }.failureOrNull()!!.code).isEqualTo(SyncErrorCodes.REQUEST_TOO_LARGE)
        }
    }

    @Test
    fun pullPackagesHandlesEmptyAndRepeatedPackages() {
        val pkg = samplePackage().toJson()
        withServer(
            MockResponse().setBody("""{"ok":true,"packages":[]}"""),
            MockResponse().setBody("""{"ok":true,"packages":[$pkg,$pkg]}""")
        ) { server ->
            val client = client(server)
            assertThat((runBlocking { client.pullPackages() } as SyncCallResult.Ok).value).isEmpty()
            val repeated = (runBlocking { client.pullPackages() } as SyncCallResult.Ok).value
            assertThat(repeated).hasSize(2)
            assertThat(repeated[0].packageId).isEqualTo(repeated[1].packageId)
            assertThat(repeated[0].operations[0].operationId).isEqualTo(repeated[1].operations[0].operationId)
        }
    }

    @Test
    fun tlsFingerprintMismatchIsReportedWithoutLeakingToken() {
        val store = MemorySecureTokenStore("install-1").apply { savePairing("dev-1", "very-secret-token") }
        val failingFactory: (SyncEndpoint) -> OkHttpClient = {
            OkHttpClient.Builder().addInterceptor(Interceptor {
                val e = SSLException("tls")
                e.initCause(CertificateFingerprintException("fingerprint mismatch"))
                throw e
            }).build()
        }
        val client = RubezhSyncClient(store, { SyncEndpoint("127.0.0.1", 8765, cert, scheme = "http") }, failingFactory)
        val failure = runBlocking { client.health() }.failureOrNull()!!
        assertThat(failure.code).isEqualTo(SyncErrorCodes.TLS_PIN_MISMATCH)
        assertThat(failure.messageRu).doesNotContain("very-secret-token")
        assertThat(failure.messageRu).contains("сопряжение")
    }

    @Test
    fun healthUnauthorizedIsNotWifiMessage() {
        withServer(
            MockResponse().setResponseCode(401).setBody("""{"ok":false,"error":"unauthorized"}""")
        ) { server ->
            val failure = runBlocking { client(server).health() }.failureOrNull()!!
            assertThat(failure.code).isEqualTo(SyncErrorCodes.UNAUTHORIZED)
            assertThat(failure.messageRu).doesNotContain("Wi-Fi")
            assertThat(failure.messageRu).contains("токен")
        }
    }

    @Test
    fun healthDeviceRevokedIsNotWifiMessage() {
        withServer(
            MockResponse().setResponseCode(403).setBody("""{"ok":false,"error":"device_revoked"}""")
        ) { server ->
            val failure = runBlocking { client(server).health() }.failureOrNull()!!
            assertThat(failure.code).isEqualTo(SyncErrorCodes.DEVICE_REVOKED)
            assertThat(failure.messageRu).doesNotContain("Wi-Fi")
        }
    }

    @Test
    fun classifyTransportErrorsAreDistinct() {
        assertThat(
            classifyTransportError(
                SSLException("x").also { it.initCause(CertificateFingerprintException("pin")) }
            )
        ).isEqualTo(SyncErrorCodes.TLS_PIN_MISMATCH)
        assertThat(classifyTransportError(SSLException("handshake"))).isEqualTo(SyncErrorCodes.TLS_ERROR)
        assertThat(classifyTransportError(ConnectException("refused")))
            .isEqualTo(SyncErrorCodes.NETWORK_UNAVAILABLE)
        assertThat(classifyTransportError(SocketTimeoutException("slow")))
            .isEqualTo(SyncErrorCodes.TIMEOUT)
        assertThat(classifyTransportError(UnknownHostException("x")))
            .isEqualTo(SyncErrorCodes.NETWORK_UNAVAILABLE)
        assertThat(SyncErrorCodes.messageRu(SyncErrorCodes.NETWORK_UNAVAILABLE))
            .contains("Wi-Fi")
        assertThat(SyncErrorCodes.messageRu(SyncErrorCodes.UNAUTHORIZED)).doesNotContain("Wi-Fi")
        assertThat(SyncErrorCodes.messageRu(SyncErrorCodes.TLS_PIN_MISMATCH)).doesNotContain("Wi-Fi")
    }

    @Test
    fun defaultClientRetriesConnectionFailures() {
        val client = defaultClientFor(SyncEndpoint("127.0.0.1", 8765, cert))
        assertThat(client.retryOnConnectionFailure).isTrue()
    }

    private fun samplePackage(): SyncPackage {
        val payload = VersionHash.taskPayload(
            syncUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            title = "Купить краску",
            description = "",
            projectUuid = null,
            priority = "normal",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        return SyncPackage(
            packageId = UUID.randomUUID().toString(),
            senderDeviceId = "dev-1",
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            operations = listOf(
                com.mars.planner.sync.SyncOperation(
                    operationId = UUID.randomUUID().toString(),
                    entityType = "task",
                    entityUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    op = "create",
                    baseHash = null,
                    newHash = VersionHash.versionHash(payload),
                    payload = payload,
                    deviceId = "dev-1"
                )
            )
        )
    }

    private fun client(server: MockWebServer, paired: Boolean = true): RubezhSyncClient {
        val store = MemorySecureTokenStore("install-1")
        if (paired) store.savePairing("dev-1", "token-1")
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
