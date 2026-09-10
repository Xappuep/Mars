package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.VersionHash
import org.json.JSONObject
import org.junit.Test

/**
 * Эталонные векторы `version_hash` протокола «рубеж-синхронизация» v1.
 *
 * Ожидаемые значения ниже получены независимой реализацией канонизации
 * (`json.dumps(payload, sort_keys=True, separators=(',', ':'), ensure_ascii=False)`
 * плюс SHA-256 от UTF-8) — той же, что применяет ПК-часть
 * `rubezh.sync.protocol.canonical_json` / `rubezh.sync.protocol.version_hash`.
 * Совпадение проверено на этих фиксированных полезных нагрузках; при любом
 * изменении набора полей, порядка ключей или формата времени тест обязан упасть.
 *
 * Формат времени: Android печатает Python-совместимый UTC с `+00:00`;
 * при ненулевых миллисекундах — ровно 6 цифр микросекунд.
 */
class VersionHashParityTest {

    private companion object {
        const val PROJECT_UUID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        const val TASK_UUID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"

        /** Миллисекунды 2026-09-01T11:00:00Z. */
        const val CREATED_AT_MILLIS = 1_788_260_400_000L

        /** Миллисекунды 2026-09-10T15:00:00Z. */
        const val DUE_AT_MILLIS = 1_789_052_400_000L

        /** Миллисекунды 2026-09-01T10:00:00Z. */
        const val PROJECT_CREATED_AT_MILLIS = 1_788_256_800_000L
    }

    private fun taskVector(
        title: String = "Kupit krasku",
        projectUuid: String? = PROJECT_UUID,
        dueAtUtc: String? = "2026-09-10T15:00:00+00:00",
        createdAtUtc: String = "2026-09-01T11:00:00+00:00"
    ): Map<String, Any?> = VersionHash.taskPayload(
        syncUuid = TASK_UUID,
        title = title,
        description = "",
        projectUuid = projectUuid,
        priority = "normal",
        dueAtUtc = dueAtUtc,
        status = "open",
        createdAtUtc = createdAtUtc
    )

    @Test
    fun protocolVersionIsOne() {
        assertThat(VersionHash.PROTOCOL_VERSION).isEqualTo(1)
    }

    @Test
    fun canonicalJsonSortsKeysAndOmitsWhitespace() {
        val canonical = VersionHash.canonicalJson(taskVector())

        assertThat(canonical).isEqualTo(
            "{\"created_at\":\"2026-09-01T11:00:00+00:00\"," +
                "\"description\":\"\"," +
                "\"due_at\":\"2026-09-10T15:00:00+00:00\"," +
                "\"entity_type\":\"task\"," +
                "\"priority\":\"normal\"," +
                "\"project_uuid\":\"$PROJECT_UUID\"," +
                "\"status\":\"open\"," +
                "\"sync_uuid\":\"$TASK_UUID\"," +
                "\"title\":\"Kupit krasku\"}"
        )
    }

    @Test
    fun cyrillicTitleIsHashedWithoutUnicodeEscaping() {
        val canonical = VersionHash.canonicalJson(taskVector(title = "Купить краску"))
        assertThat(canonical).contains("\"title\":\"Купить краску\"")
        assertThat(canonical).doesNotContain("\\u")

        assertThat(VersionHash.versionHash(taskVector(title = "Купить краску")))
            .isEqualTo("52a770eea46158d667d4b52ae641ebf6d5e66b74609a009cb05b2970cdb52f0d")
    }

    @Test
    fun nullFieldsStayInPayloadAsJsonNull() {
        val payload = taskVector(projectUuid = null, dueAtUtc = null)
        val canonical = VersionHash.canonicalJson(payload)

        assertThat(canonical).contains("\"due_at\":null")
        assertThat(canonical).contains("\"project_uuid\":null")
        assertThat(VersionHash.versionHash(payload))
            .isEqualTo("b30a04b012fd6448a40b7e243cc8b32fc6ede44031f88d1e6aaabd76f61d0992")
    }

    @Test
    fun projectVectorMatchesReferenceHash() {
        val payload = VersionHash.projectPayload(
            syncUuid = PROJECT_UUID,
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )

        assertThat(VersionHash.canonicalJson(payload)).isEqualTo(
            "{\"archived\":false," +
                "\"created_at\":\"2026-09-01T10:00:00+00:00\"," +
                "\"description\":\"\"," +
                "\"entity_type\":\"project\"," +
                "\"name\":\"Дом\"," +
                "\"sync_uuid\":\"$PROJECT_UUID\"}"
        )
        assertThat(VersionHash.versionHash(payload))
            .isEqualTo("d73be68fd4d4101b296959da711088031a9f4b636298e5fb7ce60474f0e89a1a")
    }

    @Test
    fun formatUtcPrintsPythonCompatibleUtcOffset() {
        assertThat(VersionHash.formatUtc(CREATED_AT_MILLIS)).isEqualTo("2026-09-01T11:00:00+00:00")
        assertThat(VersionHash.formatUtc(DUE_AT_MILLIS)).isEqualTo("2026-09-10T15:00:00+00:00")
        assertThat(VersionHash.formatUtc(0L)).isEqualTo("1970-01-01T00:00:00+00:00")
    }

    @Test
    fun formatUtcPrintsSixDigitsForNonZeroMilliseconds() {
        assertThat(VersionHash.formatUtc(CREATED_AT_MILLIS + 123L))
            .isEqualTo("2026-09-01T11:00:00.123000+00:00")
        assertThat(VersionHash.formatUtc(DUE_AT_MILLIS + 789L))
            .isEqualTo("2026-09-10T15:00:00.789000+00:00")
    }

    @Test
    fun parseUtcAcceptsBothZuluAndOffsetForms() {
        assertThat(VersionHash.parseUtc("2026-09-01T11:00:00Z")).isEqualTo(CREATED_AT_MILLIS)
        assertThat(VersionHash.parseUtc("2026-09-01T11:00:00+00:00")).isEqualTo(CREATED_AT_MILLIS)
        assertThat(VersionHash.parseUtc("")).isNull()
        assertThat(VersionHash.parseUtc(null)).isNull()
    }

    @Test
    fun wireVectorsUseTimestampsProducedByFormatUtc() {
        val task = taskVector(dueAtUtc = VersionHash.formatUtc(DUE_AT_MILLIS), createdAtUtc = VersionHash.formatUtc(CREATED_AT_MILLIS))
        assertThat(VersionHash.versionHash(task))
            .isEqualTo("37bee1c9e08b87135221e101facc0d00e55468f8c26c444b844157ea00ea67cf")

        val project = VersionHash.projectPayload(
            syncUuid = PROJECT_UUID,
            name = "Dom",
            description = "",
            archived = false,
            createdAtUtc = VersionHash.formatUtc(PROJECT_CREATED_AT_MILLIS)
        )
        assertThat(VersionHash.versionHash(project))
            .isEqualTo("3fa8a5ad6afd79254c0320e2c0b5384bb88f390abfbb524482accad35f912434")
    }

    @Test
    fun pythonReferenceVectorForPcCreatedAtMatches() {
        val payload = VersionHash.projectPayload(
            syncUuid = PROJECT_UUID,
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        assertThat(VersionHash.canonicalJson(payload))
            .isEqualTo("{\"archived\":false,\"created_at\":\"2026-09-01T10:00:00+00:00\",\"description\":\"\",\"entity_type\":\"project\",\"name\":\"Дом\",\"sync_uuid\":\"$PROJECT_UUID\"}")
        assertThat(VersionHash.versionHash(payload))
            .isEqualTo("d73be68fd4d4101b296959da711088031a9f4b636298e5fb7ce60474f0e89a1a")
    }

    @Test
    fun pythonReferenceVectorForPcCreatedAndDueMicrosMatches() {
        val payload = taskVector(
            title = "Купить краску",
            dueAtUtc = "2026-09-10T15:00:00.654321+00:00",
            createdAtUtc = "2026-09-01T11:00:00.123456+00:00"
        )
        assertThat(VersionHash.canonicalJson(payload))
            .isEqualTo("{\"created_at\":\"2026-09-01T11:00:00.123456+00:00\",\"description\":\"\",\"due_at\":\"2026-09-10T15:00:00.654321+00:00\",\"entity_type\":\"task\",\"priority\":\"normal\",\"project_uuid\":\"$PROJECT_UUID\",\"status\":\"open\",\"sync_uuid\":\"$TASK_UUID\",\"title\":\"Купить краску\"}")
        assertThat(VersionHash.versionHash(payload))
            .isEqualTo("693c8336da75b74f142146d4120055b59f1f4c9afc3c6e7edc513ac0f60b198e")
    }

    @Test
    fun androidMillisVectorMatchesPythonReference() {
        val payload = taskVector(
            title = "Купить краску",
            dueAtUtc = "2026-09-10T15:00:00.789000+00:00",
            createdAtUtc = "2026-09-01T11:00:00.123000+00:00"
        )
        assertThat(VersionHash.versionHash(payload))
            .isEqualTo("e93096e67382991010c686260b52088c678eea46e0103be4cb79a9d39c30761a")
    }

    @Test
    fun hashDoesNotDependOnInsertionOrder() {
        val ordered = taskVector()
        val shuffled = LinkedHashMap<String, Any?>()
        ordered.keys.reversed().forEach { shuffled[it] = ordered[it] }

        assertThat(VersionHash.versionHash(shuffled))
            .isEqualTo(VersionHash.versionHash(ordered))
    }

    @Test
    fun hashFromJsonObjectMatchesHashFromMap() {
        val payload = taskVector()
        val json = JSONObject(VersionHash.canonicalJson(payload))

        assertThat(VersionHash.versionHashFromJsonObject(json))
            .isEqualTo(VersionHash.versionHash(payload))
    }

    @Test
    fun anyFieldChangeChangesHash() {
        val base = VersionHash.versionHash(taskVector())

        assertThat(VersionHash.versionHash(taskVector(title = "Kupit krasky"))).isNotEqualTo(base)
        assertThat(VersionHash.versionHash(taskVector(projectUuid = null))).isNotEqualTo(base)
        assertThat(VersionHash.versionHash(taskVector(dueAtUtc = "2026-09-10T15:00:01+00:00")))
            .isNotEqualTo(base)
        assertThat(VersionHash.versionHash(taskVector(createdAtUtc = "2026-09-01T11:00:00Z")))
            .isNotEqualTo(base)
    }

    @Test
    fun hashIsLowercaseSha256Hex() {
        val hash = VersionHash.versionHash(taskVector())

        assertThat(hash).hasLength(64)
        assertThat(hash.all { it in '0'..'9' || it in 'a'..'f' }).isTrue()
    }

    @Test
    fun escapingFollowsJsonRulesForControlCharacters() {
        assertThat(VersionHash.canonicalJson("строка\nс \"кавычками\"\tи \\слешем"))
            .isEqualTo("\"строка\\nс \\\"кавычками\\\"\\tи \\\\слешем\"")
        assertThat(VersionHash.canonicalJson("\u0001")).isEqualTo("\"\\u0001\"")
    }

    @Test
    fun numbersAreCanonicalizedWithoutTrailingZero() {
        assertThat(VersionHash.canonicalJson(mapOf("n" to 1.0))).isEqualTo("{\"n\":1}")
        assertThat(VersionHash.canonicalJson(mapOf("n" to 2))).isEqualTo("{\"n\":2}")
        assertThat(VersionHash.canonicalJson(listOf(true, false, null))).isEqualTo("[true,false,null]")
    }
}
