package com.mars.planner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index(value = ["syncUuid"], unique = true)]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val name: String,
    val description: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val createdAtRawUtc: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["syncUuid"], unique = true),
        Index("projectSyncUuid"),
        Index("dueAtEpochMillis"),
        Index("status")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncUuid: String,
    val title: String,
    val description: String = "",
    val projectSyncUuid: String? = null,
    val priority: String = "normal",
    val dueAtEpochMillis: Long? = null,
    val dueAtRawUtc: String? = null,
    val status: String = "open",
    val createdAt: Long = System.currentTimeMillis(),
    val createdAtRawUtc: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isDemo: Boolean = false
)

@Entity(
    tableName = "sync_operations",
    indices = [
        Index(value = ["operationId"], unique = true),
        Index("causalSeq"),
        Index("acked"),
        Index("entityUuid")
    ]
)
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val entityType: String,
    val entityUuid: String,
    val op: String,
    val baseHash: String? = null,
    val newHash: String? = null,
    val payloadJson: String? = null,
    val deviceId: String,
    val protocolVersion: Int = 1,
    val causalSeq: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val acked: Boolean = false
)

@Entity(
    tableName = "sync_tombstones",
    indices = [Index(value = ["entityUuid"], unique = true)]
)
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityUuid: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val baseHash: String? = null,
    val acked: Boolean = false
)

@Entity(tableName = "sync_conflicts")
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityUuid: String,
    val localPayloadJson: String?,
    val remotePayloadJson: String?,
    val localOp: String,
    val remoteOp: String,
    val packageId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

@Entity(
    tableName = "sync_applied_packages",
    indices = [Index(value = ["packageId"], unique = true)]
)
data class SyncAppliedPackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageId: String,
    val senderDeviceId: String,
    val appliedAt: Long = System.currentTimeMillis(),
    val status: String = "applied" // applied | rejected
)

@Entity(tableName = "migration_archive")
data class MigrationArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val archiveJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reportJson: String = "",
    val reportShown: Boolean = false
)
