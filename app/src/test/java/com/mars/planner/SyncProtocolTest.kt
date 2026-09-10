package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.CausalOrder
import com.mars.planner.sync.ENTITY_PROJECT
import com.mars.planner.sync.ENTITY_TASK
import com.mars.planner.sync.MemorySecureTokenStore
import com.mars.planner.sync.OP_CREATE
import com.mars.planner.sync.OP_UPDATE
import com.mars.planner.sync.PairingQr
import com.mars.planner.sync.SyncEndpoint
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.SyncOperation
import com.mars.planner.sync.SyncPackage
import com.mars.planner.sync.TlsFingerprint
import com.mars.planner.sync.VersionHash
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertThrows

/**
 * Примитивы протокола «рубеж-синхронизация» v1, проверяемые на JVM:
 * хранилище секретов сопряжения, разбор QR, идемпотентность `package_id`
 * и сериализация пакета.
 */
class SyncProtocolTest {

    private companion object {
        const val CERT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val PROJECT_UUID = "11111111-1111-1111-1111-111111111111"
        const val TASK_UUID = "22222222-2222-2222-2222-222222222222"
    }

    private fun qrJson(
        protocolVersion: Int = VersionHash.PROTOCOL_VERSION,
        host: String = "192.168.1.50",
        port: Int = 8765,
        token: String = "one-time-token",
        cert: String = CERT
    ): String = JSONObject().apply {
        put("protocol_version", protocolVersion)
        put("host", host)
        put("port", port)
        put("token", token)
        put("cert_sha256", cert)
    }.toString()

    // ——— MemorySecureTokenStore ———

    @Test
    fun memoryTokenStoreRoundtripsPairing() {
        val store = MemorySecureTokenStore(installId = "install-1")

        assertThat(store.isPersistent).isFalse()
        assertThat(store.installId()).isEqualTo("install-1")
        assertThat(store.deviceId()).isNull()
        assertThat(store.deviceToken()).isNull()
        assertThat(store.isPaired()).isFalse()
        assertThat(store.effectiveDeviceId()).isEqualTo("install-1")

        store.savePairing(deviceId = "device-7", deviceToken = "secret-token")

        assertThat(store.deviceId()).isEqualTo("device-7")
        assertThat(store.deviceToken()).isEqualTo("secret-token")
        assertThat(store.isPaired()).isTrue()
        assertThat(store.effectiveDeviceId()).isEqualTo("device-7")
        // install_id остаётся: он нужен журналу операций и после сопряжения.
        assertThat(store.installId()).isEqualTo("install-1")
    }

    @Test
    fun clearPairingKeepsInstallIdAndDropsSecrets() {
        val store = MemorySecureTokenStore(installId = "install-2")
        store.savePairing("device-7", "secret-token")

        store.clearPairing()

        assertThat(store.deviceId()).isNull()
        assertThat(store.deviceToken()).isNull()
        assertThat(store.isPaired()).isFalse()
        assertThat(store.effectiveDeviceId()).isEqualTo("install-2")
    }

    @Test
    fun blankPairingValuesAreRejected() {
        val store = MemorySecureTokenStore(installId = "install-3")

        assertThrows(IllegalArgumentException::class.java) { store.savePairing("", "token") }
        assertThrows(IllegalArgumentException::class.java) { store.savePairing("device", " ") }
        assertThat(store.isPaired()).isFalse()
    }

    @Test
    fun generatedInstallIdIsStableAcrossCalls() {
        val store = MemorySecureTokenStore()

        val first = store.installId()

        assertThat(first).isNotEmpty()
        assertThat(store.installId()).isEqualTo(first)
    }

    // ——— PairingQr ———

    @Test
    fun pairingQrParsesFullPayload() {
        val parsed = PairingQr.parse(qrJson())

        assertThat(parsed.isOk).isTrue()
        val qr = parsed.valueOrNull()!!
        assertThat(qr.protocolVersion).isEqualTo(1)
        assertThat(qr.host).isEqualTo("192.168.1.50")
        assertThat(qr.port).isEqualTo(8765)
        assertThat(qr.token).isEqualTo("one-time-token")
        assertThat(qr.certSha256).isEqualTo(CERT)
    }

    @Test
    fun pairingQrNormalizesFingerprintCaseAndSeparators() {
        val noisy = CERT.uppercase().chunked(2).joinToString(":")

        val qr = PairingQr.parse(qrJson(cert = noisy)).valueOrNull()!!

        assertThat(qr.certSha256).isEqualTo(CERT)
        assertThat(TlsFingerprint.isValidFingerprint(qr.certSha256)).isTrue()
    }

    @Test
    fun pairingQrEndpointIsPinnedHttps() {
        val endpoint = PairingQr.parse(qrJson()).valueOrNull()!!.endpoint()

        assertThat(endpoint.isPinned).isTrue()
        assertThat(endpoint.baseUrl).isEqualTo("https://192.168.1.50:8765")
        assertThat(endpoint.isUsable()).isTrue()
    }

    @Test
    fun pairingQrUsesDefaultPortWhenAbsent() {
        val raw = JSONObject().apply {
            put("protocol_version", VersionHash.PROTOCOL_VERSION)
            put("host", "10.0.0.2")
            put("token", "t")
            put("cert_sha256", CERT)
        }.toString()

        val qr = PairingQr.parse(raw).valueOrNull()!!

        assertThat(qr.port).isEqualTo(SyncEndpoint.DEFAULT_PORT)
    }

    @Test
    fun pairingQrRejectsForeignProtocolVersion() {
        val failure = PairingQr.parse(qrJson(protocolVersion = 2)).failureOrNull()!!

        assertThat(failure.code).isEqualTo(SyncErrorCodes.UNSUPPORTED_PROTOCOL)
        assertThat(failure.messageRu).isNotEmpty()
    }

    @Test
    fun pairingQrRejectsMalformedPayloads() {
        val cases = mapOf(
            "пусто" to "",
            "не JSON" to "рубеж://pair?token=1",
            "нет хоста" to qrJson(host = " "),
            "нет токена" to qrJson(token = ""),
            "порт вне диапазона" to qrJson(port = 70_000),
            "короткий отпечаток" to qrJson(cert = "abc"),
            "не hex в отпечатке" to qrJson(cert = CERT.dropLast(1) + "z")
        )

        cases.forEach { (name, raw) ->
            val failure = PairingQr.parse(raw).failureOrNull()
            assertThat(failure).isNotNull()
            assertThat(failure!!.code).isEqualTo(SyncErrorCodes.BAD_QR)
            assertThat(name).isNotEmpty()
        }
        assertThat(PairingQr.parse(null).failureOrNull()!!.code).isEqualTo(SyncErrorCodes.BAD_QR)
    }

    @Test
    fun endpointWithBrokenFingerprintIsNotUsable() {
        val endpoint = SyncEndpoint(host = "192.168.1.50", port = 8765, certSha256 = "abc")

        assertThat(endpoint.isUsable()).isFalse()
    }

    // ——— package_id ———

    @Test
    fun packageIdIsDeterministicForSameOperations() {
        val first = SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"))
        val second = SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"))

        assertThat(second).isEqualTo(first)
        // Фиксированный вектор: повторная отправка того же набора идемпотентна для ПК.
        assertThat(first).isEqualTo("d4df27ad-b5c1-5a1d-9466-b1c7c8b569b3")
    }

    @Test
    fun packageIdChangesWithAttemptSenderAndOperationSet() {
        val base = SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"))

        assertThat(SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"), attempt = 1))
            .isEqualTo("b3399194-60d9-5b28-b0aa-e1fd5396eba3")
        assertThat(SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"), attempt = 1))
            .isNotEqualTo(base)
        assertThat(SyncPackage.packageIdFor("device-2", listOf("op-1", "op-2"))).isNotEqualTo(base)
        assertThat(SyncPackage.packageIdFor("device-1", listOf("op-2", "op-1"))).isNotEqualTo(base)
        assertThat(SyncPackage.packageIdFor("device-1", listOf("op-1"))).isNotEqualTo(base)
    }

    @Test
    fun packageIdLooksLikeUuid() {
        val id = SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2"))

        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    }

    // ——— Пакет ———

    @Test
    fun packageJsonRoundtripsThroughProtocolFormat() {
        val pkg = SyncPackage(
            packageId = SyncPackage.packageIdFor("device-1", listOf("op-1", "op-2")),
            senderDeviceId = "device-1",
            createdAtUtc = VersionHash.formatUtc(1_788_260_400_000L),
            operations = listOf(projectCreate(), taskCreate())
        )

        val restored = SyncPackage.fromJson(JSONObject(pkg.toJson().toString()))

        assertThat(restored.protocolVersion).isEqualTo(1)
        assertThat(restored.packageId).isEqualTo(pkg.packageId)
        assertThat(restored.senderDeviceId).isEqualTo("device-1")
        assertThat(restored.createdAtUtc).isEqualTo("2026-09-01T11:00:00+00:00")
        assertThat(restored.operations.map { it.operationId }).containsExactly("op-1", "op-2").inOrder()
        assertThat(restored.operations[1].projectRef).isEqualTo(PROJECT_UUID)
        assertThat(restored.wireSizeBytes()).isEqualTo(pkg.wireSizeBytes())
    }

    @Test
    fun packageFromJsonRejectsUnsupportedProtocolVersion() {
        val raw = JSONObject().apply {
            put("protocol_version", 2)
            put("package_id", "p")
            put("sender_device_id", "device-1")
            put("operations", org.json.JSONArray())
        }

        val error = assertThrows(SyncEngineException::class.java) { SyncPackage.fromJson(raw) }

        assertThat(error.code).isEqualTo(SyncErrorCodes.UNSUPPORTED_PROTOCOL)
    }

    @Test
    fun operationFromJsonRejectsUnknownEntityAndOp() {
        val unknownEntity = projectCreate().toJson().apply { put("entity_type", "note") }
        val unknownOp = projectCreate().toJson().apply { put("op", "merge") }
        val noId = projectCreate().toJson().apply { put("operation_id", "") }

        assertThat(
            assertThrows(SyncEngineException::class.java) { SyncOperation.fromJson(unknownEntity) }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
        assertThat(
            assertThrows(SyncEngineException::class.java) { SyncOperation.fromJson(unknownOp) }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
        assertThat(
            assertThrows(SyncEngineException::class.java) { SyncOperation.fromJson(noId) }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun deleteOperationCarriesNullPayload() {
        val delete = SyncOperation(
            operationId = "op-3",
            entityType = ENTITY_TASK,
            entityUuid = TASK_UUID,
            op = "delete",
            baseHash = "h-old",
            newHash = null,
            payload = null,
            deviceId = "device-1"
        )

        val restored = SyncOperation.fromJson(JSONObject(delete.toJson().toString()))

        assertThat(restored.payload).isNull()
        assertThat(restored.newHash).isNull()
        assertThat(restored.baseHash).isEqualTo("h-old")
        assertThat(restored.canonicalPayloadJson()).isNull()
    }

    @Test
    fun causalOrderKeepsProjectBeforeDependentTask() {
        val ordered = CausalOrder.order(listOf(taskCreate(), projectCreate()))

        assertThat(ordered.map { it.operationId }).containsExactly("op-1", "op-2").inOrder()
    }

    @Test
    fun causalOrderKeepsSameEntityOperationsInSequence() {
        val update = taskCreate().copy(operationId = "op-3", op = OP_UPDATE, baseHash = "h-task")

        val ordered = CausalOrder.order(listOf(projectCreate(), taskCreate(), update))

        assertThat(ordered.map { it.operationId }).containsExactly("op-1", "op-2", "op-3").inOrder()
    }

    @Test
    fun trimToWireLimitKeepsDependencyClosedPrefix() {
        val ops = listOf(projectCreate(), taskCreate())
        val full = SyncPackage(
            packageId = SyncPackage.packageIdFor("device-1", ops.map { it.operationId }),
            senderDeviceId = "device-1",
            createdAtUtc = VersionHash.formatUtc(0L),
            operations = ops
        )

        assertThat(CausalOrder.trimToWireLimit(ops, "device-1", full.wireSizeBytes())).hasSize(2)
        assertThat(CausalOrder.trimToWireLimit(ops, "device-1", full.wireSizeBytes() - 1))
            .containsExactly(ops.first())
    }

    @Test
    fun trimToWireLimitFailsWhenSingleOperationTooLarge() {
        val error = assertThrows(SyncEngineException::class.java) {
            CausalOrder.trimToWireLimit(listOf(projectCreate()), "device-1", maxBytes = 10)
        }

        assertThat(error.code).isEqualTo(SyncErrorCodes.REQUEST_TOO_LARGE)
    }

    private fun projectCreate(): SyncOperation {
        val payload = VersionHash.projectPayload(
            syncUuid = PROJECT_UUID,
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00Z"
        )
        return SyncOperation(
            operationId = "op-1",
            entityType = ENTITY_PROJECT,
            entityUuid = PROJECT_UUID,
            op = OP_CREATE,
            baseHash = null,
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = "device-1"
        )
    }

    private fun taskCreate(): SyncOperation {
        val payload = VersionHash.taskPayload(
            syncUuid = TASK_UUID,
            title = "Купить краску",
            description = "",
            projectUuid = PROJECT_UUID,
            priority = "normal",
            dueAtUtc = "2026-09-10T15:00:00Z",
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00Z"
        )
        return SyncOperation(
            operationId = "op-2",
            entityType = ENTITY_TASK,
            entityUuid = TASK_UUID,
            op = OP_CREATE,
            baseHash = null,
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = "device-1"
        )
    }
}
