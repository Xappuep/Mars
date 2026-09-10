package com.mars.planner.sync

import java.util.UUID

/**
 * Строгая проверка входящих операций протокола v1 до применения в локальную БД.
 * Повреждённые поля не нормализуются значениями по умолчанию.
 */
object IncomingOpValidator {

    private val HASH_HEX = Regex("^[0-9a-f]{64}$")
    private val PRIORITIES = setOf("low", "normal", "high")
    private val STATUSES = setOf("open", "done")

    fun validatePackage(pkg: SyncPackage) {
        if (pkg.protocolVersion != VersionHash.PROTOCOL_VERSION) {
            throw SyncEngineException(
                SyncErrorCodes.UNSUPPORTED_PROTOCOL,
                "Неподдерживаемая версия протокола"
            )
        }
        if (pkg.packageId.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет package_id")
        }
        requireUuid(pkg.packageId, "package_id")

        if (pkg.senderDeviceId.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет sender_device_id")
        }
        requireUuid(pkg.senderDeviceId, "sender_device_id")

        if (pkg.createdAtUtc.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет created_at пакета")
        }
        requireParseableUtc(pkg.createdAtUtc, "created_at пакета")

        pkg.operations.forEach { validateOperation(it, pkg.senderDeviceId) }
    }

    fun validateOperation(op: SyncOperation, senderDeviceId: String) {
        if (op.protocolVersion != VersionHash.PROTOCOL_VERSION) {
            throw SyncEngineException(
                SyncErrorCodes.UNSUPPORTED_PROTOCOL,
                "Версия операции не поддерживается"
            )
        }
        if (op.operationId.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет operation_id")
        }
        requireUuid(op.operationId, "operation_id")

        if (op.entityType != ENTITY_PROJECT && op.entityType != ENTITY_TASK) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный entity_type")
        }
        if (op.op != OP_CREATE && op.op != OP_UPDATE && op.op != OP_DELETE) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный op")
        }

        if (op.entityUuid.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет entity_uuid")
        }
        requireUuid(op.entityUuid, "entity_uuid")

        if (op.deviceId.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет device_id")
        }
        requireUuid(op.deviceId, "device_id")
        if (op.deviceId != senderDeviceId) {
            throw SyncEngineException(
                SyncErrorCodes.SENDER_MISMATCH,
                "device_id операции не совпадает с отправителем"
            )
        }

        when (op.op) {
            OP_DELETE -> validateDelete(op)
            else -> validateUpsert(op)
        }
    }

    private fun validateDelete(op: SyncOperation) {
        if (op.payload != null) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "delete должен иметь payload = null"
            )
        }
        if (op.newHash != null) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "delete должен иметь new_hash = null"
            )
        }
        op.baseHash?.let { requireHash(it, "base_hash") }
    }

    private fun validateUpsert(op: SyncOperation) {
        val payload = op.payload
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет payload")

        val newHash = op.newHash
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет new_hash")
        requireHash(newHash, "new_hash")
        op.baseHash?.let { requireHash(it, "base_hash") }

        when (op.entityType) {
            ENTITY_PROJECT -> validateProjectPayload(payload, op.entityUuid)
            ENTITY_TASK -> validateTaskPayload(payload, op.entityUuid)
        }

        val computed = VersionHash.versionHash(payload)
        if (computed != newHash) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "new_hash не совпадает с version_hash(payload)"
            )
        }
    }

    private fun validateProjectPayload(payload: Map<String, Any?>, entityUuid: String) {
        requireExactString(payload, "entity_type", ENTITY_PROJECT)
        val syncUuid = requireNonBlankString(payload, "sync_uuid")
        requireUuid(syncUuid, "sync_uuid")
        if (syncUuid != entityUuid) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "sync_uuid не совпадает с entity_uuid"
            )
        }
        requireNonBlankString(payload, "name")
        requireString(payload, "description") // может быть пустой
        requireBoolean(payload, "archived")
        val createdAt = requireNonBlankString(payload, "created_at")
        requireParseableUtc(createdAt, "created_at")
    }

    private fun validateTaskPayload(payload: Map<String, Any?>, entityUuid: String) {
        requireExactString(payload, "entity_type", ENTITY_TASK)
        val syncUuid = requireNonBlankString(payload, "sync_uuid")
        requireUuid(syncUuid, "sync_uuid")
        if (syncUuid != entityUuid) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "sync_uuid не совпадает с entity_uuid"
            )
        }
        requireNonBlankString(payload, "title")
        requireString(payload, "description")

        when (val projectUuid = payload["project_uuid"]) {
            null -> Unit
            is String -> {
                if (projectUuid.isBlank()) {
                    throw SyncEngineException(
                        SyncErrorCodes.INVALID_PAYLOAD,
                        "project_uuid не может быть пустой строкой"
                    )
                }
                requireUuid(projectUuid, "project_uuid")
            }
            else -> throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "project_uuid должен быть строкой или null"
            )
        }

        val priority = requireNonBlankString(payload, "priority")
        if (priority !in PRIORITIES) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный priority")
        }

        when (val dueAt = payload["due_at"]) {
            null -> Unit
            is String -> {
                if (dueAt.isBlank()) {
                    throw SyncEngineException(
                        SyncErrorCodes.INVALID_PAYLOAD,
                        "due_at не может быть пустой строкой"
                    )
                }
                requireParseableUtc(dueAt, "due_at")
            }
            else -> throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "due_at должен быть строкой или null"
            )
        }

        val status = requireNonBlankString(payload, "status")
        if (status !in STATUSES) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный status")
        }

        val createdAt = requireNonBlankString(payload, "created_at")
        requireParseableUtc(createdAt, "created_at")
    }

    private fun requireExactString(payload: Map<String, Any?>, field: String, expected: String) {
        val value = requireNonBlankString(payload, field)
        if (value != expected) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "$field должен быть \"$expected\""
            )
        }
    }

    private fun requireNonBlankString(payload: Map<String, Any?>, field: String): String {
        val value = requireString(payload, field)
        if (value.isBlank()) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет $field")
        }
        return value
    }

    private fun requireString(payload: Map<String, Any?>, field: String): String {
        if (!payload.containsKey(field)) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет $field")
        }
        val value = payload[field]
        if (value !is String) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "$field должен быть строкой"
            )
        }
        return value
    }

    private fun requireBoolean(payload: Map<String, Any?>, field: String): Boolean {
        if (!payload.containsKey(field)) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Нет $field")
        }
        val value = payload[field]
        if (value !is Boolean) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "$field должен быть Boolean"
            )
        }
        return value
    }

    private fun requireHash(value: String, field: String) {
        if (!HASH_HEX.matches(value)) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "$field должен быть SHA-256 hex (64 символа)"
            )
        }
    }

    private fun requireUuid(value: String, field: String) {
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            throw SyncEngineException(
                SyncErrorCodes.INVALID_PAYLOAD,
                "$field должен быть UUID"
            )
        }
    }

    private fun requireParseableUtc(value: String, field: String) {
        try {
            VersionHash.parseUtc(value)
                ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный $field")
        } catch (e: SyncEngineException) {
            throw e
        } catch (_: Exception) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный $field")
        }
    }
}
