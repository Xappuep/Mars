package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackageApplyInstrumentedTest {

    private companion object {
        const val DEVICE = "22222222-2222-2222-2222-222222222222"
        const val PROJECT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val TASK = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        const val TASK2 = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        const val OP1 = "11111111-1111-4111-8111-111111111111"
        const val OP2 = "33333333-3333-4333-8333-333333333333"
        const val OP3 = "44444444-4444-4444-8444-444444444444"
        const val PKG1 = "dddddddd-dddd-5ddd-8ddd-dddddddddddd"
        const val PKG2 = "eeeeeeee-eeee-5eee-8eee-eeeeeeeeeeee"
        const val PKG3 = "ffffffff-ffff-5fff-8fff-ffffffffffff"
    }

    private lateinit var db: MarsDatabase
    private lateinit var repo: PlannerRepository
    private lateinit var engine: PackageEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = MarsDatabase.buildInMemory(context)
        repo = PlannerRepository(db) { DEVICE }
        engine = PackageEngine(repo, deviceIdProvider = { DEVICE })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun invalidSecondOperationRollsBackFirst() = runBlocking {
        val goodProject = projectCreateOp(OP1, PROJECT, "Дом")
        val badTaskPayload = VersionHash.taskPayload(
            syncUuid = TASK,
            title = "Будет отброшено",
            description = "",
            projectUuid = PROJECT,
            priority = "normal",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        ).toMutableMap().also { it.remove("title") }
        val badTask = SyncOperation(
            operationId = OP2,
            entityType = ENTITY_TASK,
            entityUuid = TASK,
            op = OP_CREATE,
            newHash = VersionHash.versionHash(badTaskPayload),
            payload = badTaskPayload,
            deviceId = DEVICE
        )
        val outcome = engine.applyIncomingPackage(
            SyncPackage(
                packageId = PKG1,
                senderDeviceId = DEVICE,
                createdAtUtc = "2026-09-08T12:00:00+00:00",
                operations = listOf(goodProject, badTask)
            )
        )

        assertThat(outcome).isInstanceOf(ApplyOutcome.Rejected::class.java)
        assertThat((outcome as ApplyOutcome.Rejected).code).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
        assertThat(repo.getProjectByUuid(PROJECT)).isNull()
        assertThat(repo.getTaskByUuid(TASK)).isNull()
        assertThat(db.syncDao().countOperations()).isEqualTo(0)
        assertThat(db.syncDao().getTombstone(PROJECT)).isNull()
    }

    @Test
    fun duplicateOperationIdDoesNotDuplicateOrCrash() = runBlocking {
        val project = projectCreateOp(OP1, PROJECT, "Дом")
        val task = taskCreateOp(OP2, TASK, PROJECT, "Купить")
        val first = engine.applyIncomingPackage(
            SyncPackage(
                packageId = PKG1,
                senderDeviceId = DEVICE,
                createdAtUtc = "2026-09-08T12:00:00+00:00",
                operations = listOf(project, task)
            )
        )
        assertThat(first).isInstanceOf(ApplyOutcome.Applied::class.java)

        val second = engine.applyIncomingPackage(
            SyncPackage(
                packageId = PKG2,
                senderDeviceId = DEVICE,
                createdAtUtc = "2026-09-08T12:01:00+00:00",
                operations = listOf(
                    project,
                    task,
                    taskCreateOp(OP3, TASK2, PROJECT, "Ещё одна")
                )
            )
        )
        assertThat(second).isInstanceOf(ApplyOutcome.Applied::class.java)
        val stats = (second as ApplyOutcome.Applied).stats
        assertThat(stats.skipped).isAtLeast(2)
        assertThat(stats.added).isEqualTo(1)

        assertThat(db.taskDao().getAllOnce().map { it.syncUuid }.toSet())
            .containsExactly(TASK, TASK2)
        assertThat(db.projectDao().getAllOnce()).hasSize(1)
    }

    @Test
    fun appliedTaskLocalHashMatchesNewHash() = runBlocking {
        val project = projectCreateOp(OP1, PROJECT, "Дом")
        val payload = VersionHash.taskPayload(
            syncUuid = TASK,
            title = "Купить краску",
            description = "с микросекундами",
            projectUuid = PROJECT,
            priority = "high",
            dueAtUtc = "2026-09-10T15:00:00.654321+00:00",
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00.123456+00:00"
        )
        val newHash = VersionHash.versionHash(payload)
        val task = SyncOperation(
            operationId = OP2,
            entityType = ENTITY_TASK,
            entityUuid = TASK,
            op = OP_CREATE,
            newHash = newHash,
            payload = payload,
            deviceId = DEVICE
        )
        val outcome = engine.applyIncomingPackage(
            SyncPackage(
                packageId = PKG3,
                senderDeviceId = DEVICE,
                createdAtUtc = "2026-09-08T12:00:00+00:00",
                operations = listOf(project, task)
            )
        )
        assertThat(outcome).isInstanceOf(ApplyOutcome.Applied::class.java)
        assertThat(repo.taskHashByUuid(TASK)).isEqualTo(newHash)
        val stored = repo.getTaskByUuid(TASK)!!
        assertThat(stored.createdAtRawUtc).isEqualTo("2026-09-01T11:00:00.123456+00:00")
        assertThat(stored.dueAtRawUtc).isEqualTo("2026-09-10T15:00:00.654321+00:00")
    }

    private fun projectCreateOp(opId: String, uuid: String, name: String): SyncOperation {
        val payload = VersionHash.projectPayload(
            syncUuid = uuid,
            name = name,
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        return SyncOperation(
            operationId = opId,
            entityType = ENTITY_PROJECT,
            entityUuid = uuid,
            op = OP_CREATE,
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = DEVICE
        )
    }

    private fun taskCreateOp(
        opId: String,
        uuid: String,
        projectUuid: String,
        title: String
    ): SyncOperation {
        val payload = VersionHash.taskPayload(
            syncUuid = uuid,
            title = title,
            description = "",
            projectUuid = projectUuid,
            priority = "normal",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        return SyncOperation(
            operationId = opId,
            entityType = ENTITY_TASK,
            entityUuid = uuid,
            op = OP_CREATE,
            newHash = VersionHash.versionHash(payload),
            payload = payload,
            deviceId = DEVICE
        )
    }
}
