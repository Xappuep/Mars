package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import com.mars.planner.export.toAppSettings
import com.mars.planner.export.toDto
import org.junit.Test

/**
 * Совместимость внутреннего JSON-кодека после удаления мотиватора и ручного импорта.
 */
class Stage9Part2BackupCompatTest {

    @Test
    fun fromJsonIgnoresLegacyMotivatorModeField() {
        val json = """
            {
              "version": 2,
              "exportedAt": 1726000000000,
              "projects": [
                {
                  "syncUuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "name": "Дом",
                  "description": "",
                  "archived": false,
                  "createdAt": 1,
                  "updatedAt": 1
                }
              ],
              "tasks": [
                {
                  "syncUuid": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "title": "Купить хлеб",
                  "description": "",
                  "projectSyncUuid": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "priority": "normal",
                  "dueAtEpochMillis": null,
                  "status": "open",
                  "createdAt": 1,
                  "updatedAt": 1
                }
              ],
              "settings": {
                "motivatorMode": "strict",
                "morningReminderEnabled": true,
                "morningReminderHour": 8,
                "morningReminderMinute": 30,
                "eveningReminderEnabled": false,
                "eveningReminderHour": 21,
                "eveningReminderMinute": 0,
                "defaultSnoozeMinutes": 15,
                "userName": "Михаил",
                "theme": "orbit",
                "effectIntensity": "normal",
                "reduceAnimations": false
              }
            }
        """.trimIndent()

        val payload = BackupCodec.fromJson(json)
        assertThat(payload.projects).hasSize(1)
        assertThat(payload.tasks).hasSize(1)
        assertThat(payload.tasks.single().syncUuid).isEqualTo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        assertThat(payload.settings).isNotNull()
        val settings = payload.settings!!.toAppSettings()
        assertThat(settings.userName).isEqualTo("Михаил")
        assertThat(settings.morningReminderHour).isEqualTo(8)
        assertThat(settings.defaultSnoozeMinutes).isEqualTo(15)
        // Новые SettingsDto не содержат motivatorMode — поле из JSON просто отброшено Gson.
        val roundTrip = BackupCodec.toJson(
            tasks = listOf(
                TaskItem(
                    syncUuid = payload.tasks.single().syncUuid,
                    title = payload.tasks.single().title,
                    projectSyncUuid = payload.tasks.single().projectSyncUuid,
                    status = TaskStatus.OPEN,
                    createdAt = 1,
                    updatedAt = 1
                )
            ),
            projects = listOf(
                ProjectItem(
                    syncUuid = payload.projects.single().syncUuid,
                    name = payload.projects.single().name,
                    createdAt = 1,
                    updatedAt = 1
                )
            ),
            settings = AppSettings(userName = "Михаил")
        )
        assertThat(roundTrip).doesNotContain("motivatorMode")
        assertThat(AppSettings().toDto().toString()).doesNotContain("motivator")
    }

    @Test
    fun phoneBackupStylePayloadWithoutSettingsRoundtripsIdentity() {
        val projectUuid = "cccccccc-cccc-cccc-cccc-cccccccccccc"
        val taskUuid = "dddddddd-dddd-dddd-dddd-dddddddddddd"
        val json = BackupCodec.toJson(
            tasks = listOf(
                TaskItem(
                    syncUuid = taskUuid,
                    title = "Тест",
                    projectSyncUuid = projectUuid,
                    status = TaskStatus.OPEN,
                    createdAt = 10,
                    updatedAt = 20
                )
            ),
            projects = listOf(
                ProjectItem(
                    syncUuid = projectUuid,
                    name = "Проект",
                    createdAt = 10,
                    updatedAt = 20
                )
            ),
            settings = null
        )
        val payload = BackupCodec.fromJson(json)
        assertThat(payload.settings).isNull()
        assertThat(payload.projects.single().syncUuid).isEqualTo(projectUuid)
        assertThat(payload.tasks.single().syncUuid).isEqualTo(taskUuid)
    }
}
