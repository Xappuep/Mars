package com.mars.planner.sync

import android.content.Context
import com.mars.planner.data.PlannerRepository
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Резервная копия телефона перед заменой данными ПК и восстановление из неё.
 * Копия не удаляется после успешного снимка.
 */
class PhoneSnapshotBackup(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, "backups").also { it.mkdirs() }

    fun listBackups(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    fun latestBackup(): File? = listBackups().firstOrNull()

    suspend fun createBackup(planner: PlannerRepository): File {
        val projects = planner.projectsOnce()
        val tasks = planner.tasksOnce()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val file = File(dir, "$PREFIX$stamp.json")
        // Настройки pairing/темы не входят в этот файл намеренно —
        // replace снимка их не трогает; бэкап хранит только содержимое планера.
        file.writeText(BackupCodec.toJson(tasks, projects, settings = null), Charsets.UTF_8)
        return file
    }

    suspend fun restoreLatest(planner: PlannerRepository): File {
        val file = latestBackup()
            ?: throw SyncEngineException(SyncErrorCodes.NOT_FOUND, "Нет резервной копии")
        val payload = BackupCodec.fromJson(file.readText(Charsets.UTF_8))
        val projects = payload.projects.map { dto ->
            ProjectItem(
                syncUuid = dto.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() },
                name = dto.name,
                description = dto.description,
                archived = dto.archived,
                createdAt = dto.createdAt,
                createdAtRawUtc = VersionHash.formatUtc(dto.createdAt),
                updatedAt = dto.updatedAt
            )
        }
        val tasks = payload.tasks.map { dto ->
            TaskItem(
                syncUuid = dto.syncUuid.ifBlank { java.util.UUID.randomUUID().toString() },
                title = dto.title,
                description = dto.description,
                projectSyncUuid = dto.projectSyncUuid,
                priority = TaskPriority.fromKey(dto.priority),
                dueAtEpochMillis = dto.dueAtEpochMillis,
                dueAtRawUtc = dto.dueAtEpochMillis?.let { VersionHash.formatUtc(it) },
                status = TaskStatus.fromKey(dto.status),
                createdAt = dto.createdAt,
                createdAtRawUtc = VersionHash.formatUtc(dto.createdAt),
                updatedAt = dto.updatedAt
            )
        }
        planner.replacePlannerContentSilent(projects, tasks)
        return file
    }

    companion object {
        const val PREFIX = "pre_pc_primary_"
    }
}
