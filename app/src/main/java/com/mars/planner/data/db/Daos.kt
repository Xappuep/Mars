package com.mars.planner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL AND nestingLevel = 0
        ORDER BY dueDateEpochDay ASC, dueTimeMinutes ASC, priority DESC, updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeEveryTask(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query(
        """
        SELECT * FROM tasks
        WHERE dueDateEpochDay = :epochDay
          AND parentTaskId IS NULL
          AND nestingLevel = 0
        ORDER BY dueTimeMinutes ASC, priority DESC, updatedAt DESC
        """
    )
    fun observeForDay(epochDay: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL AND nestingLevel = 0
        ORDER BY dueDateEpochDay ASC, updatedAt DESC
        """
    )
    fun observeRootTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAt ASC")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAt ASC")
    suspend fun getSubtasks(parentId: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL
          AND nestingLevel = 0
          AND (
            title LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
            OR category LIKE '%' || :query || '%'
          )
        ORDER BY updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE dueDateEpochDay BETWEEN :fromDay AND :toDay
          AND parentTaskId IS NULL
          AND nestingLevel = 0
        """
    )
    suspend fun getBetweenDays(fromDay: Long, toDay: Long): List<TaskEntity>

    @Query(
        """
        SELECT * FROM tasks
        WHERE parentTaskId IS NULL AND nestingLevel = 0
        """
    )
    suspend fun getAllRootsOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllOnce(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    @Query("DELETE FROM tasks WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("SELECT COUNT(*) FROM tasks WHERE isDemo = 1")
    suspend fun countDemo(): Int
}

@Dao
interface EnhancementDao {
    @Query("SELECT * FROM enhancements WHERE sourceTaskId = :taskId ORDER BY createdAt DESC")
    fun observeForTask(taskId: Long): Flow<List<EnhancementEntity>>

    @Query(
        """
        SELECT * FROM enhancements
        WHERE status NOT IN ('realized', 'cancelled')
        ORDER BY createdAt DESC
        """
    )
    fun observeActiveIdeas(): Flow<List<EnhancementEntity>>

    @Query("SELECT * FROM enhancements")
    suspend fun getAllOnce(): List<EnhancementEntity>

    @Query("SELECT * FROM enhancements WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EnhancementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EnhancementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EnhancementEntity>): List<Long>

    @Update
    suspend fun update(item: EnhancementEntity)

    @Query("DELETE FROM enhancements WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM enhancements")
    suspend fun deleteAll()
}
