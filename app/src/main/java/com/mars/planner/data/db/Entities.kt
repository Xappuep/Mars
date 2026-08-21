package com.mars.planner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtEpochMillis: Long? = null,
    val priority: String = "normal",
    val category: String = "",
    val status: String = "new",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val postponeCount: Int = 0,
    val postponeReason: String? = null,
    val parentTaskId: Long? = null,
    val nestingLevel: Int = 0,
    val relatedToTaskId: Long? = null,
    val isDemo: Boolean = false
)

@Entity(
    tableName = "enhancements",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sourceTaskId")]
)
data class EnhancementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTaskId: Long,
    val title: String,
    val description: String = "",
    val status: String = "idea",
    val priority: String = "normal",
    val createdAt: Long = System.currentTimeMillis(),
    val plannedDateEpochDay: Long? = null,
    val deferredReason: String? = null,
    val convertedTaskId: Long? = null
)
