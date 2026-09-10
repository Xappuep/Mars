package com.mars.planner.data

import androidx.room.withTransaction
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.db.MigrationArchiveEntity
import com.mars.planner.data.db.ProjectEntity
import com.mars.planner.data.db.SyncConflictEntity
import com.mars.planner.data.db.SyncOperationEntity
import com.mars.planner.data.db.SyncTombstoneEntity
import com.mars.planner.data.db.TaskEntity
import com.mars.planner.data.db.toDomain
import com.mars.planner.data.db.toEntity
import com.mars.planner.domain.logic.DaySummaryCalculator
import com.mars.planner.domain.logic.StatsCalculator
import com.mars.planner.domain.logic.TodayTasksSelector
import com.mars.planner.domain.model.DaySummary
import com.mars.planner.domain.model.MigrationReport
import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.ProjectWithStats
import com.mars.planner.domain.model.StatsSnapshot
import com.mars.planner.domain.model.SyncConflictItem
import com.mars.planner.domain.model.SyncEntityType
import com.mars.planner.domain.model.SyncOp
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.sync.FullSnapshot
import com.mars.planner.sync.SyncEngineException
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.VersionHash
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class PlannerRepository(
    private val db: MarsDatabase,
    private val deviceIdProvider: () -> String
) {
    private val projects = db.projectDao()
    private val tasks = db.taskDao()
    private val sync = db.syncDao()

    fun observeProjects(): Flow<List<ProjectItem>> =
        projects.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveProjects(): Flow<List<ProjectItem>> =
        projects.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeProjectsWithStats(): Flow<List<ProjectWithStats>> =
        combine(projects.observeAll(), tasks.observeAll()) { projs, allTasks ->
            projs.map { p ->
                val related = allTasks.filter { it.projectSyncUuid == p.syncUuid }
                ProjectWithStats(
                    project = p.toDomain(),
                    totalTasks = related.size,
                    doneTasks = related.count { it.status == TaskStatus.DONE.key }
                )
            }
        }

    fun observeAllTasks(): Flow<List<TaskItem>> =
        tasks.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTodayTasks(today: LocalDate = LocalDate.now()): Flow<List<TaskItem>> {
        val end = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() - 1
        return tasks.observeTodayAndOverdue(end).map { list ->
            TodayTasksSelector.select(list.map { it.toDomain() }, today)
        }
    }

    fun observeTask(id: Long): Flow<TaskItem?> =
        tasks.observeById(id).map { it?.toDomain() }

    fun searchTasks(query: String): Flow<List<TaskItem>> =
        tasks.search(query).map { list -> list.map { it.toDomain() } }

    fun observePendingSyncCount(): Flow<Int> = sync.observePendingCount()

    fun observeConflicts(): Flow<List<SyncConflictItem>> =
        sync.observeOpenConflicts().map { list -> list.map { it.toDomain() } }

    suspend fun getTask(id: Long): TaskItem? = tasks.getById(id)?.toDomain()
    suspend fun getTaskByUuid(uuid: String): TaskItem? = tasks.getByUuid(uuid)?.toDomain()
    suspend fun getProject(id: Long): ProjectItem? = projects.getById(id)?.toDomain()
    suspend fun getProjectByUuid(uuid: String): ProjectItem? = projects.getByUuid(uuid)?.toDomain()

    suspend fun daySummary(today: LocalDate = LocalDate.now()): DaySummary {
        val list = TodayTasksSelector.select(tasks.getAllOnce().map { it.toDomain() }, today)
        return DaySummaryCalculator.summarize(list, today)
    }

    suspend fun stats(): StatsSnapshot =
        StatsCalculator.compute(tasks.getAllOnce().map { it.toDomain() })

    suspend fun saveProject(project: ProjectItem): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val existing = if (project.id != 0L) projects.getById(project.id)
        else projects.getByUuid(project.syncUuid)
        if (existing == null) {
            val uuid = project.syncUuid.ifBlank { UUID.randomUUID().toString() }
            val entity = project.copy(
                syncUuid = uuid,
                createdAt = if (project.createdAt == 0L) now else project.createdAt,
                createdAtRawUtc = project.createdAtRawUtc ?: VersionHash.formatUtc(if (project.createdAt == 0L) now else project.createdAt),
                updatedAt = now
            ).toEntity().copy(id = 0)
            val id = projects.insert(entity)
            if (!entity.isDemo) {
                appendOp(
                    SyncEntityType.PROJECT, uuid, SyncOp.CREATE,
                    baseHash = null,
                    newHash = hashProject(entity.copy(id = id)),
                    payload = projectPayload(entity.copy(id = id))
                )
            }
            id
        } else {
            val base = hashProject(existing)
            val updated = project.copy(
                id = existing.id,
                syncUuid = existing.syncUuid,
                createdAt = existing.createdAt,
                createdAtRawUtc = existing.createdAtRawUtc,
                updatedAt = now,
                isDemo = existing.isDemo
            ).toEntity()
            projects.update(updated)
            if (!updated.isDemo) {
                appendOp(
                    SyncEntityType.PROJECT, updated.syncUuid, SyncOp.UPDATE,
                    baseHash = base,
                    newHash = hashProject(updated),
                    payload = projectPayload(updated)
                )
            }
            updated.id
        }
    }

    suspend fun archiveProject(id: Long, archived: Boolean) {
        val current = projects.getById(id) ?: return
        saveProject(current.toDomain().copy(archived = archived))
    }

    suspend fun saveTask(task: TaskItem): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val existing = if (task.id != 0L) tasks.getById(task.id)
        else tasks.getByUuid(task.syncUuid)
        if (existing == null) {
            val uuid = task.syncUuid.ifBlank { UUID.randomUUID().toString() }
            val createdAt = if (task.createdAt == 0L) now else task.createdAt
            val entity = task.copy(
                syncUuid = uuid,
                createdAt = createdAt,
                createdAtRawUtc = task.createdAtRawUtc ?: VersionHash.formatUtc(createdAt),
                dueAtRawUtc = when {
                    task.dueAtRawUtc != null && task.dueAtEpochMillis == existing?.dueAtEpochMillis -> task.dueAtRawUtc
                    task.dueAtEpochMillis != null -> VersionHash.formatUtc(task.dueAtEpochMillis)
                    else -> null
                },
                updatedAt = now
            ).toEntity().copy(id = 0)
            val id = tasks.insert(entity)
            if (!entity.isDemo) {
                appendOp(
                    SyncEntityType.TASK, uuid, SyncOp.CREATE,
                    baseHash = null,
                    newHash = hashTask(entity.copy(id = id)),
                    payload = taskPayload(entity.copy(id = id))
                )
            }
            id
        } else {
            val base = hashTask(existing)
            val updated = task.copy(
                id = existing.id,
                syncUuid = existing.syncUuid,
                createdAt = existing.createdAt,
                createdAtRawUtc = existing.createdAtRawUtc,
                dueAtRawUtc = when {
                    task.dueAtEpochMillis == null -> null
                    task.dueAtEpochMillis == existing.dueAtEpochMillis -> existing.dueAtRawUtc
                    else -> VersionHash.formatUtc(task.dueAtEpochMillis)
                },
                updatedAt = now,
                isDemo = existing.isDemo
            ).toEntity()
            tasks.update(updated)
            if (!updated.isDemo) {
                appendOp(
                    SyncEntityType.TASK, updated.syncUuid, SyncOp.UPDATE,
                    baseHash = base,
                    newHash = hashTask(updated),
                    payload = taskPayload(updated)
                )
            }
            updated.id
        }
    }

    suspend fun setTaskStatus(id: Long, status: TaskStatus): TaskItem? {
        val current = tasks.getById(id) ?: return null
        val updated = current.toDomain().copy(status = status)
        saveTask(updated)
        return getTask(id)
    }

    /** Перенос срока: изменение dueAt, отдельного статуса «отложено» больше нет. */
    suspend fun setTaskDueAt(id: Long, dueAtEpochMillis: Long?): TaskItem? {
        val current = tasks.getById(id) ?: return null
        saveTask(current.toDomain().copy(dueAtEpochMillis = dueAtEpochMillis))
        return getTask(id)
    }

    /** Импорт с объединением: существующие по UUID обновляются, новые добавляются. */
    suspend fun mergeImport(importedProjects: List<ProjectItem>, importedTasks: List<TaskItem>) {
        importedProjects.forEach { project ->
            val existing = projects.getByUuid(project.syncUuid)
            saveProject(project.copy(id = existing?.id ?: 0L))
        }
        importedTasks.forEach { task ->
            val existing = tasks.getByUuid(task.syncUuid)
            saveTask(task.copy(id = existing?.id ?: 0L))
        }
    }

    /** Импорт с заменой: локальные данные удаляются (с журналом операций), затем импорт. */
    suspend fun replaceAll(importedProjects: List<ProjectItem>, importedTasks: List<TaskItem>) {
        tasks.getAllOnce().forEach { deleteTask(it.id) }
        projects.getAllOnce().filter { !it.isDemo }.forEach { deleteProject(it.id) }
        mergeImport(importedProjects, importedTasks)
    }

    suspend fun deleteTask(id: Long) = db.withTransaction {
        val current = tasks.getById(id) ?: return@withTransaction
        if (!current.isDemo) {
            val base = hashTask(current)
            appendOp(
                SyncEntityType.TASK, current.syncUuid, SyncOp.DELETE,
                baseHash = base,
                newHash = null,
                payload = null
            )
            sync.upsertTombstone(
                SyncTombstoneEntity(
                    entityType = SyncEntityType.TASK.key,
                    entityUuid = current.syncUuid,
                    baseHash = base
                )
            )
        }
        tasks.deleteById(id)
    }

    suspend fun deleteProject(id: Long) = db.withTransaction {
        val current = projects.getById(id) ?: return@withTransaction
        val related = tasks.getByProject(current.syncUuid)
        related.forEach { deleteTask(it.id) }
        if (!current.isDemo) {
            val base = hashProject(current)
            appendOp(
                SyncEntityType.PROJECT, current.syncUuid, SyncOp.DELETE,
                baseHash = base,
                newHash = null,
                payload = null
            )
            sync.upsertTombstone(
                SyncTombstoneEntity(
                    entityType = SyncEntityType.PROJECT.key,
                    entityUuid = current.syncUuid,
                    baseHash = base
                )
            )
        }
        projects.deleteById(id)
    }

    suspend fun pendingOperations(): List<SyncOperationEntity> =
        sync.pendingOperations()

    suspend fun ackOperations(ids: List<String>) {
        if (ids.isNotEmpty()) sync.ackOperations(ids)
    }

    suspend fun tombstone(uuid: String): SyncTombstoneEntity? = sync.getTombstone(uuid)

    suspend fun clearTombstone(uuid: String) = sync.deleteTombstone(uuid)

    /**
     * Запись применённой удалённой операции в журнал (`acked = 1`, `causalSeq = 0`),
     * чтобы повтор того же `operation_id` был идемпотентен и не попал в отправку.
     */
    suspend fun insertSeenRemoteOperation(entity: SyncOperationEntity) {
        sync.insertOperation(entity.copy(id = 0, acked = true, causalSeq = 0L))
    }

    suspend fun markPackageApplied(packageId: String, sender: String, status: String = "applied") {
        sync.insertAppliedPackage(
            com.mars.planner.data.db.SyncAppliedPackageEntity(
                packageId = packageId,
                senderDeviceId = sender,
                status = status
            )
        )
    }

    suspend fun getAppliedPackage(packageId: String) = sync.getAppliedPackage(packageId)

    suspend fun getOperation(opId: String) = sync.getOperation(opId)

    suspend fun insertConflict(conflict: SyncConflictEntity): Long = sync.insertConflict(conflict)

    suspend fun resolveConflictKeepLocal(conflictId: Long) {
        val c = sync.getConflict(conflictId) ?: return
        sync.updateConflict(c.copy(resolved = true))
    }

    suspend fun resolveConflictAcceptRemote(conflictId: Long) {
        val c = sync.getConflict(conflictId) ?: return
        db.withTransaction {
            when (c.entityType) {
                SyncEntityType.TASK.key -> {
                    if (c.remoteOp == SyncOp.DELETE.key || c.remotePayloadJson == null) {
                        tasks.getByUuid(c.entityUuid)?.let { deleteTask(it.id) }
                    } else {
                        applyTaskPayload(JSONObject(c.remotePayloadJson))
                    }
                }
                SyncEntityType.PROJECT.key -> {
                    if (c.remoteOp == SyncOp.DELETE.key || c.remotePayloadJson == null) {
                        projects.getByUuid(c.entityUuid)?.let { deleteProject(it.id) }
                    } else {
                        applyProjectPayload(JSONObject(c.remotePayloadJson))
                    }
                }
            }
            sync.updateConflict(c.copy(resolved = true))
        }
    }

    suspend fun applyTaskPayload(payload: JSONObject): Long {
        val uuid = payload.getString("sync_uuid")
        val existing = tasks.getByUuid(uuid)
        val priorityKey = payload.getString("priority")
        val priority = TaskPriority.entries.find { it.key == priorityKey }
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный priority")
        val statusKey = payload.getString("status")
        val status = TaskStatus.entries.find { it.key == statusKey }
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Неизвестный status")
        if (!payload.has("description") || payload.get("description") !is String) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "description должен быть строкой")
        }
        val createdAtRaw = payload.getString("created_at")
        val createdAt = VersionHash.parseUtc(createdAtRaw)
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный created_at")
        val dueAtRaw = if (payload.isNull("due_at")) null else {
            val raw = payload.get("due_at")
            if (raw !is String || raw.isBlank()) {
                throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный due_at")
            }
            raw
        }
        val dueAt = dueAtRaw?.let {
            VersionHash.parseUtc(it)
                ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный due_at")
        }
        val item = TaskItem(
            id = existing?.id ?: 0L,
            syncUuid = uuid,
            title = payload.getString("title"),
            description = payload.getString("description"),
            projectSyncUuid = if (payload.isNull("project_uuid")) null else payload.getString("project_uuid"),
            priority = priority,
            dueAtEpochMillis = dueAt,
            dueAtRawUtc = dueAtRaw,
            status = status,
            createdAt = createdAt,
            createdAtRawUtc = createdAtRaw,
            updatedAt = System.currentTimeMillis(),
            isDemo = false
        )
        return upsertTaskSilent(item)
    }

    suspend fun applyProjectPayload(payload: JSONObject): Long {
        val uuid = payload.getString("sync_uuid")
        val existing = projects.getByUuid(uuid)
        if (!payload.has("description") || payload.get("description") !is String) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "description должен быть строкой")
        }
        if (!payload.has("archived") || payload.get("archived") !is Boolean) {
            throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "archived должен быть Boolean")
        }
        val createdAtRaw = payload.getString("created_at")
        val createdAt = VersionHash.parseUtc(createdAtRaw)
            ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "Некорректный created_at")
        val item = ProjectItem(
            id = existing?.id ?: 0L,
            syncUuid = uuid,
            name = payload.getString("name"),
            description = payload.getString("description"),
            archived = payload.getBoolean("archived"),
            createdAt = createdAt,
            createdAtRawUtc = createdAtRaw,
            updatedAt = System.currentTimeMillis(),
            isDemo = false
        )
        return upsertProjectSilent(item)
    }

    suspend fun upsertTaskSilent(task: TaskItem): Long {
        val existing = if (task.id != 0L) tasks.getById(task.id) else tasks.getByUuid(task.syncUuid)
        return if (existing == null) {
            tasks.insert(task.toEntity().copy(id = 0))
        } else {
            tasks.update(task.copy(id = existing.id, syncUuid = existing.syncUuid).toEntity())
            existing.id
        }
    }

    suspend fun upsertProjectSilent(project: ProjectItem): Long {
        val existing = if (project.id != 0L) projects.getById(project.id)
        else projects.getByUuid(project.syncUuid)
        return if (existing == null) {
            projects.insert(project.toEntity().copy(id = 0))
        } else {
            projects.update(project.copy(id = existing.id, syncUuid = existing.syncUuid).toEntity())
            existing.id
        }
    }

    suspend fun deleteTaskByUuidSilent(uuid: String) {
        tasks.deleteByUuid(uuid)
        sync.deleteTombstone(uuid)
    }

    suspend fun deleteProjectByUuidSilent(uuid: String) {
        projects.deleteByUuid(uuid)
        sync.deleteTombstone(uuid)
    }

    suspend fun withTxn(block: suspend () -> Unit) = db.withTransaction { block() }

    suspend fun hashTaskEntity(entity: TaskEntity): String = hashTask(entity)
    suspend fun hashProjectEntity(entity: ProjectEntity): String = hashProject(entity)

    suspend fun taskHashByUuid(uuid: String): String? = tasks.getByUuid(uuid)?.let { hashTask(it) }
    suspend fun projectHashByUuid(uuid: String): String? = projects.getByUuid(uuid)?.let { hashProject(it) }

    suspend fun taskPayloadJsonByUuid(uuid: String): String? =
        tasks.getByUuid(uuid)?.let { VersionHash.canonicalJson(taskPayload(it)) }

    suspend fun projectPayloadJsonByUuid(uuid: String): String? =
        projects.getByUuid(uuid)?.let { VersionHash.canonicalJson(projectPayload(it)) }

    fun hashTask(entity: TaskEntity): String =
        VersionHash.versionHash(
            VersionHash.taskPayload(
                syncUuid = entity.syncUuid,
                title = entity.title,
                description = entity.description,
                projectUuid = entity.projectSyncUuid,
                priority = entity.priority,
                dueAtUtc = entity.dueAtRawUtc ?: entity.dueAtEpochMillis?.let { VersionHash.formatUtc(it) },
                status = entity.status,
                createdAtUtc = entity.createdAtRawUtc.ifBlank { VersionHash.formatUtc(entity.createdAt) }
            )
        )

    fun hashProject(entity: ProjectEntity): String =
        VersionHash.versionHash(
            VersionHash.projectPayload(
                syncUuid = entity.syncUuid,
                name = entity.name,
                description = entity.description,
                archived = entity.archived,
                createdAtUtc = entity.createdAtRawUtc.ifBlank { VersionHash.formatUtc(entity.createdAt) }
            )
        )

    fun taskPayload(entity: TaskEntity): Map<String, Any?> =
        VersionHash.taskPayload(
            syncUuid = entity.syncUuid,
            title = entity.title,
            description = entity.description,
            projectUuid = entity.projectSyncUuid,
            priority = entity.priority,
            dueAtUtc = entity.dueAtRawUtc ?: entity.dueAtEpochMillis?.let { VersionHash.formatUtc(it) },
            status = entity.status,
            createdAtUtc = entity.createdAtRawUtc.ifBlank { VersionHash.formatUtc(entity.createdAt) }
        )

    fun projectPayload(entity: ProjectEntity): Map<String, Any?> =
        VersionHash.projectPayload(
            syncUuid = entity.syncUuid,
            name = entity.name,
            description = entity.description,
            archived = entity.archived,
            createdAtUtc = entity.createdAtRawUtc.ifBlank { VersionHash.formatUtc(entity.createdAt) }
        )

    private suspend fun appendOp(
        type: SyncEntityType,
        uuid: String,
        op: SyncOp,
        baseHash: String?,
        newHash: String?,
        payload: Map<String, Any?>?
    ) {
        val seq = sync.maxCausalSeq() + 1
        val payloadJson = payload?.let { VersionHash.canonicalJson(it) }
        sync.insertOperation(
            SyncOperationEntity(
                operationId = UUID.randomUUID().toString(),
                entityType = type.key,
                entityUuid = uuid,
                op = op.key,
                baseHash = baseHash,
                newHash = newHash,
                payloadJson = payloadJson,
                deviceId = deviceIdProvider(),
                protocolVersion = VersionHash.PROTOCOL_VERSION,
                causalSeq = seq
            )
        )
    }

    suspend fun migrationReport(): MigrationReport? {
        val arch = sync.latestArchive() ?: return null
        val r = JSONObject(arch.reportJson.ifBlank { "{}" })
        return MigrationReport(
            projectsCreated = r.optInt("projectsCreated"),
            tasksMigrated = r.optInt("tasksMigrated"),
            subtasksConverted = r.optInt("subtasksConverted"),
            enhancementsConverted = r.optInt("enhancementsConverted"),
            cancelledToDone = r.optInt("cancelledToDone"),
            dueAt2359Count = r.optInt("dueAt2359Count"),
            archivePath = "internal:migration_archive#${arch.id}",
            shown = arch.reportShown
        )
    }

    suspend fun markMigrationReportShown() {
        sync.latestArchive()?.let { sync.markReportShown(it.id) }
    }

    suspend fun exportMigrationArchiveJson(): String? =
        sync.latestArchive()?.archiveJson

    suspend fun clearDemo() {
        tasks.deleteDemo()
        projects.deleteDemo()
    }

    suspend fun loadDemo() {
        if (tasks.countDemo() > 0) return
        val projectUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        projects.insert(
            ProjectEntity(
                syncUuid = projectUuid,
                name = "Демо",
                description = "Демонстрационный проект",
                createdAt = now,
                createdAtRawUtc = VersionHash.formatUtc(now),
                updatedAt = now,
                isDemo = true
            )
        )
        tasks.insert(
            TaskEntity(
                syncUuid = UUID.randomUUID().toString(),
                title = "Пример открытой задачи",
                description = "Демо",
                projectSyncUuid = projectUuid,
                dueAtEpochMillis = now + 86_400_000L,
                dueAtRawUtc = VersionHash.formatUtc(now + 86_400_000L),
                status = TaskStatus.OPEN.key,
                createdAt = now,
                createdAtRawUtc = VersionHash.formatUtc(now),
                updatedAt = now,
                isDemo = true
            )
        )
        tasks.insert(
            TaskEntity(
                syncUuid = UUID.randomUUID().toString(),
                title = "Пример выполненной задачи",
                projectSyncUuid = projectUuid,
                status = TaskStatus.DONE.key,
                createdAt = now,
                createdAtRawUtc = VersionHash.formatUtc(now),
                updatedAt = now,
                isDemo = true
            )
        )
    }

    suspend fun exportSnapshot(): Pair<List<TaskItem>, List<ProjectItem>> =
        tasks.getAllOnce().map { it.toDomain() } to projects.getAllOnce().map { it.toDomain() }

    suspend fun projectsOnce(): List<ProjectItem> = projects.getAllOnce().map { it.toDomain() }
    suspend fun tasksOnce(): List<TaskItem> = tasks.getAllOnce().map { it.toDomain() }

    suspend fun countUserProjects(): Int = projects.countUser()
    suspend fun countUserTasks(): Int = tasks.countUser()

    /**
     * Тихая замена содержимого планера без исходящих операций:
     * для режима «ПК — основной» и восстановления локальной копии.
     */
    suspend fun replacePlannerContentSilent(
        importedProjects: List<ProjectItem>,
        importedTasks: List<TaskItem>
    ) = db.withTransaction {
        tasks.deleteAll()
        projects.deleteAll()
        sync.clearOperations()
        sync.clearTombstones()
        sync.clearConflicts()
        sync.clearAppliedPackages()
        importedProjects.forEach { project ->
            projects.insert(
                project.copy(id = 0L, isDemo = false).toEntity()
            )
        }
        importedTasks.forEach { task ->
            tasks.insert(
                task.copy(id = 0L, isDemo = false).toEntity()
            )
        }
    }

    suspend fun applyPcPrimarySnapshot(snapshot: FullSnapshot, snapshotId: String) {
        val projectItems = snapshot.projects.map { p ->
            ProjectItem(
                syncUuid = p.syncUuid,
                name = p.name,
                description = p.description,
                archived = p.archived,
                createdAt = VersionHash.parseUtc(p.createdAtUtc)
                    ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "created_at"),
                createdAtRawUtc = p.createdAtUtc,
                updatedAt = System.currentTimeMillis(),
                isDemo = false
            )
        }
        val taskItems = snapshot.tasks.map { t ->
            TaskItem(
                syncUuid = t.syncUuid,
                title = t.title,
                description = t.description,
                projectSyncUuid = t.projectUuid,
                priority = TaskPriority.entries.find { it.key == t.priority }
                    ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "priority"),
                dueAtEpochMillis = t.dueAtUtc?.let {
                    VersionHash.parseUtc(it)
                        ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "due_at")
                },
                dueAtRawUtc = t.dueAtUtc,
                status = TaskStatus.entries.find { it.key == t.status }
                    ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "status"),
                createdAt = VersionHash.parseUtc(t.createdAtUtc)
                    ?: throw SyncEngineException(SyncErrorCodes.INVALID_PAYLOAD, "created_at"),
                createdAtRawUtc = t.createdAtUtc,
                updatedAt = System.currentTimeMillis(),
                isDemo = false
            )
        }
        db.withTransaction {
            tasks.deleteAll()
            projects.deleteAll()
            sync.clearOperations()
            sync.clearTombstones()
            sync.clearConflicts()
            sync.clearAppliedPackages()
            projectItems.forEach { project ->
                projects.insert(project.copy(id = 0L, isDemo = false).toEntity())
            }
            taskItems.forEach { task ->
                tasks.insert(task.copy(id = 0L, isDemo = false).toEntity())
            }
            projectItems.forEach { p ->
                val local = projects.getByUuid(p.syncUuid)?.let { hashProject(it) }
                val expected = snapshot.projects.first { it.syncUuid == p.syncUuid }.versionHash
                if (local != expected) {
                    throw SyncEngineException(
                        SyncErrorCodes.INTERNAL_ERROR,
                        "Хеш проекта после снимка не совпал"
                    )
                }
            }
            taskItems.forEach { t ->
                val local = tasks.getByUuid(t.syncUuid)?.let { hashTask(it) }
                val expected = snapshot.tasks.first { it.syncUuid == t.syncUuid }.versionHash
                if (local != expected) {
                    throw SyncEngineException(
                        SyncErrorCodes.INTERNAL_ERROR,
                        "Хеш задачи после снимка не совпал"
                    )
                }
            }
            sync.insertAppliedPackage(
                com.mars.planner.data.db.SyncAppliedPackageEntity(
                    packageId = snapshotId,
                    senderDeviceId = PC_PRIMARY_SNAPSHOT_SENDER,
                    status = STATUS_SNAPSHOT_PENDING_ACK
                )
            )
        }
    }

    suspend fun listPendingSnapshotAckIds(): List<String> =
        sync.listAppliedBySenderStatus(PC_PRIMARY_SNAPSHOT_SENDER, STATUS_SNAPSHOT_PENDING_ACK)
            .map { it.packageId }

    suspend fun findPendingSnapshotAckId(): String? {
        val pending = listPendingSnapshotAckIds()
        if (pending.size > 1) {
            throw SyncEngineException(
                SyncErrorCodes.SNAPSHOT_STATE_CORRUPT,
                "Несколько незакрытых подтверждений снимка"
            )
        }
        return pending.firstOrNull()
    }

    suspend fun countPendingSnapshotAcks(): Int = listPendingSnapshotAckIds().size

    suspend fun markSnapshotAckCompleted(snapshotId: String) {
        sync.updateAppliedPackageStatus(snapshotId, STATUS_SNAPSHOT_APPLIED)
    }

    /** Закрывает локальные pending ACK после восстановления резервной копии. */
    suspend fun closePendingSnapshotAcksAfterRestore() {
        sync.updateAppliedStatusForSender(
            PC_PRIMARY_SNAPSHOT_SENDER,
            STATUS_SNAPSHOT_PENDING_ACK,
            STATUS_SNAPSHOT_SUPERSEDED
        )
    }

    companion object {
        const val PC_PRIMARY_SNAPSHOT_SENDER = "pc-primary-snapshot"
        const val STATUS_SNAPSHOT_PENDING_ACK = "snapshot_pending_ack"
        const val STATUS_SNAPSHOT_APPLIED = "applied"
        const val STATUS_SNAPSHOT_SUPERSEDED = "superseded_by_restore"
    }
}
