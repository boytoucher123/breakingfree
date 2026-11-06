package com.breakingfree.app.todo.data

import com.breakingfree.app.todo.data.local.LocalTask
import com.breakingfree.app.todo.data.local.LocalTaskDependency
import com.breakingfree.app.todo.data.local.LocalTaskTag
import com.breakingfree.app.todo.data.local.TaskDao
import com.breakingfree.app.todo.domain.Reminder
import com.breakingfree.app.todo.domain.ReminderMode
import com.breakingfree.app.todo.domain.TaskStatus
import com.breakingfree.app.todo.domain.Tags
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single entry point for the todo data. Room is the only data source: everything is offline.
 *
 * Every write also keeps the "finish unfinished tasks" system task in step and then calls
 * [onChanged] (widget redraw, reminder rescheduling).
 */
class TaskRepository(
    private val dao: TaskDao,
    private val onChanged: suspend () -> Unit,
) {
    private val writeLock = Mutex()

    fun getTasksStream(): Flow<List<Task>> = combine(
        dao.observeAll(),
        dao.observeAllTags(),
        dao.observeAllDependencies(),
    ) { tasks, tags, deps ->
        withContext(Dispatchers.Default) { assemble(tasks, tags, deps) }
    }

    suspend fun getTasks(): List<Task> =
        assemble(dao.getAll(), dao.getAllTags(), dao.getAllDependencies())

    fun getTaskStream(taskId: String): Flow<Task?> =
        getTasksStream().map { tasks -> tasks.firstOrNull { it.id == taskId } }

    suspend fun getTask(taskId: String): Task? = getTasks().firstOrNull { it.id == taskId }

    suspend fun createTask(draft: TaskDraft): String {
        val id = UUID.randomUUID().toString()
        write {
            dao.save(toLocal(id, draft, TaskStatus.TODO, System.currentTimeMillis(), null, null), cleanTags(draft.tags), draft.blockedBy)
        }
        return id
    }

    suspend fun updateTask(taskId: String, draft: TaskDraft) = write {
        val existing = dao.getById(taskId) ?: throw Exception("Task (id $taskId) not found")
        dao.save(
            toLocal(taskId, draft, TaskStatus.of(existing.status), existing.createdAt, existing.completedAt, existing.systemKind),
            cleanTags(draft.tags),
            draft.blockedBy,
        )
    }

    suspend fun setStatus(taskId: String, status: TaskStatus) = write {
        dao.updateStatus(taskId, status.code, if (status == TaskStatus.DONE) System.currentTimeMillis() else null)
    }

    suspend fun completeTask(taskId: String) = setStatus(taskId, TaskStatus.DONE)

    suspend fun clearCompletedTasks() = write { dao.deleteCompleted() }

    suspend fun deleteTask(taskId: String) = write { dao.deleteById(taskId) }

    /** Removes [tag] from every task (tasks left with no tags become unfinished). */
    suspend fun deleteTag(tag: String) = write { dao.deleteTag(tag) }

    /** Brings the system task in line with the data; safe to call any time (e.g. at startup). */
    suspend fun reconcile() = write { }

    private suspend fun write(block: suspend () -> Unit) {
        writeLock.withLock {
            block()
            reconcileSystemTask()
        }
        onChanged()
    }

    /**
     * The "finish unfinished tasks" system task exists (open) while any task is missing details,
     * and resolves itself (marked done) as soon as the unfinished area is empty.
     */
    private suspend fun reconcileSystemTask() {
        val tasks = getTasks()
        val unfinished = tasks.count { it.isUnfinished }
        val sys = tasks.firstOrNull { it.system == SystemKind.FINISH_UNFINISHED }
        val now = System.currentTimeMillis()
        when {
            unfinished > 0 && sys == null -> dao.upsert(
                toLocal(
                    UUID.randomUUID().toString(),
                    TaskDraft(
                        title = FINISH_TITLE,
                        description = "Some tasks are missing a priority or tags, so they can't be suggested. " +
                            "Open each one and fill in the missing details. This resolves itself once they're all done.",
                        priority = Priority.MEDIUM,
                    ),
                    TaskStatus.TODO, now, null, SystemKind.FINISH_UNFINISHED.name,
                ),
            )
            unfinished > 0 && sys != null && sys.isCompleted -> dao.updateStatus(sys.id, TaskStatus.TODO.code, null)
            unfinished == 0 && sys != null && !sys.isCompleted -> dao.updateStatus(sys.id, TaskStatus.DONE.code, now)
        }
    }

    private fun cleanTags(tags: List<String>): List<String> = tags.map(Tags::normalize).filter { it.isNotEmpty() }.distinct()

    private fun toLocal(
        id: String,
        d: TaskDraft,
        status: TaskStatus,
        createdAt: Long,
        completedAt: Long?,
        systemKind: String?,
    ) = LocalTask(
        id = id,
        title = d.title,
        description = d.description,
        status = status.code,
        priority = d.priority.level,
        dueAt = d.dueAt,
        startAt = d.startAt,
        days = d.days,
        reminderMode = d.reminder.mode.code,
        reminderStart = d.reminder.startMinute,
        reminderEnd = d.reminder.endMinute,
        reminderDays = d.reminder.days,
        systemKind = systemKind,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun assemble(tasks: List<LocalTask>, tags: List<LocalTaskTag>, deps: List<LocalTaskDependency>): List<Task> {
        val tagsByTask = tags.groupBy({ it.taskId }, { it.tag })
        val depsByTask = deps.groupBy({ it.taskId }, { it.dependsOnId })
        return tasks.map { t ->
            Task(
                id = t.id,
                title = t.title,
                description = t.description,
                status = TaskStatus.of(t.status),
                priority = Priority.of(t.priority),
                dueAt = t.dueAt,
                startAt = t.startAt,
                days = t.days,
                tags = tagsByTask[t.id].orEmpty().sorted(),
                blockedBy = depsByTask[t.id].orEmpty().toSet(),
                reminder = Reminder(ReminderMode.of(t.reminderMode), t.reminderStart, t.reminderEnd, t.reminderDays),
                system = t.systemKind?.let { k -> SystemKind.entries.firstOrNull { it.name == k } },
                createdAt = t.createdAt,
            )
        }
    }

    companion object {
        const val FINISH_TITLE = "Finish unfinished tasks"
    }
}
