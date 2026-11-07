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
    /** Version 2's 0..10 interest. No longer used (see [interestLevel]); kept so the table matches. */
    @ColumnInfo(defaultValue = "0") val interest: Int = 0,
    /** [com.breakingfree.app.todo.data.TaskSize] level. Added in version 2. */
    @ColumnInfo(defaultValue = "0") val size: Int = 0,
    /** [com.breakingfree.app.todo.data.Difficulty] level. Added in version 2. */
    @ColumnInfo(defaultValue = "0") val difficulty: Int = 0,
    /** Interest -5..5, null = not set. Added in version 3. */
    val interestLevel: Int? = null,
    /** Time-of-day window in minutes, -1 = any. Added in version 3. */
    @ColumnInfo(defaultValue = "-1") val windowStart: Int = -1,
    @ColumnInfo(defaultValue = "-1") val windowEnd: Int = -1,
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
