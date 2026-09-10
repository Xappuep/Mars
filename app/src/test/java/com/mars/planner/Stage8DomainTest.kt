package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.TaskRules
import com.mars.planner.domain.logic.TodayTasksSelector
import com.mars.planner.domain.model.AppTheme
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.sync.TlsFingerprint
import com.mars.planner.sync.VersionHash
import com.mars.planner.sync.CausalOrder
import com.mars.planner.sync.SyncOperation
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class Stage8DomainTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val zone = ZoneOffset.UTC

    private fun millis(day: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        day.atTime(hour, minute).toInstant(zone).toEpochMilli()

    @Test
    fun legacyStatusesMapCorrectly() {
        assertThat(TaskStatus.fromLegacyKey("new")).isEqualTo(TaskStatus.OPEN)
        assertThat(TaskStatus.fromLegacyKey("in_progress")).isEqualTo(TaskStatus.OPEN)
        assertThat(TaskStatus.fromLegacyKey("postponed")).isEqualTo(TaskStatus.OPEN)
        assertThat(TaskStatus.fromLegacyKey("not_done")).isEqualTo(TaskStatus.OPEN)
        assertThat(TaskStatus.fromLegacyKey("done")).isEqualTo(TaskStatus.DONE)
        assertThat(TaskStatus.fromLegacyKey("cancelled")).isEqualTo(TaskStatus.DONE)
    }

    @Test
    fun todayShowsOpenDueToday() {
        val task = TaskItem(
            syncUuid = UUID.randomUUID().toString(),
            title = "сегодня",
            status = TaskStatus.OPEN,
            dueAtEpochMillis = millis(today)
        )
        assertThat(TodayTasksSelector.belongsOnToday(task, today, zone)).isTrue()
    }

    @Test
    fun todayShowsOverdueOpen() {
        val task = TaskItem(
            syncUuid = UUID.randomUUID().toString(),
            title = "просрочено",
            status = TaskStatus.OPEN,
            dueAtEpochMillis = millis(today.minusDays(2))
        )
        val selected = TodayTasksSelector.select(listOf(task), today, zone)
        assertThat(selected).hasSize(1)
        assertThat(TaskRules.isOverdue(task, today, zone)).isTrue()
        val summary = DaySummaryCalculator.summarize(selected, today, zone)
        assertThat(summary.overdue).isEqualTo(1)
    }

    @Test
    fun doneOverdueNotOnToday() {
        val task = TaskItem(
            syncUuid = UUID.randomUUID().toString(),
            title = "готово",
            status = TaskStatus.DONE,
            dueAtEpochMillis = millis(today.minusDays(1))
        )
        assertThat(TaskRules.isOverdue(task, today, zone)).isFalse()
        assertThat(TodayTasksSelector.belongsOnToday(task, today, zone)).isFalse()
    }

    @Test
    fun futureExcludedFromToday() {
        val task = TaskItem(
            syncUuid = UUID.randomUUID().toString(),
            title = "завтра",
            status = TaskStatus.OPEN,
            dueAtEpochMillis = millis(today.plusDays(1))
        )
        assertThat(TodayTasksSelector.belongsOnToday(task, today, zone)).isFalse()
    }

    @Test
    fun versionHashMatchesPythonCanonicalOrder() {
        val payload = VersionHash.taskPayload(
            syncUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            title = "Купить краску",
            description = "",
            projectUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            priority = "normal",
            dueAtUtc = "2026-09-10T15:00:00+00:00",
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00+00:00"
        )
        val canonical = VersionHash.canonicalJson(payload)
        // Ключи отсортированы, без пробелов — как json.dumps(..., sort_keys=True, separators=(',',':'))
        assertThat(canonical).startsWith("{\"created_at\":")
        assertThat(canonical).contains("\"entity_type\":\"task\"")
        // Разделители без пробелов; пробелы внутри значений сохраняются как есть.
        assertThat(canonical).doesNotContain("\": ")
        assertThat(canonical).doesNotContain(", \"")
        val hash = VersionHash.versionHash(payload)
        assertThat(hash).hasLength(64)
        assertThat(VersionHash.versionHash(payload)).isEqualTo(hash)
    }

    @Test
    fun projectHashStable() {
        val p = VersionHash.projectPayload(
            syncUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        assertThat(VersionHash.versionHash(p)).isEqualTo(VersionHash.versionHash(p.toMap()))
    }

    @Test
    fun rubezhThemesPresent() {
        assertThat(AppTheme.entries.map { it.key }).containsExactly(
            "orbit", "nebula", "white-station", "ash-amber",
            "polar-night", "light-concrete", "shelter-terminal"
        ).inOrder()
        assertThat(AppTheme.fromKey("bunker")).isEqualTo(AppTheme.SHELTER)
    }

    @Test
    fun fingerprintConstantTimeCompare() {
        val a = "aabbcc"
        val b = "aabbcc"
        val c = "aabbcd"
        assertThat(TlsFingerprint.constantTimeEquals(a, b)).isTrue()
        assertThat(TlsFingerprint.constantTimeEquals(a, c)).isFalse()
    }

    @Test
    fun causalOrderPutsProjectBeforeTask() {
        val pUuid = "11111111-1111-1111-1111-111111111111"
        val tUuid = "22222222-2222-2222-2222-222222222222"
        val ops = listOf(
            SyncOperation(
                operationId = "o2",
                entityType = "task",
                entityUuid = tUuid,
                op = "create",
                baseHash = null,
                newHash = "h2",
                payload = mapOf(
                    "entity_type" to "task",
                    "sync_uuid" to tUuid,
                    "project_uuid" to pUuid,
                    "title" to "t",
                    "description" to "",
                    "priority" to "normal",
                    "due_at" to null,
                    "status" to "open",
                    "created_at" to "2026-09-01T00:00:00+00:00"
                ),
                deviceId = "d",
                protocolVersion = 1
            ),
            SyncOperation(
                operationId = "o1",
                entityType = "project",
                entityUuid = pUuid,
                op = "create",
                baseHash = null,
                newHash = "h1",
                payload = mapOf(
                    "entity_type" to "project",
                    "sync_uuid" to pUuid,
                    "name" to "P",
                    "description" to "",
                    "archived" to false,
                    "created_at" to "2026-09-01T00:00:00+00:00"
                ),
                deviceId = "d",
                protocolVersion = 1
            )
        )
        val ordered = CausalOrder.order(ops)
        assertThat(ordered.map { it.operationId }).containsExactly("o1", "o2").inOrder()
    }
}

private fun <K, V> Map<K, V>.toMap(): Map<K, V> = LinkedHashMap(this)
