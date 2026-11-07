package com.breakingfree.app.todo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task as stored in Room. */
@Entity(tableName = "task")
data class LocalTask(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    /** [com.breakingfree.app.todo.domain.TaskStatus] code. */
    val status: Int,
    val priority: Int,
    val dueAt: Long?,
    val startAt: Long?,
    /** Weekday mask the task can be done on, 0 = any. */
    val days: Int,
    val reminderMode: Int,
    val reminderStart: Int,
    val reminderEnd: Int,
    val reminderDays: Int,
    /** [com.breakingfree.app.todo.data.SystemKind] name, null for the user's own tasks. */
    val systemKind: String?,
    val createdAt: Long,
    val completedAt: Long?,
    /** 0..10. Added in database version 2. */
    @ColumnInfo(defaultValue = "0") val interest: Int = 0,
    /** [com.breakingfree.app.todo.data.TaskSize] level. Added in version 2. */
    @ColumnInfo(defaultValue = "0") val size: Int = 0,
    /** [com.breakingfree.app.todo.data.Difficulty] level. Added in version 2. */
    @ColumnInfo(defaultValue = "0") val difficulty: Int = 0,
)

/** One tag on one task. */
@Entity(
    tableName = "task_tag",
    primaryKeys = ["taskId", "tag"],
    foreignKeys = [
        ForeignKey(entity = LocalTask::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag")],
)
data class LocalTaskTag(val taskId: String, val tag: String)

/** [taskId] can't be started until [dependsOnId] is completed. */
@Entity(
    tableName = "task_dependency",
    primaryKeys = ["taskId", "dependsOnId"],
    foreignKeys = [
        ForeignKey(entity = LocalTask::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocalTask::class, parentColumns = ["id"], childColumns = ["dependsOnId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dependsOnId")],
)
data class LocalTaskDependency(val taskId: String, val dependsOnId: String)
