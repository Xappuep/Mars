package com.mars.planner.sync

import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.SyncConflictEntity
import com.mars.planner.data.db.SyncOperationEntity
import com.mars.planner.data.db.toEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.PriorityQueue

/** Ошибка применения/сборки пакета с кодом из протокола v1. */
class SyncEngineException(val code: String, message: String) : Exception(message)

/** Операция ждёт проект из того же пакета. */
private class DeferOperation : Exception(null, null, false, false)

const val ENTITY_PROJECT = "project"
const val ENTITY_TASK = "task"
const val OP_CREATE = "create"
const val OP_UPDATE = "update"
const val OP_DELETE = "delete"

/** Одна операция журнала в формате протокола v1. */
data class SyncOperation(
    val operationId: String,
    val entityType: String,
    val entityUuid: String,
    val op: String,
    val baseHash: String? = null,
    val newHash: String? = null,
    val payload: Map<String, Any?>? = null,
    val deviceId: String,
    val protocolVersion: Int = VersionHash.PROTOCOL_VERSION
) {
    val entityKey: String get() = "$entityType:$entityUuid"

    /** Проект, на который ссылается задача (для причинного порядка). */
    val projectRef: String?
        get() = if (entityType == ENTITY_TASK) payload?.get("project_uuid") as? String else null

    fun canonicalPayloadJson(): String? = payload?.let { VersionHash.canonicalJson(it) }

    fun payloadJsonObject(): JSONObject? =
        canonicalPayloadJson()?.let { JSONObject(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        put("operation_id", operationId)
        put("entity_type", entityType)
        put("entity_uuid", entityUuid)
        put("op", op)
        put("base_hash", baseHash ?: JSONObject.NULL)
        put("new_hash", newHash ?: JSONObject.NULL)
        put("payload", payloadJsonObject() ?: JSONObject.NULL)
        put("device_id", deviceId)
        put("protocol_version", protocolVersion)
    }

    companion object {
        fun fromJson(obj: JSONObject): SyncOperation {
            val operationId = obj.optString("operation_id").orBlankFail("operation_id")
            val entityType = obj.optString("entity_type").orBlankFail("entity_type")
            val entityUuid = obj.optString("entity_uuid").orBlankFail("entity_uuid")
            val op = obj.optString("op").orBlankFail("op")
            if (entityType != ENTITY_PROJECT && entityType != ENTITY_TASK) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный entity_type")
            }
            if (op != OP_CREATE && op != OP_UPDATE && op != OP_DELETE) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный op")
            }
            val version = obj.optInt("protocol_version", VersionHash.PROTOCOL_VERSION)
            if (version != VersionHash.PROTOCOL_VERSION) {
                throw SyncEngineException(
                    SyncErrorCodes.UNSUPPORTED_PROTOCOL,
                    "Версия операции не поддерживается"
                )
            }
            val payload = obj.optJSONObject("payload")?.let { jsonObjectToMap(it) }
            val deviceId = obj.optString("device_id").orBlankFail("device_id")
            return SyncOperation(
                operationId = operationId,
                entityType = entityType,
                entityUuid = entityUuid,
                op = op,
                baseHash = obj.optStringOrNull("base_hash"),
                newHash = obj.optStringOrNull("new_hash"),
                payload = payload,
                deviceId = deviceId,
                protocolVersion = version
            )
        }
    }
}

/** Пакет операций протокола v1. */
data class SyncPackage(
    val packageId: String,
    val senderDeviceId: String,
    val createdAtUtc: String,
    val operations: List<SyncOperation>,
    val protocolVersion: Int = VersionHash.PROTOCOL_VERSION
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("protocol_version", protocolVersion)
        put("package_id", packageId)
        put("sender_device_id", senderDeviceId)
        put("created_at", createdAtUtc)
        put("operations", JSONArray().also { arr -> operations.forEach { arr.put(it.toJson()) } })
    }

    fun wireSizeBytes(): Int = toJson().toString().toByteArray(Charsets.UTF_8).size

    companion object {
        fun fromJson(obj: JSONObject): SyncPackage {
            val version = obj.optInt("protocol_version", -1)
            if (version != VersionHash.PROTOCOL_VERSION) {
                throw SyncEngineException(
                    SyncErrorCodes.UNSUPPORTED_PROTOCOL,
                    "Неподдерживаемая версия протокола"
                )
            }
            val packageId = obj.optString("package_id").orBlankFail("package_id")
            val sender = obj.optString("sender_device_id").orBlankFail("sender_device_id")
            val rawOps = obj.optJSONArray("operations")
                ?: throw SyncEngineException(
                    SyncErrorCodes.INVALID_PAYLOAD,
                    "operations должен быть массивом"
                )
            val ops = ArrayList<SyncOperation>(rawOps.length())
            for (i in 0 until rawOps.length()) {
                val item = rawOps.optJSONObject(i)
                    ?: throw SyncEngineException(
                        SyncErrorCodes.INVALID_PAYLOAD,
                        "Операция должна быть объектом"
                    )
                ops += SyncOperation.fromJson(item)
            }
            return SyncPackage(
                packageId = packageId,
                senderDeviceId = sender,
                createdAtUtc = obj.optString("created_at", ""),
                operations = ops,
                protocolVersion = version
            )
        }

        /**
         * Детерминированный `package_id`: повтор отправки того же набора операций
         * даёт тот же идентификатор, поэтому ПК распознаёт дубль как идемпотентный.
         * После ответа `package_rejected` нужен новый пакет — увеличьте `attempt`.
         */
        fun packageIdFor(
            senderDeviceId: String,
            operationIds: List<String>,
            attempt: Int = 0
        ): String {
            val seed = buildString {
                append("rubezh-package|v")
                append(VersionHash.PROTOCOL_VERSION)
                append('|').append(senderDeviceId)
                append('|').append(attempt)
                append('|').append(operationIds.joinToString(","))
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(Charsets.UTF_8))
            return uuidFromBytes(digest)
        }
    }
}

/** Причинный порядок операций внутри пакета. Чистая логика — тестируется на JVM. */
object CausalOrder {

    /**
     * Стабильная топологическая сортировка отдельных операций:
     * предыдущая операция той же сущности идёт раньше следующей;
     * `task:create`/`update` со ссылкой на проект ждёт `project:create` из пакета;
     * среди готовых выбирается наименьший исходный индекс.
     */
    fun order(operations: List<SyncOperation>): List<SyncOperation> {
        if (operations.isEmpty()) return emptyList()
        val n = operations.size
        val projectCreateIndex = HashMap<String, Int>()
        operations.forEachIndexed { index, op ->
            if (op.entityType == ENTITY_PROJECT && op.op == OP_CREATE) {
                projectCreateIndex[op.entityUuid] = index
            }
        }

        val predecessors = Array(n) { HashSet<Int>() }
        val successors = Array(n) { HashSet<Int>() }
        fun addEdge(before: Int, after: Int) {
            if (before == after) return
            if (predecessors[after].add(before)) successors[before].add(after)
        }

        val lastByEntity = HashMap<String, Int>()
        operations.forEachIndexed { index, op ->
            lastByEntity.put(op.entityKey, index)?.let { addEdge(it, index) }
            if (op.entityType != ENTITY_TASK) return@forEachIndexed
            if (op.op != OP_CREATE && op.op != OP_UPDATE) return@forEachIndexed
            val ref = op.projectRef ?: return@forEachIndexed
            projectCreateIndex[ref]?.let { addEdge(it, index) }
        }

        val indegree = IntArray(n) { predecessors[it].size }
        val ready = PriorityQueue<Int>()
        for (i in 0 until n) if (indegree[i] == 0) ready += i
        val ordered = ArrayList<SyncOperation>(n)
        while (ready.isNotEmpty()) {
            val i = ready.poll() ?: break
            ordered += operations[i]
            for (j in successors[i]) {
                indegree[j] -= 1
                if (indegree[j] == 0) ready += j
            }
        }
        if (ordered.size != n) {
            throw SyncEngineException(
                SyncErrorCodes.UNRESOLVED_DEPENDENCY,
                "Цикл или неразрешимый порядок операций в пакете"
            )
        }
        return ordered
    }

    /**
     * Префикс топологического порядка, укладывающийся в лимит размера тела.
     * Префикс замкнут по зависимостям, поэтому пакет остаётся применимым.
     */
    fun trimToWireLimit(
        operations: List<SyncOperation>,
        senderDeviceId: String,
        maxBytes: Int
    ): List<SyncOperation> {
        if (operations.isEmpty()) return emptyList()
        var kept = operations
        while (kept.isNotEmpty()) {
            val probe = SyncPackage(
                packageId = SyncPackage.packageIdFor(senderDeviceId, kept.map { it.operationId }),
                senderDeviceId = senderDeviceId,
                createdAtUtc = VersionHash.formatUtc(0L),
                operations = kept
            )
            if (probe.wireSizeBytes() <= maxBytes) return kept
            kept = kept.dropLast(1)
        }
        throw SyncEngineException(
            SyncErrorCodes.REQUEST_TOO_LARGE,
            "Одна операция не укладывается в лимит размера пакета"
        )
    }
}

data class ApplyStats(
    val added: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val conflicts: Int = 0,
    val skipped: Int = 0
)

sealed class ApplyOutcome {
    /** Безопасные операции применены; конфликты записаны для решения пользователем. */
    data class Applied(val stats: ApplyStats) : ApplyOutcome()

    /** Пакет с этим `package_id` уже применялся — повтор идемпотентен. */
    data object AlreadyApplied : ApplyOutcome()

    /** Ничего не применено, транзакция откатана. */
    data class Rejected(val code: String, val detail: String) : ApplyOutcome()
}

/**
 * Сборка исходящих и применение входящих пакетов протокола v1.
 * Все записи идут через [PlannerRepository], конфликты не затирают локальные данные.
 */
class PackageEngine(
    private val repo: PlannerRepository,
    private val deviceIdProvider: () -> String,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Пакет из неподтверждённых операций журнала в причинном порядке,
     * либо null, если отправлять нечего.
     *
     * @param attempt увеличивается после ответа `package_rejected`, чтобы получить новый `package_id`.
     */
    suspend fun buildOutgoingPackage(
        attempt: Int = 0,
        maxBytes: Int = MAX_PACKAGE_BYTES
    ): SyncPackage? {
        val sender = deviceIdProvider()
        if (sender.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.NOT_PAIRED, "Устройство не сопряжено")
        }
        val pending = repo.pendingOperations()
        if (pending.isEmpty()) return null
        // device_id каждой операции должен совпадать с устройством Bearer-токена.
        val operations = pending.map { it.toSyncOperation(sender) }
        val ordered = CausalOrder.trimToWireLimit(
            CausalOrder.order(operations),
            sender,
            maxBytes
        )
        if (ordered.isEmpty()) return null
        return SyncPackage(
            packageId = SyncPackage.packageIdFor(sender, ordered.map { it.operationId }, attempt),
            senderDeviceId = sender,
            createdAtUtc = VersionHash.formatUtc(clock()),
            operations = ordered
        )
    }

    /** ПК принял пакет — снимаем операции с отправки. */
    suspend fun onPushAccepted(pkg: SyncPackage) {
        repo.ackOperations(pkg.operations.map { it.operationId })
    }

    /**
     * Применяет входящий пакет одной транзакцией: безопасные операции пишутся,
     * спорные попадают в журнал конфликтов, ошибка проверки откатывает весь пакет.
     */
    suspend fun applyIncomingPackage(pkg: SyncPackage): ApplyOutcome {
        if (pkg.packageId.isBlank()) {
            return ApplyOutcome.Rejected(SyncErrorCodes.INVALID_PAYLOAD, "Нет package_id")
        }
        repo.getAppliedPackage(pkg.packageId)?.let { known ->
            return if (known.status == STATUS_REJECTED) {
                ApplyOutcome.Rejected(
                    SyncErrorCodes.PACKAGE_REJECTED,
                    "Пакет ранее отклонён, нужен новый package_id"
                )
            } else {
                ApplyOutcome.AlreadyApplied
            }
        }
        var stats = ApplyStats()
        return try {
            validateIncoming(pkg)
            repo.withTxn {
                stats = applyOperations(pkg)
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_APPLIED)
            }
            ApplyOutcome.Applied(stats)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: SyncEngineException) {
            // Транзакция уже откатилась; фиксируем только факт отклонения package_id.
            runCatching {
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_REJECTED)
            }
            ApplyOutcome.Rejected(e.code, e.message ?: e.code)
        } catch (e: org.json.JSONException) {
            runCatching {
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_REJECTED)
            }
            ApplyOutcome.Rejected(SyncErrorCodes.INVALID_PAYLOAD, e.message ?: "invalid_payload")
        } catch (e: java.time.format.DateTimeParseException) {
            runCatching {
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_REJECTED)
            }
            ApplyOutcome.Rejected(SyncErrorCodes.INVALID_PAYLOAD, e.message ?: "invalid_payload")
        } catch (e: IllegalArgumentException) {
            runCatching {
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_REJECTED)
            }
            ApplyOutcome.Rejected(SyncErrorCodes.INVALID_PAYLOAD, e.message ?: "invalid_payload")
        } catch (e: Exception) {
            runCatching {
                repo.markPackageApplied(pkg.packageId, pkg.senderDeviceId, STATUS_REJECTED)
            }
            ApplyOutcome.Rejected(
                SyncErrorCodes.INTERNAL_ERROR,
                e.message ?: SyncErrorCodes.INTERNAL_ERROR
            )
        }
    }

    private fun validateIncoming(pkg: SyncPackage) {
        IncomingOpValidator.validatePackage(pkg)
    }

    private suspend fun applyOperations(pkg: SyncPackage): ApplyStats {
        var added = 0
        var updated = 0
        var deleted = 0
        var conflicts = 0
        var skipped = 0

        // Стабильный планировщик по исходным индексам: после каждого успешного
        // применения просмотр начинается заново, отложенная операция удерживает
        // только хвост своей сущности.
        val remaining = pkg.operations.toMutableList()
        var safety = remaining.size * (remaining.size + 2) + 1
        while (remaining.isNotEmpty()) {
            if (safety-- <= 0) {
                throw SyncEngineException(
                    SyncErrorCodes.UNRESOLVED_DEPENDENCY,
                    "Неразрешённая ссылка на проект в пакете"
                )
            }
            var progressed = false
            for (i in remaining.indices) {
                val op = remaining[i]
                val blockedByOwnTail = (0 until i).any { remaining[it].entityKey == op.entityKey }
                if (blockedByOwnTail) continue
                val effect = try {
                    applyOne(op, pkg.packageId)
                } catch (ignored: DeferOperation) {
                    continue
                }
                when (effect) {
                    OpEffect.ADDED -> added++
                    OpEffect.UPDATED -> updated++
                    OpEffect.DELETED -> deleted++
                    OpEffect.CONFLICT -> conflicts++
                    OpEffect.SKIPPED -> skipped++
                }
                repo.insertSeenRemoteOperation(op.toJournalEntity(pkg.senderDeviceId))
                remaining.removeAt(i)
                progressed = true
                break
            }
            if (!progressed) {
                throw SyncEngineException(
                    SyncErrorCodes.UNRESOLVED_DEPENDENCY,
                    "Неразрешённая ссылка на проект в пакете"
                )
            }
        }
        return ApplyStats(added, updated, deleted, conflicts, skipped)
    }

    private enum class OpEffect { ADDED, UPDATED, DELETED, CONFLICT, SKIPPED }

    private suspend fun applyOne(op: SyncOperation, packageId: String): OpEffect {
        if (repo.getOperation(op.operationId) != null) return OpEffect.SKIPPED

        val (localPayload, localHash) = localVersion(op.entityType, op.entityUuid)
        val tombstone = repo.tombstone(op.entityUuid)?.takeIf { it.entityType == op.entityType }

        return when (op.op) {
            OP_CREATE -> when {
                localHash != null && localHash == op.newHash -> OpEffect.SKIPPED
                localHash != null || tombstone != null -> {
                    recordConflict(op, packageId, localPayload, remoteDeleted = false)
                    OpEffect.CONFLICT
                }
                else -> {
                    upsert(op)
                    OpEffect.ADDED
                }
            }

            OP_DELETE -> when {
                localHash == null && tombstone != null -> OpEffect.SKIPPED
                localHash == null -> OpEffect.SKIPPED
                localHash == op.baseHash || localHash == op.newHash -> {
                    delete(op)
                    OpEffect.DELETED
                }
                else -> {
                    recordConflict(op, packageId, localPayload, remoteDeleted = true)
                    OpEffect.CONFLICT
                }
            }

            else -> when {
                // Удаление против изменения — конфликт, ничего не затираем.
                localHash == null && tombstone != null -> {
                    recordConflict(op, packageId, localPayload = null, remoteDeleted = false)
                    OpEffect.CONFLICT
                }
                localHash == null -> {
                    upsert(op)
                    OpEffect.ADDED
                }
                localHash == op.newHash -> OpEffect.SKIPPED
                localHash == op.baseHash -> {
                    upsert(op)
                    OpEffect.UPDATED
                }
                else -> {
                    recordConflict(op, packageId, localPayload, remoteDeleted = false)
                    OpEffect.CONFLICT
                }
            }
        }
    }

    private suspend fun localVersion(
        entityType: String,
        entityUuid: String
    ): Pair<Map<String, Any?>?, String?> = when (entityType) {
        ENTITY_PROJECT -> {
            val entity = repo.getProjectByUuid(entityUuid)?.toEntity()
            if (entity == null) null to null
            else repo.projectPayload(entity) to repo.hashProject(entity)
        }
        else -> {
            val entity = repo.getTaskByUuid(entityUuid)?.toEntity()
            if (entity == null) null to null
            else repo.taskPayload(entity) to repo.hashTask(entity)
        }
    }

    private suspend fun upsert(op: SyncOperation) {
        val payload = op.payload
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет payload")
        val json = JSONObject(VersionHash.canonicalJson(payload))
        when (op.entityType) {
            ENTITY_PROJECT -> repo.applyProjectPayload(json)
            else -> {
                val ref = payload["project_uuid"] as? String
                if (!ref.isNullOrBlank() && repo.getProjectByUuid(ref) == null) {
                    throw DeferOperation()
                }
                repo.applyTaskPayload(json)
            }
        }
        repo.clearTombstone(op.entityUuid)
        val localHash = when (op.entityType) {
            ENTITY_PROJECT -> repo.projectHashByUuid(op.entityUuid)
            else -> repo.taskHashByUuid(op.entityUuid)
        }
        if (localHash == null || localHash != op.newHash) {
            throw SyncEngineException(
                SyncErrorCodes.INTERNAL_ERROR,
                "Локальный хеш не совпал с new_hash после применения"
            )
        }
    }

    private suspend fun delete(op: SyncOperation) {
        when (op.entityType) {
            ENTITY_PROJECT -> repo.deleteProjectByUuidSilent(op.entityUuid)
            else -> repo.deleteTaskByUuidSilent(op.entityUuid)
        }
    }

    private suspend fun recordConflict(
        op: SyncOperation,
        packageId: String,
        localPayload: Map<String, Any?>?,
        remoteDeleted: Boolean
    ) {
        val remoteJson = if (remoteDeleted || op.op == OP_DELETE) {
            VersionHash.canonicalJson(
                mapOf(
                    "entity_type" to op.entityType,
                    "sync_uuid" to op.entityUuid,
                    "deleted" to true
                )
            )
        } else {
            op.canonicalPayloadJson()
        }
        val localJson = localPayload?.let { VersionHash.canonicalJson(it) }
            ?: VersionHash.canonicalJson(
                mapOf(
                    "entity_type" to op.entityType,
                    "sync_uuid" to op.entityUuid,
                    "deleted" to true
                )
            )
        repo.insertConflict(
            SyncConflictEntity(
                entityType = op.entityType,
                entityUuid = op.entityUuid,
                localPayloadJson = localJson,
                remotePayloadJson = remoteJson,
                localOp = if (localPayload == null) OP_DELETE else OP_UPDATE,
                remoteOp = op.op,
                packageId = packageId,
                createdAt = clock()
            )
        )
    }

    private fun SyncOperationEntity.toSyncOperation(senderDeviceId: String): SyncOperation =
        SyncOperation(
            operationId = operationId,
            entityType = entityType,
            entityUuid = entityUuid,
            op = op,
            baseHash = baseHash,
            newHash = newHash,
            payload = payloadJson?.takeIf { it.isNotBlank() }
                ?.let { jsonObjectToMap(JSONObject(it)) },
            deviceId = senderDeviceId,
            protocolVersion = protocolVersion
        )

    /** Отметка «операция уже виделась» — повтор того же `operation_id` идемпотентен. */
    private fun SyncOperation.toJournalEntity(senderDeviceId: String): SyncOperationEntity =
        SyncOperationEntity(
            operationId = operationId,
            entityType = entityType,
            entityUuid = entityUuid,
            op = op,
            baseHash = baseHash,
            newHash = newHash,
            payloadJson = canonicalPayloadJson(),
            deviceId = deviceId.ifBlank { senderDeviceId },
            protocolVersion = protocolVersion,
            causalSeq = 0L,
            createdAt = clock(),
            acked = true
        )

    companion object {
        /** 1 MiB минус запас на конверт `{"package": ...}` и заголовки. */
        const val MAX_PACKAGE_BYTES = 1_040_000

        const val STATUS_APPLIED = "applied"
        const val STATUS_REJECTED = "rejected"
    }
}

internal fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    for (key in obj.keys()) out[key] = unwrapJson(obj.opt(key))
    return out
}

private fun unwrapJson(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> jsonObjectToMap(value)
    is JSONArray -> (0 until value.length()).map { unwrapJson(value.opt(it)) }
    else -> value
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun String?.orBlankFail(field: String): String =
    this?.takeIf { it.isNotBlank() }
        ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет поля $field")

private fun uuidFromBytes(bytes: ByteArray): String {
    val b = bytes.copyOf(16)
    b[6] = ((b[6].toInt() and 0x0F) or 0x50).toByte()
    b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
    val hex = b.joinToString("") { "%02x".format(it) }
    return buildString {
        append(hex, 0, 8).append('-')
        append(hex, 8, 12).append('-')
        append(hex, 12, 16).append('-')
        append(hex, 16, 20).append('-')
        append(hex, 20, 32)
    }
}
