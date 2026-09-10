package com.mars.planner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY archived ASC, name ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY name ASC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects")
    suspend fun getAllOnce(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM projects WHERE syncUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM projects WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM projects WHERE isDemo = 0")
    suspend fun countUser(): Int
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueAtEpochMillis ASC, updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE status = 'open'
          AND dueAtEpochMillis IS NOT NULL
          AND dueAtEpochMillis <= :endOfDayMillis
        ORDER BY dueAtEpochMillis ASC
        """
    )
    fun observeTodayAndOverdue(endOfDayMillis: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE syncUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE projectSyncUuid = :projectUuid")
    suspend fun getByProject(projectUuid: String): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllOnce(): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE syncUuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM tasks WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tasks WHERE isDemo = 0")
    suspend fun countUser(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDemo = 1")
    suspend fun countDemo(): Int
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_operations WHERE acked = 0 ORDER BY causalSeq ASC")
    suspend fun pendingOperations(): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations WHERE acked = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(causalSeq), 0) FROM sync_operations")
    suspend fun maxCausalSeq(): Long

    @Query("SELECT * FROM sync_operations WHERE operationId = :opId LIMIT 1")
    suspend fun getOperation(opId: String): SyncOperationEntity?

    @Query("SELECT COUNT(*) FROM sync_operations")
    suspend fun countOperations(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOperation(op: SyncOperationEntity): Long

    @Query("UPDATE sync_operations SET acked = 1 WHERE operationId IN (:ids)")
    suspend fun ackOperations(ids: List<String>)

    @Query("DELETE FROM sync_operations")
    suspend fun clearOperations()

    @Query("DELETE FROM sync_tombstones")
    suspend fun clearTombstones()

    @Query("DELETE FROM sync_conflicts")
    suspend fun clearConflicts()

    @Query("DELETE FROM sync_applied_packages")
    suspend fun clearAppliedPackages()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones WHERE entityUuid = :uuid LIMIT 1")
    suspend fun getTombstone(uuid: String): SyncTombstoneEntity?

    @Query("DELETE FROM sync_tombstones WHERE entityUuid = :uuid")
    suspend fun deleteTombstone(uuid: String)

    @Insert
    suspend fun insertConflict(conflict: SyncConflictEntity): Long

    @Query("SELECT * FROM sync_conflicts WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeOpenConflicts(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getConflict(id: Long): SyncConflictEntity?

    @Update
    suspend fun updateConflict(conflict: SyncConflictEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppliedPackage(pkg: SyncAppliedPackageEntity): Long

    @Query("SELECT * FROM sync_applied_packages WHERE packageId = :packageId LIMIT 1")
    suspend fun getAppliedPackage(packageId: String): SyncAppliedPackageEntity?

    @Query(
        """
        SELECT * FROM sync_applied_packages
        WHERE senderDeviceId = :sender AND status = :status
        ORDER BY id DESC
        """
    )
    suspend fun listAppliedBySenderStatus(sender: String, status: String): List<SyncAppliedPackageEntity>

    @Query(
        """
        SELECT * FROM sync_applied_packages
        WHERE senderDeviceId = :sender AND status = :status
        ORDER BY id DESC LIMIT 1
        """
    )
    suspend fun findAppliedBySenderStatus(sender: String, status: String): SyncAppliedPackageEntity?

    @Query("UPDATE sync_applied_packages SET status = :status WHERE packageId = :packageId")
    suspend fun updateAppliedPackageStatus(packageId: String, status: String)

    @Query(
        """
        UPDATE sync_applied_packages SET status = :newStatus
        WHERE senderDeviceId = :sender AND status = :oldStatus
        """
    )
    suspend fun updateAppliedStatusForSender(sender: String, oldStatus: String, newStatus: String): Int

    @Insert
    suspend fun insertArchive(archive: MigrationArchiveEntity): Long

    @Query("SELECT * FROM migration_archive ORDER BY id DESC LIMIT 1")
    suspend fun latestArchive(): MigrationArchiveEntity?

    @Query("UPDATE migration_archive SET reportShown = 1 WHERE id = :id")
    suspend fun markReportShown(id: Long)
}
