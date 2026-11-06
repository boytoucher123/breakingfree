package com.breakingfree.app.todo.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM task")
    fun observeAll(): Flow<List<LocalTask>>

    @Query("SELECT * FROM task_tag")
    fun observeAllTags(): Flow<List<LocalTaskTag>>

    @Query("SELECT * FROM task_dependency")
    fun observeAllDependencies(): Flow<List<LocalTaskDependency>>

    @Query("SELECT * FROM task")
    suspend fun getAll(): List<LocalTask>

    @Query("SELECT * FROM task_tag")
    suspend fun getAllTags(): List<LocalTaskTag>

    @Query("SELECT * FROM task_dependency")
    suspend fun getAllDependencies(): List<LocalTaskDependency>

    @Query("SELECT * FROM task WHERE id = :taskId")
    suspend fun getById(taskId: String): LocalTask?

    @Upsert
    suspend fun upsert(task: LocalTask)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<LocalTaskTag>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependencies(deps: List<LocalTaskDependency>)

    @Query("DELETE FROM task_tag WHERE taskId = :taskId")
    suspend fun deleteTags(taskId: String)

    @Query("DELETE FROM task_dependency WHERE taskId = :taskId")
    suspend fun deleteDependencies(taskId: String)

    /** Writes a task together with its tags and prerequisites, replacing the old ones. */
    @Transaction
    suspend fun save(task: LocalTask, tags: List<String>, dependsOn: Set<String>) {
        upsert(task)
        deleteTags(task.id)
        insertTags(tags.map { LocalTaskTag(task.id, it) })
        deleteDependencies(task.id)
        insertDependencies(dependsOn.filter { it != task.id }.map { LocalTaskDependency(task.id, it) })
    }

    @Query("UPDATE task SET status = :status, completedAt = :completedAt WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: Int, completedAt: Long?)

    @Query("DELETE FROM task WHERE id = :taskId")
    suspend fun deleteById(taskId: String): Int

    @Query("DELETE FROM task WHERE status = 2")
    suspend fun deleteCompleted(): Int

    /** Removes a tag from every task. */
    @Query("DELETE FROM task_tag WHERE tag = :tag")
    suspend fun deleteTag(tag: String)
}

@Database(
    entities = [LocalTask::class, LocalTaskTag::class, LocalTaskDependency::class],
    version = 1,
    exportSchema = false,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
