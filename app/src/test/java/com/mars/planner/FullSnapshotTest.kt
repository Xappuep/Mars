package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.FullSnapshot
import com.mars.planner.sync.FullSnapshotCodec
import com.mars.planner.sync.FullSnapshotValidator
import com.mars.planner.sync.SNAPSHOT_KIND_PC_PRIMARY
import com.mars.planner.sync.SnapshotInfo
import com.mars.planner.sync.SnapshotProject
import com.mars.planner.sync.SnapshotTask
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.VersionHash
import org.junit.Assert.assertThrows
import org.junit.Test

class FullSnapshotTest {

    private fun project(
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

    private fun task(
        uuid: String = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        projectUuid: String? = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        status: String = "open",
        priority: String = "normal"
    ): SnapshotTask {
        val payload = VersionHash.taskPayload(
            syncUuid = uuid,
            title = "Купить",
            description = "",
            projectUuid = projectUuid,
            priority = priority,
            dueAtUtc = null,
            status = status,
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        return SnapshotTask(
            syncUuid = uuid,
            title = "Купить",
            description = "",
            projectUuid = projectUuid,
            priority = priority,
            dueAtUtc = null,
            status = status,
            createdAtUtc = "2026-09-01T11:00:00+00:00",
            versionHash = VersionHash.versionHash(payload)
        )
    }

    private fun snapshot(
        projects: List<SnapshotProject> = listOf(project()),
        tasks: List<SnapshotTask> = listOf(task())
    ): FullSnapshot = FullSnapshot(
        snapshotKind = SNAPSHOT_KIND_PC_PRIMARY,
        protocolVersion = 1,
        createdAtUtc = "2026-09-08T12:00:00+00:00",
        projects = projects,
        tasks = tasks
    )

    @Test
    fun validSnapshotPasses() {
        val snap = snapshot(
            projects = listOf(project(), project("cccccccc-cccc-cccc-cccc-cccccccccccc", "Архив", true)),
            tasks = listOf(
                task(status = "done"),
                task("dddddddd-dddd-dddd-dddd-dddddddddddd", projectUuid = null)
            )
        )
        FullSnapshotValidator.validate(snap, snap.sha256())
    }

    @Test
    fun corruptChecksumRejected() {
        val snap = snapshot()
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, "0".repeat(64))
            }.code
        ).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun unknownStatusRejected() {
        val bad = task(status = "cancelled")
        val snap = snapshot(tasks = listOf(bad))
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, snap.sha256())
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun unknownPriorityRejected() {
        val bad = task(priority = "urgent")
        val snap = snapshot(tasks = listOf(bad))
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, snap.sha256())
            }.code
        ).isEqualTo(SyncErrorCodes.INVALID_PAYLOAD)
    }

    @Test
    fun missingProjectRefRejected() {
        val bad = task(projectUuid = "99999999-9999-4999-8999-999999999999")
        val snap = snapshot(tasks = listOf(bad))
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, snap.sha256())
            }.code
        ).isEqualTo(SyncErrorCodes.UNRESOLVED_DEPENDENCY)
    }

    @Test
    fun mergeChunksPreservesEntities() {
        val p = project()
        val t = task()
        val a = FullSnapshot(
            snapshotKind = SNAPSHOT_KIND_PC_PRIMARY,
            protocolVersion = 1,
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            projects = listOf(p),
            tasks = emptyList()
        )
        val b = FullSnapshot(
            snapshotKind = SNAPSHOT_KIND_PC_PRIMARY,
            protocolVersion = 1,
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            projects = emptyList(),
            tasks = listOf(t)
        )
        val merged = FullSnapshotCodec.mergeChunks(listOf(a, b))
        assertThat(merged.projects).hasSize(1)
        assertThat(merged.tasks).hasSize(1)
        FullSnapshotValidator.validate(merged, merged.sha256())
    }

    @Test
    fun uuidPreservedInSnapshotEntities() {
        val uuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val snap = snapshot(projects = listOf(project(uuid)))
        assertThat(snap.projects.single().syncUuid).isEqualTo(uuid)
    }

    @Test
    fun incompatibleProtocolRejected() {
        val snap = snapshot().copy(protocolVersion = 99)
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, snap.sha256())
            }.code
        ).isEqualTo(SyncErrorCodes.UNSUPPORTED_PROTOCOL)
    }

    @Test
    fun projectsProcessedBeforeTaskRefs() {
        val snap = snapshot(
            projects = listOf(project()),
            tasks = listOf(task())
        )
        assertThat(snap.projects).isNotEmpty()
        FullSnapshotValidator.validate(snap, snap.sha256())
    }

    @Test
    fun infoRejectsBadShaAndCounts() {
        val good = SnapshotInfo(
            snapshotId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            projectCount = 1,
            taskCount = 1,
            byteSize = 100,
            sha256 = "a".repeat(64),
            chunkCount = 1
        )
        FullSnapshotValidator.validateInfo(good)
        assertThrows(SyncEngineException::class.java) {
            FullSnapshotValidator.validateInfo(good.copy(sha256 = "zz"))
        }
        assertThrows(SyncEngineException::class.java) {
            FullSnapshotValidator.validateInfo(good.copy(chunkCount = 0))
        }
        assertThrows(SyncEngineException::class.java) {
            FullSnapshotValidator.validateInfo(good.copy(byteSize = 0))
        }
        assertThrows(SyncEngineException::class.java) {
            FullSnapshotValidator.validateInfo(good.copy(projectCount = -1))
        }
    }

    @Test
    fun duplicateUuidRejected() {
        val p = project()
        val snap = snapshot(projects = listOf(p, p), tasks = emptyList())
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validate(snap, snap.sha256())
            }.code
        ).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun mergedCountsMustMatchInfo() {
        val snap = snapshot()
        val info = SnapshotInfo(
            snapshotId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            createdAtUtc = snap.createdAtUtc,
            projectCount = 99,
            taskCount = snap.tasks.size,
            byteSize = VersionHash.canonicalJson(snap.contentForHash()).toByteArray(Charsets.UTF_8).size,
            sha256 = snap.sha256(),
            chunkCount = 1
        )
        assertThat(
            assertThrows(SyncEngineException::class.java) {
                FullSnapshotValidator.validateMergedAgainstInfo(snap, info)
            }.code
        ).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }
}
