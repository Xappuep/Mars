package com.mars.planner.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.db.MigrationArchiveEntity
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Часть 2: архив миграции, локальные настройки без sync-операций, резервная копия телефона.
 * Только in-memory Room / пакет debug. Рабочую базу телефона не использует.
 */
@RunWith(AndroidJUnit4::class)
class Stage9Part2LegacyCleanupInstrumentedTest {

    private lateinit var context: Context
    private lateinit var db: MarsDatabase
    private lateinit var repo: PlannerRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = MarsDatabase.buildInMemory(context)
        repo = PlannerRepository(db) { "phone-device" }
        settings = SettingsRepository(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun hasMigrationArchiveReflectsPresenceWithoutParsingJson() = runBlocking {
        assertThat(repo.hasMigrationArchive()).isFalse()
        db.syncDao().insertArchive(
            MigrationArchiveEntity(
                archiveJson = """{"huge":true,"payload":"${"x".repeat(2000)}"}""",
                reportJson = """{"projectsCreated":1}"""
            )
        )
        assertThat(repo.hasMigrationArchive()).isTrue()
        // UI: кнопка «Выгрузить архив миграции» только при true; секция скрыта при false.
    }

    @Test
    fun settingsLocalUpdateDoesNotEnqueueSyncOperations() = runBlocking {
        val before = db.syncDao().pendingOperations().size
        settings.update {
            it.copy(
                userName = "Локально",
                reduceAnimations = true,
                tasksGroupsUserConfigured = true,
                tasksGroupExpandedKeys = "p1\n__no_project__",
                tasksNoProjectPinnedTop = true
            )
        }
        val after = db.syncDao().pendingOperations().size
        assertThat(after).isEqualTo(before)
        assertThat(settings.settings.first().userName).isEqualTo("Локально")
    }

    @Test
    fun phoneBackupRoundtripPreservesUuids() = runBlocking {
        val projectUuid = UUID.randomUUID().toString()
        val taskUuid = UUID.randomUUID().toString()
        repo.saveProject(ProjectItem(syncUuid = projectUuid, name = "Тел", description = "d"))
        repo.saveTask(
            TaskItem(
                syncUuid = taskUuid,
                title = "Задача",
                projectSyncUuid = projectUuid,
                status = TaskStatus.OPEN
            )
        )

        val legacyJson = """
            {
              "version": 2,
              "exportedAt": 1,
              "projects": [{
                "syncUuid": "$projectUuid",
                "name": "Тел",
                "description": "d",
                "archived": false,
                "createdAt": 1,
                "updatedAt": 1
              }],
              "tasks": [{
                "syncUuid": "$taskUuid",
                "title": "Задача",
                "description": "",
                "projectSyncUuid": "$projectUuid",
                "priority": "normal",
                "status": "open",
                "createdAt": 1,
                "updatedAt": 1
              }],
              "settings": { "motivatorMode": "adaptive", "userName": "old" }
            }
        """.trimIndent()
        val parsed = BackupCodec.fromJson(legacyJson)
        assertThat(parsed.projects.single().syncUuid).isEqualTo(projectUuid)
        assertThat(parsed.tasks.single().syncUuid).isEqualTo(taskUuid)

        val backup = PhoneSnapshotBackup(context)
        val file = backup.createBackup(repo)
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).doesNotContain("motivatorMode")

        repo.replacePlannerContentSilent(
            importedProjects = listOf(
                ProjectItem(syncUuid = UUID.randomUUID().toString(), name = "ПК")
            ),
            importedTasks = emptyList()
        )
        assertThat(repo.projectsOnce().single().name).isEqualTo("ПК")

        backup.restoreLatest(repo)
        assertThat(repo.projectsOnce().single().syncUuid).isEqualTo(projectUuid)
        assertThat(repo.tasksOnce().single().syncUuid).isEqualTo(taskUuid)

        settings.update {
            it.copy(pendingSnapshotAckId = "", requiresPcPrimarySnapshot = true)
        }
        assertThat(settings.settings.first().requiresPcPrimarySnapshot).isTrue()
    }

    @Test
    fun appSettingsDefaultsHaveNoDemoLoadedField() {
        val defaults = AppSettings()
        assertThat(defaults.userName).isEmpty()
        assertThat(defaults.reduceAnimations).isFalse()
    }
}
