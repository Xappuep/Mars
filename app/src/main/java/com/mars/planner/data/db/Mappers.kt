package com.mars.planner.data.db

import com.mars.planner.domain.model.ProjectItem
import com.mars.planner.domain.model.SyncConflictItem
import com.mars.planner.domain.model.SyncEntityType
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus

fun ProjectEntity.toDomain() = ProjectItem(
    id = id,
    syncUuid = syncUuid,
    name = name,
    description = description,
    archived = archived,
    createdAt = createdAt,
    createdAtRawUtc = createdAtRawUtc.ifBlank { null },
    updatedAt = updatedAt,
    isDemo = isDemo
)

fun ProjectItem.toEntity() = ProjectEntity(
    id = id,
    syncUuid = syncUuid,
    name = name,
    description = description,
    archived = archived,
    createdAt = createdAt,
    createdAtRawUtc = createdAtRawUtc ?: "",
    updatedAt = updatedAt,
    isDemo = isDemo
)

fun TaskEntity.toDomain() = TaskItem(
    id = id,
    syncUuid = syncUuid,
    title = title,
    description = description,
    projectSyncUuid = projectSyncUuid,
    priority = TaskPriority.fromKey(priority),
    dueAtEpochMillis = dueAtEpochMillis,
    dueAtRawUtc = dueAtRawUtc,
    status = TaskStatus.fromKey(status),
    createdAt = createdAt,
    createdAtRawUtc = createdAtRawUtc.ifBlank { null },
    updatedAt = updatedAt,
    isDemo = isDemo
)

fun TaskItem.toEntity() = TaskEntity(
    id = id,
    syncUuid = syncUuid,
    title = title,
    description = description,
    projectSyncUuid = projectSyncUuid,
    priority = priority.key,
    dueAtEpochMillis = dueAtEpochMillis,
    dueAtRawUtc = dueAtRawUtc,
    status = status.key,
    createdAt = createdAt,
    createdAtRawUtc = createdAtRawUtc ?: "",
    updatedAt = updatedAt,
    isDemo = isDemo
)

fun SyncConflictEntity.toDomain() = SyncConflictItem(
    id = id,
    entityType = SyncEntityType.fromKey(entityType),
    entityUuid = entityUuid,
    localPayloadJson = localPayloadJson,
    remotePayloadJson = remotePayloadJson,
    localOp = localOp,
    remoteOp = remoteOp,
    packageId = packageId,
    createdAt = createdAt,
    resolved = resolved
)
