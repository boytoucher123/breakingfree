package com.breakingfree.app.todo.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
    exportSchema = false,
)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        /** Version 2: interest, size and difficulty. Existing tasks get 0 (not set) for each. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN interest INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE task ADD COLUMN difficulty INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Version 3: interest becomes -5..5 with "not set" distinct from 0 (apathetic), and tasks
         * get a time-of-day window. Old interest 1..10 maps to -4..5; 0 (not set) stays not set.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task ADD COLUMN interestLevel INTEGER")
                db.execSQL("ALTER TABLE task ADD COLUMN windowStart INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE task ADD COLUMN windowEnd INTEGER NOT NULL DEFAULT -1")
                db.execSQL("UPDATE task SET interestLevel = interest - 5 WHERE interest > 0")
            }
        }
    }
}
