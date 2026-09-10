package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.prefs.LegacyDataStoreCleanup
import com.mars.planner.sync.ENTITY_PROJECT
import com.mars.planner.sync.ENTITY_TASK
import com.mars.planner.sync.IncomingOpValidator
import com.mars.planner.sync.OP_CREATE
import com.mars.planner.sync.OP_DELETE
import com.mars.planner.sync.OP_UPDATE
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.SyncOperation
import com.mars.planner.sync.SyncPackage
import com.mars.planner.sync.VersionHash
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertThrows
import org.junit.Test

class Corr2FixesTest {

    private companion object {
        const val DEVICE = "22222222-2222-2222-2222-222222222222"
        const val PROJECT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val TASK = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val OP1 = "11111111-1111-4111-8111-111111111111"
        const val OP2 = "33333333-3333-4333-8333-333333333333"
    }

    @Test
    fun legacyCleanupPhysicallyRemovesPlaintextSyncKey() {
        val prefs = mutablePreferencesOf(
            LegacyDataStoreCleanup.syncKey to "legacy-plaintext-secret",
            LegacyDataStoreCleanup.syncHost to "192.168.1.10",
            LegacyDataStoreCleanup.syncPort to 8765
        )

        val removed = LegacyDataStoreCleanup.purge(prefs)

        assertThat(removed).isTrue()
        assertThat(prefs.contains(LegacyDataStoreCleanup.syncKey)).isFalse()
        assertThat(prefs.contains(LegacyDataStoreCleanup.syncHost)).isFalse()
        assertThat(prefs.contains(LegacyDataStoreCleanup.syncPort)).isFalse()
        assertThat(prefs[LegacyDataStoreCleanup.legacySecretsPurged]).isTrue()
    }

    @Test
    fun legacyCleanupIsIdempotentWhenKeyAlreadyGone() {
        val prefs = mutablePreferencesOf()

        val removed = LegacyDataStoreCleanup.purge(prefs)

        assertThat(removed).isFalse()
        assertThat(prefs[LegacyDataStoreCleanup.legacySecretsPurged]).isTrue()
        assertThat(LegacyDataStoreCleanup.purge(prefs)).isFalse()
    }

    @Test
    fun incomingCreateRequiresMatchingNewHashAndSyncUuid() {
        val payload = VersionHash.projectPayload(
            syncUuid = PROJECT,
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        val good = SyncOperation(
            operationId = OP1,
            entityType = ENTITY_PROJECT,
            entityUuid = PROJECT,
            op = OP_CREATE,
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = DEVICE
        )
        IncomingOpValidator.validateOperation(good, DEVICE)

        val badHash = good.copy(newHash = "0".repeat(64))
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(badHash, DEVICE)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)

        val mismatchedUuid = good.copy(
            payload = payload + ("sync_uuid" to TASK)
        )
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(mismatchedUuid, DEVICE)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun incomingDeleteMustHaveNullPayloadAndNullNewHash() {
        val delete = SyncOperation(
            operationId = OP2,
            entityType = ENTITY_TASK,
            entityUuid = TASK,
            op = OP_DELETE,
            baseHash = "a".repeat(64),
            newHash = null,
            payload = null,
            deviceId = DEVICE
        )
        IncomingOpValidator.validateOperation(delete, DEVICE)

        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(delete.copy(newHash = "b".repeat(64)), DEVICE)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)

        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(
                    delete.copy(payload = mapOf("deleted" to true)),
                    DEVICE
                )
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun incomingRejectsSenderMismatchAndBadDates() {
        val payload = VersionHash.taskPayload(
            syncUuid = TASK,
            title = "Купить",
            description = "",
            projectUuid = PROJECT,
            priority = "normal",
            dueAtUtc = "2026-09-10T15:00:00+00:00",
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        val op = SyncOperation(
            operationId = OP2,
            entityType = ENTITY_TASK,
            entityUuid = TASK,
            op = OP_UPDATE,
            baseHash = "c".repeat(64),
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = DEVICE
        )

        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(op, senderDeviceId = "99999999-9999-4999-8999-999999999999")
            }.code
        ).isEqualTo(SyncErrorCodes.SENDER_MISMATCH)

        val badDate = op.copy(
            payload = payload + ("created_at" to "not-a-date"),
            newHash = VersionHash.versionHash(payload + ("created_at" to "not-a-date"))
        )
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(badDate, DEVICE)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun incomingPackageRejectsEntityTypeMismatchInPayload() {
        val payload = VersionHash.projectPayload(
            syncUuid = PROJECT,
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        val op = SyncOperation(
            operationId = OP1,
            entityType = ENTITY_PROJECT,
            entityUuid = PROJECT,
            op = OP_CREATE,
            newHash = VersionHash.versionHash(payload + ("entity_type" to "task")),
            payload = payload + ("entity_type" to "task"),
            deviceId = DEVICE
        )
        val pkg = SyncPackage(
            packageId = "dddddddd-dddd-5ddd-8ddd-dddddddddddd",
            senderDeviceId = DEVICE,
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            operations = listOf(op)
        )
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validatePackage(pkg)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }
}
