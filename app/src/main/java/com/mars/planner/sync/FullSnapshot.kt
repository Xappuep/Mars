package com.mars.planner.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

const val SNAPSHOT_KIND_PC_PRIMARY = "pc_primary_full"
const val FEATURE_FULL_SNAPSHOT = "full_snapshot"

data class SnapshotInfo(
    val snapshotId: String,
    val createdAtUtc: String,
    val projectCount: Int,
    val taskCount: Int,
    val byteSize: Int,
    val sha256: String,
    val chunkCount: Int
)

data class SnapshotProject(
    val syncUuid: String,
    val name: String,
    val description: String,
    val archived: Boolean,
    val createdAtUtc: String,
    val versionHash: String
) {
    fun payloadMap(): Map<String, Any?> = VersionHash.projectPayload(
        syncUuid = syncUuid,
        name = name,
        description = description,
        archived = archived,
        createdAtUtc = createdAtUtc
    )
}

data class SnapshotTask(
    val syncUuid: String,
    val title: String,
    val description: String,
    val projectUuid: String?,
    val priority: String,
    val dueAtUtc: String?,
    val status: String,
    val createdAtUtc: String,
    val versionHash: String
) {
    fun payloadMap(): Map<String, Any?> = VersionHash.taskPayload(
        syncUuid = syncUuid,
        title = title,
        description = description,
        projectUuid = projectUuid,
        priority = priority,
        dueAtUtc = dueAtUtc,
        status = status,
        createdAtUtc = createdAtUtc
    )
}

data class FullSnapshot(
    val snapshotKind: String,
    val protocolVersion: Int,
    val createdAtUtc: String,
    val projects: List<SnapshotProject>,
    val tasks: List<SnapshotTask>
) {
    fun contentForHash(): Map<String, Any?> = linkedMapOf(
        "created_at" to createdAtUtc,
        "projects" to projects.map { p ->
            linkedMapOf(
                "archived" to p.archived,
                "created_at" to p.createdAtUtc,
                "description" to p.description,
                "entity_type" to ENTITY_PROJECT,
                "name" to p.name,
                "sync_uuid" to p.syncUuid,
                "version_hash" to p.versionHash
            )
        },
        "protocol_version" to protocolVersion,
        "snapshot_kind" to snapshotKind,
        "tasks" to tasks.map { t ->
            linkedMapOf(
                "created_at" to t.createdAtUtc,
                "description" to t.description,
                "due_at" to t.dueAtUtc,
                "entity_type" to ENTITY_TASK,
                "priority" to t.priority,
                "project_uuid" to t.projectUuid,
                "status" to t.status,
                "sync_uuid" to t.syncUuid,
                "title" to t.title,
                "version_hash" to t.versionHash
            )
        }
    )

    fun sha256(): String {
        val bytes = VersionHash.canonicalJson(contentForHash()).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}

data class SnapshotChunkResponse(
    val snapshotId: String,
    val chunkIndex: Int,
    val chunkCount: Int,
    val sha256: String,
    val protocolVersion: Int,
    val payload: FullSnapshot
)

data class SnapshotAckResponse(
    val snapshotId: String,
    val packagesAcked: Int,
    val alreadyAcked: Boolean
)

object FullSnapshotCodec {
    fun parseInfo(json: JSONObject): SnapshotInfo = SnapshotInfo(
        snapshotId = json.getString("snapshot_id"),
        createdAtUtc = json.getString("created_at"),
        projectCount = json.getInt("project_count"),
        taskCount = json.getInt("task_count"),
        byteSize = json.getInt("byte_size"),
        sha256 = json.getString("sha256"),
        chunkCount = json.getInt("chunk_count")
    )

    fun parseAck(json: JSONObject, expectedSnapshotId: String): SnapshotAckResponse {
        if (!json.has("ok")) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Нет поля ok")
        }
        val okRaw = json.get("ok")
        if (okRaw !is Boolean) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "ok должен быть boolean")
        }
        if (!okRaw) {
            val err = json.optString("error").ifBlank { SyncErrorCodes.SNAPSHOT_CORRUPT }
            throw SyncEngineException(err, "ACK отклонён")
        }
        if (!json.has("snapshot_id")) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Нет snapshot_id")
        }
        val sidRaw = json.get("snapshot_id")
        if (sidRaw !is String) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "snapshot_id должен быть строкой")
        }
        if (sidRaw != expectedSnapshotId) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "snapshot_id не совпал")
        }
        if (!json.has("packages_acked")) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Нет packages_acked")
        }
        val ackedRaw = json.get("packages_acked")
        if (ackedRaw !is Int && ackedRaw !is Long) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "packages_acked должен быть целым")
        }
        val packagesAcked = when (ackedRaw) {
            is Int -> ackedRaw
            is Long -> {
                if (ackedRaw > Int.MAX_VALUE || ackedRaw < 0L) {
                    throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "packages_acked вне диапазона")
                }
                ackedRaw.toInt()
            }
            else -> throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "packages_acked должен быть целым")
        }
        if (packagesAcked < 0) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "packages_acked отрицательный")
        }
        var alreadyAcked = false
        if (json.has("already_acked")) {
            val aa = json.get("already_acked")
            if (aa !is Boolean) {
                throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "already_acked должен быть boolean")
            }
            alreadyAcked = aa
        }
        return SnapshotAckResponse(
            snapshotId = sidRaw,
            packagesAcked = packagesAcked,
            alreadyAcked = alreadyAcked
        )
    }

    fun parseChunkResponse(json: JSONObject): SnapshotChunkResponse {
        val payload = json.optJSONObject("payload")
            ?: throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Нет payload части")
        return SnapshotChunkResponse(
            snapshotId = json.getString("snapshot_id"),
            chunkIndex = json.getInt("chunk_index"),
            chunkCount = json.getInt("chunk_count"),
            sha256 = json.getString("sha256"),
            protocolVersion = json.optInt("protocol_version", -1),
            payload = parseChunkPayload(payload)
        )
    }

    fun parseChunkPayload(json: JSONObject): FullSnapshot {
        val projects = mutableListOf<SnapshotProject>()
        val tasks = mutableListOf<SnapshotTask>()
        val arrP = json.getJSONArray("projects")
        for (i in 0 until arrP.length()) {
            val o = arrP.getJSONObject(i)
            projects += SnapshotProject(
                syncUuid = o.getString("sync_uuid"),
                name = o.getString("name"),
                description = o.getString("description"),
                archived = o.getBoolean("archived"),
                createdAtUtc = o.getString("created_at"),
                versionHash = o.getString("version_hash")
            )
        }
        val arrT = json.getJSONArray("tasks")
        for (i in 0 until arrT.length()) {
            val o = arrT.getJSONObject(i)
            tasks += SnapshotTask(
                syncUuid = o.getString("sync_uuid"),
                title = o.getString("title"),
                description = o.getString("description"),
                projectUuid = if (o.isNull("project_uuid")) null else o.getString("project_uuid"),
                priority = o.getString("priority"),
                dueAtUtc = if (o.isNull("due_at")) null else o.getString("due_at"),
                status = o.getString("status"),
                createdAtUtc = o.getString("created_at"),
                versionHash = o.getString("version_hash")
            )
        }
        return FullSnapshot(
            snapshotKind = json.getString("snapshot_kind"),
            protocolVersion = json.getInt("protocol_version"),
            createdAtUtc = json.getString("created_at"),
            projects = projects,
            tasks = tasks
        )
    }

    fun mergeChunks(chunks: List<FullSnapshot>): FullSnapshot {
        require(chunks.isNotEmpty()) { "empty chunks" }
        val first = chunks.first()
        return FullSnapshot(
            snapshotKind = first.snapshotKind,
            protocolVersion = first.protocolVersion,
            createdAtUtc = first.createdAtUtc,
            projects = chunks.flatMap { it.projects },
            tasks = chunks.flatMap { it.tasks }
        )
    }
}

object FullSnapshotValidator {
    private val PRIORITIES = setOf("low", "normal", "high")
    private val STATUSES = setOf("open", "done")
    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    /** Верхние пределы, согласованные с лимитом тела ~1 MiB и чанками. */
    const val MAX_PROJECTS = 50_000
    const val MAX_TASKS = 200_000
    const val MAX_BYTE_SIZE = 50_000_000
    const val MAX_CHUNK_COUNT = 512

    fun validateInfo(info: SnapshotInfo) {
        requireUuid(info.snapshotId, "snapshot_id")
        if (info.createdAtUtc.isBlank() || VersionHash.parseUtc(info.createdAtUtc) == null) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный created_at снимка")
        }
        if (info.projectCount < 0 || info.projectCount > MAX_PROJECTS) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный project_count")
        }
        if (info.taskCount < 0 || info.taskCount > MAX_TASKS) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный task_count")
        }
        if (info.byteSize <= 0 || info.byteSize > MAX_BYTE_SIZE) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный byte_size")
        }
        if (info.chunkCount <= 0 || info.chunkCount > MAX_CHUNK_COUNT) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный chunk_count")
        }
        if (!SHA256_HEX.matches(info.sha256)) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Некорректный sha256")
        }
    }

    fun validateChunkMeta(info: SnapshotInfo, chunk: SnapshotChunkResponse, expectedIndex: Int) {
        if (chunk.protocolVersion != VersionHash.PROTOCOL_VERSION) {
            throw SyncEngineException(SyncErrorCodes.UNSUPPORTED_PROTOCOL, "Несовместимая версия части")
        }
        if (chunk.snapshotId != info.snapshotId) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "snapshot_id части не совпал")
        }
        if (chunk.chunkIndex != expectedIndex) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "chunk_index не совпал")
        }
        if (chunk.chunkCount != info.chunkCount) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "chunk_count части не совпал")
        }
        if (!chunk.sha256.equals(info.sha256, ignoreCase = true)) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "sha256 части не совпал")
        }
        if (chunk.payload.protocolVersion != VersionHash.PROTOCOL_VERSION) {
            throw SyncEngineException(SyncErrorCodes.UNSUPPORTED_PROTOCOL, "Несовместимая версия payload")
        }
        if (chunk.payload.createdAtUtc != info.createdAtUtc) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "created_at частей различается")
        }
        if (chunk.payload.snapshotKind != SNAPSHOT_KIND_PC_PRIMARY) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Неверный snapshot_kind части")
        }
    }

    fun validateMergedAgainstInfo(snapshot: FullSnapshot, info: SnapshotInfo) {
        validate(snapshot, info.sha256)
        if (snapshot.projects.size != info.projectCount) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Число проектов не совпало")
        }
        if (snapshot.tasks.size != info.taskCount) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Число задач не совпало")
        }
        val actualBytes = VersionHash.canonicalJson(snapshot.contentForHash()).toByteArray(Charsets.UTF_8).size
        if (actualBytes != info.byteSize) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "byte_size не совпал")
        }
        val projectIds = snapshot.projects.map { it.syncUuid }
        if (projectIds.size != projectIds.toSet().size) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Повтор UUID проекта")
        }
        val taskIds = snapshot.tasks.map { it.syncUuid }
        if (taskIds.size != taskIds.toSet().size) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Повтор UUID задачи")
        }
    }

    fun validate(snapshot: FullSnapshot, expectedSha256: String) {
        if (snapshot.protocolVersion != VersionHash.PROTOCOL_VERSION) {
            throw SyncEngineException(SyncErrorCodes.UNSUPPORTED_PROTOCOL, "Несовместимая версия снимка")
        }
        if (snapshot.snapshotKind != SNAPSHOT_KIND_PC_PRIMARY) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный вид снимка")
        }
        if (snapshot.createdAtUtc.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет created_at снимка")
        }
        VersionHash.parseUtc(snapshot.createdAtUtc)
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный created_at снимка")

        val projectUuids = mutableSetOf<String>()
        snapshot.projects.forEach { p ->
            requireUuid(p.syncUuid, "sync_uuid")
            if (!projectUuids.add(p.syncUuid)) {
                throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Повтор UUID проекта")
            }
            if (p.name.isBlank()) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет name")
            }
            VersionHash.parseUtc(p.createdAtUtc)
                ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный created_at")
            val computed = VersionHash.versionHash(p.payloadMap())
            if (computed != p.versionHash) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Хеш проекта не совпал")
            }
        }

        val taskUuids = mutableSetOf<String>()
        snapshot.tasks.forEach { t ->
            requireUuid(t.syncUuid, "sync_uuid")
            if (!taskUuids.add(t.syncUuid)) {
                throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Повтор UUID задачи")
            }
            if (t.title.isBlank()) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет title")
            }
            if (t.priority !in PRIORITIES) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный priority")
            }
            if (t.status !in STATUSES) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный status")
            }
            VersionHash.parseUtc(t.createdAtUtc)
                ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный created_at")
            t.dueAtUtc?.let {
                if (it.isBlank()) {
                    throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Пустой due_at")
                }
                VersionHash.parseUtc(it)
                    ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный due_at")
            }
            t.projectUuid?.let { ref ->
                if (ref.isBlank()) {
                    throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Пустой project_uuid")
                }
                requireUuid(ref, "project_uuid")
                if (ref !in projectUuids) {
                    throw SyncEngineException(
                        SyncErrorCodes.UNRESOLVED_DEPENDENCY,
                        "Задача ссылается на отсутствующий проект"
                    )
                }
            }
            val computed = VersionHash.versionHash(t.payloadMap())
            if (computed != t.versionHash) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Хеш задачи не совпал")
            }
        }

        val actual = snapshot.sha256()
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Контрольная сумма снимка не совпала")
        }
    }

    private fun requireUuid(value: String, field: String) {
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "$field должен быть UUID")
        }
    }
}
