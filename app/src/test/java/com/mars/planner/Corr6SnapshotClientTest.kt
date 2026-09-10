package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.FullSnapshotCodec
import com.mars.planner.sync.FullSnapshotValidator
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.runWithBusyFlag
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

class Corr6SnapshotClientTest {

    @Test
    fun corruptSnapshotInfoMissingFieldThrows() {
        val json = JSONObject(
            """{"ok":true,"snapshot_id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","created_at":"2026-09-08T12:00:00+00:00"}"""
        )
        val result = runCatching { FullSnapshotCodec.parseInfo(json) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun corruptSnapshotInfoWrongNumericTypeThrows() {
        val json = JSONObject()
            .put("snapshot_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            .put("created_at", "2026-09-08T12:00:00+00:00")
            .put("project_count", "not-a-number")
            .put("task_count", 1)
            .put("byte_size", 100)
            .put("sha256", "a".repeat(64))
            .put("chunk_count", 1)
        val result = runCatching { FullSnapshotCodec.parseInfo(json) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun corruptSnapshotChunkMissingPayloadThrows() {
        val json = JSONObject()
            .put("snapshot_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            .put("chunk_index", 0)
            .put("chunk_count", 1)
            .put("sha256", "a".repeat(64))
        val result = runCatching { FullSnapshotCodec.parseChunkResponse(json) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun corruptSnapshotChunkWrongIndexTypeThrows() {
        val json = JSONObject()
            .put("snapshot_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
            .put("chunk_index", "zero")
            .put("chunk_count", 1)
            .put("sha256", "a".repeat(64))
            .put(
                "payload",
                JSONObject()
                    .put("snapshot_kind", "pc_primary_full")
                    .put("protocol_version", 1)
                    .put("created_at", "2026-09-08T12:00:00+00:00")
                    .put("projects", org.json.JSONArray())
                    .put("tasks", org.json.JSONArray())
            )
        val result = runCatching { FullSnapshotCodec.parseChunkResponse(json) }
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun validateInfoRejectsHugeChunkCountAsCorrupt() {
        val info = com.mars.planner.sync.SnapshotInfo(
            snapshotId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            projectCount = 1,
            taskCount = 1,
            byteSize = 100,
            sha256 = "a".repeat(64),
            chunkCount = FullSnapshotValidator.MAX_CHUNK_COUNT + 1
        )
        val ex = runCatching { FullSnapshotValidator.validateInfo(info) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(SyncEngineException::class.java)
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun validateInfoRejectsHugeByteSize() {
        val info = com.mars.planner.sync.SnapshotInfo(
            snapshotId = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            projectCount = 1,
            taskCount = 1,
            byteSize = Int.MAX_VALUE,
            sha256 = "a".repeat(64),
            chunkCount = 1
        )
        val ex = runCatching { FullSnapshotValidator.validateInfo(info) }.exceptionOrNull()
        assertThat(ex).isNotNull()
        assertThat((ex as SyncEngineException).code).isEqualTo(SyncErrorCodes.SNAPSHOT_CORRUPT)
    }

    @Test
    fun busyFlagClearedOnException() = runBlocking {
        var busy = false
        val seen = mutableListOf<Boolean>()
        runCatching {
            runWithBusyFlag({
                busy = it
                seen += it
            }) {
                error("boom")
            }
        }
        assertThat(busy).isFalse()
        assertThat(seen).containsExactly(true, false).inOrder()
    }

    @Test
    fun busyFlagClearedOnSuccess() = runBlocking {
        var busy = true
        runWithBusyFlag({ busy = it }) { /* ok */ }
        assertThat(busy).isFalse()
    }
}
