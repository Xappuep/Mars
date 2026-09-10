package com.mars.planner.sync

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Канонический JSON и SHA-256 версии сущности — совместимы с
 * `rubezh.sync.protocol.canonical_json` / `version_hash`.
 */
object VersionHash {
    const val PROTOCOL_VERSION = 1

    private val utcSecondsFmt: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendOffset("+HH:MM", "+00:00")
        .toFormatter()
        .withZone(ZoneOffset.UTC)
    private val utcMicrosFmt: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 6, 6, true)
        .appendOffset("+HH:MM", "+00:00")
        .toFormatter()
        .withZone(ZoneOffset.UTC)

    /**
     * Python-совместимое UTC-представление:
     * - `+00:00`, не `Z`;
     * - без дробной части при нулевых микросекундах;
     * - ровно 6 цифр при ненулевых микросекундах.
     */
    fun formatUtc(epochMillis: Long): String {
        val micros = Math.floorMod(epochMillis, 1000L) * 1000L
        val instant = Instant.ofEpochMilli(epochMillis)
        return if (micros == 0L) utcSecondsFmt.format(instant) else utcMicrosFmt.format(instant)
    }

    fun parseUtc(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return OffsetDateTime.parse(value.replace(" ", "T")).toInstant().toEpochMilli()
    }

    fun projectPayload(
        syncUuid: String,
        name: String,
        description: String,
        archived: Boolean,
        createdAtUtc: String
    ): Map<String, Any?> = linkedMapOf(
        "archived" to archived,
        "created_at" to createdAtUtc,
        "description" to description,
        "entity_type" to "project",
        "name" to name,
        "sync_uuid" to syncUuid
    )

    fun taskPayload(
        syncUuid: String,
        title: String,
        description: String,
        projectUuid: String?,
        priority: String,
        dueAtUtc: String?,
        status: String,
        createdAtUtc: String
    ): Map<String, Any?> = linkedMapOf(
        "created_at" to createdAtUtc,
        "description" to description,
        "due_at" to dueAtUtc,
        "entity_type" to "task",
        "priority" to priority,
        "project_uuid" to projectUuid,
        "status" to status,
        "sync_uuid" to syncUuid,
        "title" to title
    )

    fun canonicalJson(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> {
            val d = value.toDouble()
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        is String -> jsonEscape(value)
        is Map<*, *> -> {
            val keys = value.keys.map { it.toString() }.sorted()
            keys.joinToString(",", "{", "}") { k ->
                "${jsonEscape(k)}:${canonicalJson(value[k])}"
            }
        }
        is List<*> -> value.joinToString(",", "[", "]") { canonicalJson(it) }
        is JSONObject -> canonicalJson(jsonObjectToMap(value))
        is JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) list += value.opt(i)
            canonicalJson(list)
        }
        else -> jsonEscape(value.toString())
    }

    fun versionHash(payload: Map<String, Any?>): String {
        val bytes = canonicalJson(payload).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun versionHashFromJsonObject(obj: JSONObject): String =
        versionHash(jsonObjectToMap(obj))

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        val keys = obj.keys().asSequence().toList().sorted()
        for (k in keys) {
            map[k] = when (val v = obj.opt(k)) {
                JSONObject.NULL, null -> null
                is JSONObject -> jsonObjectToMap(v)
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until v.length()) {
                        val item = v.opt(i)
                        list += when (item) {
                            JSONObject.NULL, null -> null
                            is JSONObject -> jsonObjectToMap(item)
                            else -> item
                        }
                    }
                    list
                }
                else -> v
            }
        }
        return map
    }

    private fun jsonEscape(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u%04x".format(ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
