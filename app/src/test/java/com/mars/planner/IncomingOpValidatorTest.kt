package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.ENTITY_PROJECT
import com.mars.planner.sync.ENTITY_TASK
import com.mars.planner.sync.IncomingOpValidator
import com.mars.planner.sync.OP_CREATE
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.SyncOperation
import com.mars.planner.sync.SyncPackage
import com.mars.planner.sync.VersionHash
import org.junit.Assert.assertThrows
import org.junit.Test

class IncomingOpValidatorTest {

    private companion object {
        const val DEVICE = "22222222-2222-2222-2222-222222222222"
        const val PROJECT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val TASK = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val OP1 = "11111111-1111-4111-8111-111111111111"
        const val OP2 = "33333333-3333-4333-8333-333333333333"
        const val PKG = "dddddddd-dddd-5ddd-8ddd-dddddddddddd"
    }

    @Test
    fun validProjectAndTaskPass() {
        IncomingOpValidator.validatePackage(
            SyncPackage(
                packageId = PKG,
                senderDeviceId = DEVICE,
                createdAtUtc = "2026-09-08T12:00:00.123456+00:00",
                operations = listOf(validProjectOp(), validTaskOp())
            )
        )
    }

    @Test
    fun missingTitleIsRejected() {
        val payload = validTaskPayload().toMutableMap().also { it.remove("title") }
        assertInvalidPayload(validTaskOp(payload))
    }

    @Test
    fun missingNameIsRejected() {
        val payload = validProjectPayload().toMutableMap().also { it.remove("name") }
        assertInvalidPayload(validProjectOp(payload))
    }

    @Test
    fun descriptionWrongTypeIsRejected() {
        val payload = validTaskPayload() + ("description" to 1)
        assertInvalidPayload(validTaskOp(payload))
    }

    @Test
    fun archivedWrongTypeIsRejected() {
        val payload = validProjectPayload() + ("archived" to "false")
        assertInvalidPayload(validProjectOp(payload))
    }

    @Test
    fun unknownPriorityIsRejected() {
        val payload = validTaskPayload() + ("priority" to "urgent")
        assertInvalidPayload(validTaskOp(payload))
    }

    @Test
    fun unknownStatusIsRejected() {
        val payload = validTaskPayload() + ("status" to "cancelled")
        assertInvalidPayload(validTaskOp(payload))
    }

    @Test
    fun blankDeviceIdIsRejected() {
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(
                    validTaskOp().copy(deviceId = ""),
                    DEVICE
                )
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun malformedDueAtIsRejected() {
        val payload = validTaskPayload() + ("due_at" to "tomorrow")
        assertInvalidPayload(validTaskOp(payload))
    }

    @Test
    fun missingPackageCreatedAtIsRejected() {
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validatePackage(
                    SyncPackage(
                        packageId = PKG,
                        senderDeviceId = DEVICE,
                        createdAtUtc = "",
                        operations = listOf(validProjectOp())
                    )
                )
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun malformedPackageCreatedAtIsRejected() {
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validatePackage(
                    SyncPackage(
                        packageId = PKG,
                        senderDeviceId = DEVICE,
                        createdAtUtc = "not-a-date",
                        operations = listOf(validProjectOp())
                    )
                )
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun invalidPackageIdIsRejected() {
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validatePackage(
                    SyncPackage(
                        packageId = "not-a-uuid",
                        senderDeviceId = DEVICE,
                        createdAtUtc = "2026-09-08T12:00:00+00:00",
                        operations = listOf(validProjectOp())
                    )
                )
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    private fun assertInvalidPayload(op: SyncOperation) {
        val rebuilt = when (op.entityType) {
            ENTITY_PROJECT, ENTITY_TASK -> {
                val payload = op.payload
                if (payload == null) op
                else op.copy(newHash = VersionHash.versionHash(payload), payload = payload)
            }
            else -> op
        }
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                IncomingOpValidator.validateOperation(rebuilt, DEVICE)
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    private fun validProjectPayload(): Map<String, Any?> = VersionHash.projectPayload(
        syncUuid = PROJECT,
        name = "Дом",
        description = "",
        archived = false,
        createdAtUtc = "2026-09-01T10:00:00.123456+00:00"
    )

    private fun validTaskPayload(): Map<String, Any?> = VersionHash.taskPayload(
        syncUuid = TASK,
        title = "Купить краску",
        description = "",
        projectUuid = PROJECT,
        priority = "normal",
        dueAtUtc = "2026-09-10T15:00:00.654321+00:00",
        status = "open",
        createdAtUtc = "2026-09-01T11:00:00.123456+00:00"
    )

    private fun validProjectOp(
        payload: Map<String, Any?> = validProjectPayload()
    ): SyncOperation = SyncOperation(
        operationId = OP1,
        entityType = ENTITY_PROJECT,
        entityUuid = PROJECT,
        op = OP_CREATE,
        newHash = VersionHash.versionHash(payload),
        payload = payload,
        deviceId = DEVICE
    )

    private fun validTaskOp(
        payload: Map<String, Any?> = validTaskPayload()
    ): SyncOperation = SyncOperation(
        operationId = OP2,
        entityType = ENTITY_TASK,
        entityUuid = TASK,
        op = OP_CREATE,
        newHash = VersionHash.versionHash(payload),
        payload = payload,
        deviceId = DEVICE
    )
}
