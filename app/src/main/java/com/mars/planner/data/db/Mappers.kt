package com.mars.planner.data.db

import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus

fun TaskEntity.toDomain(): TaskItem = TaskItem(
    id = id,
    title = title,
    description = description,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeMinutes = dueTimeMinutes,
    reminderAtEpochMillis = reminderAtEpochMillis,
    priority = TaskPriority.fromKey(priority),
    category = category,
    status = TaskStatus.fromKey(status),
    createdAt = createdAt,
    updatedAt = updatedAt,
    postponeCount = postponeCount,
    postponeReason = postponeReason,
    parentTaskId = parentTaskId,
    nestingLevel = nestingLevel,
    relatedToTaskId = relatedToTaskId,
    isDemo = isDemo
)

fun TaskItem.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeMinutes = dueTimeMinutes,
    reminderAtEpochMillis = reminderAtEpochMillis,
    priority = priority.key,
    category = category,
    status = status.key,
    createdAt = createdAt,
    updatedAt = updatedAt,
    postponeCount = postponeCount,
    postponeReason = postponeReason,
    parentTaskId = parentTaskId,
    nestingLevel = nestingLevel,
    relatedToTaskId = relatedToTaskId,
    isDemo = isDemo
)

fun EnhancementEntity.toDomain(): EnhancementIdea = EnhancementIdea(
    id = id,
    sourceTaskId = sourceTaskId,
    title = title,
    description = description,
    status = EnhancementStatus.fromKey(status),
    priority = TaskPriority.fromKey(priority),
    createdAt = createdAt,
    plannedDateEpochDay = plannedDateEpochDay,
    deferredReason = deferredReason,
    convertedTaskId = convertedTaskId
)

fun EnhancementIdea.toEntity(): EnhancementEntity = EnhancementEntity(
    id = id,
    sourceTaskId = sourceTaskId,
    title = title,
    description = description,
    status = status.key,
    priority = priority.key,
    createdAt = createdAt,
    plannedDateEpochDay = plannedDateEpochDay,
    deferredReason = deferredReason,
    convertedTaskId = convertedTaskId
)
