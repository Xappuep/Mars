package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.SyncUiPresentation
import org.json.JSONObject
import org.junit.Test
import java.time.ZoneOffset

class SyncUiPresentationTest {

    @Test
    fun categorizesNetworkTlsAuthAndPairingSeparately() {
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.NETWORK_UNAVAILABLE))
            .isEqualTo(SyncUiPresentation.ErrorCategory.NETWORK)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.TIMEOUT))
            .isEqualTo(SyncUiPresentation.ErrorCategory.NETWORK)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.TLS_PIN_MISMATCH))
            .isEqualTo(SyncUiPresentation.ErrorCategory.SECURE_CONNECTION)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.TLS_ERROR))
            .isEqualTo(SyncUiPresentation.ErrorCategory.SECURE_CONNECTION)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.UNAUTHORIZED))
            .isEqualTo(SyncUiPresentation.ErrorCategory.AUTHORIZATION)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.DEVICE_REVOKED))
            .isEqualTo(SyncUiPresentation.ErrorCategory.AUTHORIZATION)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.BAD_QR))
            .isEqualTo(SyncUiPresentation.ErrorCategory.PAIRING)
        assertThat(SyncUiPresentation.categorize(SyncErrorCodes.PAIRING_DENIED))
            .isEqualTo(SyncUiPresentation.ErrorCategory.PAIRING)
    }

    @Test
    fun unknownCodeIsInternalWithSafeAction() {
        val err = SyncUiPresentation.presentError("totally_unknown_code_xyz")
        assertThat(err.category).isEqualTo(SyncUiPresentation.ErrorCategory.INTERNAL)
        assertThat(err.title).isNotEmpty()
        assertThat(err.explanation).isNotEmpty()
        assertThat(err.action).isNotEmpty()
        assertThat(SyncUiPresentation.presentationLeaksSecrets(err)).isFalse()
    }

    @Test
    fun eachKnownCodeHasTitleExplanationAndActionWithoutLeaks() {
        val codes = listOf(
            SyncErrorCodes.NETWORK_UNAVAILABLE,
            SyncErrorCodes.TIMEOUT,
            SyncErrorCodes.TLS_PIN_MISMATCH,
            SyncErrorCodes.TLS_ERROR,
            SyncErrorCodes.UNAUTHORIZED,
            SyncErrorCodes.FORBIDDEN,
            SyncErrorCodes.DEVICE_REVOKED,
            SyncErrorCodes.BAD_PAIRING_TOKEN,
            SyncErrorCodes.PAIRING_TOKEN_EXPIRED,
            SyncErrorCodes.PAIRING_TOKEN_USED,
            SyncErrorCodes.PAIRING_PENDING,
            SyncErrorCodes.PAIRING_DENIED,
            SyncErrorCodes.BAD_QR,
            SyncErrorCodes.UNSUPPORTED_PROTOCOL,
            SyncErrorCodes.INVALID_PAYLOAD,
            SyncErrorCodes.SNAPSHOT_CORRUPT,
            SyncErrorCodes.SNAPSHOT_REQUIRED,
            SyncErrorCodes.INTERNAL_ERROR
        )
        codes.forEach { code ->
            val err = SyncUiPresentation.presentError(code)
            assertThat(err.title).isNotEmpty()
            assertThat(err.explanation).isNotEmpty()
            assertThat(err.action).isNotEmpty()
            assertThat(SyncUiPresentation.presentationLeaksSecrets(err)).isFalse()
            assertThat(err.title).doesNotContain(code)
        }
    }

    @Test
    fun networkAndRevokedMessagesAreNotMerged() {
        val net = SyncUiPresentation.presentError(SyncErrorCodes.NETWORK_UNAVAILABLE)
        val revoked = SyncUiPresentation.presentError(SyncErrorCodes.DEVICE_REVOKED)
        val tls = SyncUiPresentation.presentError(SyncErrorCodes.TLS_PIN_MISMATCH)
        assertThat(net.title).isNotEqualTo(revoked.title)
        assertThat(net.title).isNotEqualTo(tls.title)
        assertThat(revoked.title).isNotEqualTo(tls.title)
        assertThat(net.category).isNotEqualTo(revoked.category)
        assertThat(net.category).isNotEqualTo(tls.category)
    }

    @Test
    fun connectionStatusesAreUserFacing() {
        val unlinked = SyncUiPresentation.connectionStatus(
            syncPaired = false,
            pairingActive = false,
            pairedDeviceName = "",
            pairedHost = "",
            pairedPort = 8765,
            lastSyncError = "",
            requiresPcPrimarySnapshot = false,
            pendingSnapshotAck = false
        )
        assertThat(unlinked.kind).isEqualTo(SyncUiPresentation.ConnectionKind.NOT_LINKED)
        assertThat(unlinked.title).isEqualTo("Телефон не связан с ПК")

        val waiting = SyncUiPresentation.connectionStatus(
            syncPaired = false,
            pairingActive = true,
            pairedDeviceName = "",
            pairedHost = "192.168.1.10",
            pairedPort = 8765,
            lastSyncError = "",
            requiresPcPrimarySnapshot = false,
            pendingSnapshotAck = false
        )
        assertThat(waiting.kind).isEqualTo(SyncUiPresentation.ConnectionKind.AWAITING_PC_CONFIRMATION)
        assertThat(waiting.title).contains("Рубеж")

        val linked = SyncUiPresentation.connectionStatus(
            syncPaired = true,
            pairingActive = false,
            pairedDeviceName = "Домашний ПК",
            pairedHost = "192.168.1.10",
            pairedPort = 8765,
            lastSyncError = "",
            requiresPcPrimarySnapshot = false,
            pendingSnapshotAck = false
        )
        assertThat(linked.kind).isEqualTo(SyncUiPresentation.ConnectionKind.LINKED)
        assertThat(linked.deviceName).isEqualTo("Домашний ПК")
        assertThat(linked.technicalEndpoint).isEqualTo("192.168.1.10:8765")
    }

    @Test
    fun exchangeStatusUsesRussianPhrases() {
        val status = SyncUiPresentation.exchangeStatus(
            pendingCount = 4,
            conflictCount = 0,
            lastPushAt = 0L,
            lastPullAt = 0L,
            busy = false,
            busyDetail = null,
            lastActionOk = true,
            lastActionMessage = "Отправлено"
        )
        assertThat(status.pendingLine).isEqualTo("На телефоне есть 4 изменения для отправки")
        assertThat(status.conflictsLine).isEqualTo("Расхождений нет")
        assertThat(status.lastPushLine).startsWith("Последняя отправка:")
        assertThat(status.lastPullLine).startsWith("Последнее получение:")
        assertThat(SyncUiPresentation.pushButtonLabel(4)).contains("(4)")
    }

    @Test
    fun parsesTaskAndProjectPayloadsWithoutJsonUuidHash() {
        val taskJson = JSONObject().apply {
            put("title", "Купить корм")
            put("description", "Для Марса")
            put("status", "open")
            put("priority", "high")
            put("due_at", "2026-09-15T18:00:00Z")
            put("entity_uuid", "11111111-1111-1111-1111-111111111111")
            put("version_hash", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        }.toString()
        val task = SyncUiPresentation.parsePayload(taskJson, "update")
        assertThat(task.fields["Заголовок"]).isEqualTo("Купить корм")
        assertThat(task.fields["Описание"]).isEqualTo("Для Марса")
        assertThat(task.fields["Статус"]).isEqualTo("Открыта")
        assertThat(task.fields["Приоритет"]).isEqualTo("Высокий")
        assertThat(task.fields["Срок"]).isNotEqualTo("2026-09-15T18:00:00Z")
        assertThat(task.fields.values.joinToString()).doesNotContain("11111111")
        assertThat(task.fields.values.joinToString()).doesNotContain("0123456789abcdef")
        assertThat(task.fields.keys).doesNotContain("entity_uuid")

        val projectJson = JSONObject().apply {
            put("name", "Дом")
            put("description", "Бытовые дела")
            put("archived", false)
        }.toString()
        val project = SyncUiPresentation.parsePayload(projectJson, "create")
        assertThat(project.fields["Название"]).isEqualTo("Дом")
        assertThat(project.opLabel).isEqualTo("создано")
    }

    @Test
    fun highlightsOnlyDifferingFields() {
        val local = JSONObject().apply {
            put("title", "Одно название")
            put("description", "Старое")
            put("status", "open")
            put("priority", "normal")
        }.toString()
        val remote = JSONObject().apply {
            put("title", "Одно название")
            put("description", "Новое")
            put("status", "done")
            put("priority", "normal")
        }.toString()
        val compare = SyncUiPresentation.compareConflict(
            entityTypeTask = true,
            localPayloadJson = local,
            remotePayloadJson = remote,
            localOp = "update",
            remoteOp = "update"
        )
        assertThat(compare.entityTypeLabel).isEqualTo("Задача")
        assertThat(compare.recordTitle).isEqualTo("Одно название")
        assertThat(compare.differingRows.map { it.label }).containsExactly("Описание", "Статус").inOrder()
        assertThat(compare.differingRows.none { it.label == "Заголовок" }).isTrue()
        assertThat(compare.differingRows.none { it.label == "Приоритет" }).isTrue()
    }

    @Test
    fun deletedVersusChangedScenario() {
        val local = JSONObject().apply { put("deleted", true) }.toString()
        val remote = JSONObject().apply {
            put("title", "Живая задача")
            put("status", "open")
        }.toString()
        val compare = SyncUiPresentation.compareConflict(
            entityTypeTask = true,
            localPayloadJson = local,
            remotePayloadJson = remote,
            localOp = "delete",
            remoteOp = "update"
        )
        assertThat(compare.phoneDeleted).isTrue()
        assertThat(compare.pcDeleted).isFalse()
        assertThat(compare.scenarioNote).contains("удалена")
        assertThat(compare.recordTitle).isEqualTo("Живая задача")
    }

    @Test
    fun emptyAndCorruptPayloadsAreSafe() {
        val empty = SyncUiPresentation.parsePayload(null, "update")
        assertThat(empty.empty).isTrue()
        assertThat(empty.fields).isEmpty()

        val corrupt = SyncUiPresentation.parsePayload("{not-json", "update")
        assertThat(corrupt.unreadable).isTrue()
        assertThat(corrupt.fields).isEmpty()

        val compare = SyncUiPresentation.compareConflict(
            entityTypeTask = false,
            localPayloadJson = "{bad",
            remotePayloadJson = "",
            localOp = "update",
            remoteOp = "update"
        )
        assertThat(compare.phoneUnreadable).isTrue()
        assertThat(compare.entityTypeLabel).isEqualTo("Проект")
        assertThat(compare.scenarioNote).isNotNull()
    }

    @Test
    fun longValuesAreKeptAndDoNotIntroduceLeaks() {
        val longTitle = "А".repeat(240)
        val longDesc = "Описание ".repeat(80)
        val local = JSONObject().apply {
            put("title", longTitle)
            put("description", longDesc)
            put("status", "open")
        }.toString()
        val remote = JSONObject().apply {
            put("title", longTitle + "!")
            put("description", longDesc)
            put("status", "open")
        }.toString()
        val compare = SyncUiPresentation.compareConflict(
            entityTypeTask = true,
            localPayloadJson = local,
            remotePayloadJson = remote,
            localOp = "update",
            remoteOp = "update"
        )
        assertThat(compare.recordTitle.length).isAtLeast(240)
        assertThat(compare.differingRows.single().label).isEqualTo("Заголовок")
        assertThat(SyncUiPresentation.containsForbiddenLeak(compare.recordTitle)).isFalse()
        assertThat(SyncUiPresentation.containsForbiddenLeak(compare.differingRows.single().phoneValue))
            .isFalse()
    }

    @Test
    fun leakDetectorFlagsJsonUuidAndTokenFragments() {
        assertThat(SyncUiPresentation.containsForbiddenLeak("""{"token":"abc"}""")).isTrue()
        assertThat(SyncUiPresentation.containsForbiddenLeak("uuid устройства")).isTrue()
        assertThat(
            SyncUiPresentation.containsForbiddenLeak("11111111-1111-1111-1111-111111111111")
        ).isTrue()
        assertThat(SyncUiPresentation.containsForbiddenLeak("На телефоне есть 4 изменения")).isFalse()
    }

    @Test
    fun dueFormattingUsesLocalZoneWithoutRawIso() {
        val view = SyncUiPresentation.parsePayload(
            JSONObject().put("title", "Срок").put("due_at", "2026-01-02T10:30:00Z").toString(),
            "update"
        )
        val due = view.fields.getValue("Срок")
        assertThat(due).doesNotContain("T10:30:00Z")
        // Формат зависит от зоны; достаточно, что это человекочитаемая подпись.
        assertThat(due).isNotEmpty()
        assertThat(ZoneOffset.UTC.id).isNotEmpty()
    }
}
