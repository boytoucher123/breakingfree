package com.breakingfree.app.todo.data

import com.breakingfree.app.todo.domain.Candidate
import com.breakingfree.app.todo.domain.Reminder
import com.breakingfree.app.todo.domain.TaskStatus
import com.breakingfree.app.todo.domain.Weekdays

/** Priority levels, lowest to highest. Stored as their [level]. */
enum class Priority(val level: Int, val label: String) {
    NONE(0, "NONE"),
    LOW(1, "LOW"),
    MEDIUM(2, "MEDIUM"),
    HIGH(3, "HIGH");

    companion object {
        fun of(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

/** Kinds of task the app creates and manages by itself. */
enum class SystemKind {
    /** "Go through the unfinished tasks": exists while any task is missing details. */
    FINISH_UNFINISHED,
}

/**
 * Immutable model class for a Task.
 *
 * @param dueAt deadline (epoch millis), or null.
 * @param startAt the task can't be started before this time (epoch millis), or null.
 * @param days [Weekdays] mask of days the task can be worked on; 0 = any day.
 * @param blockedBy ids of tasks that must be completed before this one can start.
 */
data class Task(
    val id: String,
    val title: String = "",
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val priority: Priority = Priority.NONE,
    val dueAt: Long? = null,
    val startAt: Long? = null,
    val days: Int = Weekdays.ANY,
    val tags: List<String> = emptyList(),
    val blockedBy: Set<String> = emptySet(),
    val reminder: Reminder = Reminder(),
    val system: SystemKind? = null,
    val createdAt: Long = 0L,
) {
    val isCompleted get() = status == TaskStatus.DONE
    val isActive get() = !isCompleted
    val isInProgress get() = status == TaskStatus.IN_PROGRESS
    val isSystem get() = system != null

    /** Missing details: no tags or no priority. Parked in the unfinished area until filled in. */
    val isUnfinished get() = !isSystem && !isCompleted && (tags.isEmpty() || priority == Priority.NONE)

    val titleForList: String
        get() = title.ifEmpty { description }

    fun toCandidate() = Candidate(
        id = id,
        priority = priority.level,
        dueAt = dueAt,
        startAt = startAt,
        completed = isCompleted,
        blockedBy = blockedBy,
        inProgress = isInProgress,
        unfinished = isUnfinished,
        days = days,
    )
}

/** Everything the user can edit on a task. */
data class TaskDraft(
    val title: String = "",
    val description: String = "",
    val priority: Priority = Priority.NONE,
    val dueAt: Long? = null,
    val startAt: Long? = null,
    val days: Int = Weekdays.ANY,
    val tags: List<String> = emptyList(),
    val blockedBy: Set<String> = emptySet(),
    val reminder: Reminder = Reminder(),
)

fun Task.toDraft() = TaskDraft(title, description, priority, dueAt, startAt, days, tags, blockedBy, reminder)
