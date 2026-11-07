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

/** Rough size of a task. Scales how much its deadline counts; no deadline, no effect. */
enum class TaskSize(val level: Int, val label: String) {
    UNSET(0, "?"),
    TINY(1, "TINY"),
    SMALL(2, "SMALL"),
    MEDIUM(3, "MEDIUM"),
    LARGE(4, "LARGE"),
    HUGE(5, "HUGE");

    companion object {
        fun of(level: Int): TaskSize = entries.firstOrNull { it.level == level } ?: UNSET
    }
}

/** How hard a task is. Used by the Easy pick. */
enum class Difficulty(val level: Int, val label: String) {
    UNSET(0, "?"),
    TRIVIAL(1, "TRIVIAL"),
    EASY(2, "EASY"),
    MODERATE(3, "MODERATE"),
    HARD(4, "HARD"),
    BRUTAL(5, "BRUTAL");

    companion object {
        fun of(level: Int): Difficulty = entries.firstOrNull { it.level == level } ?: UNSET
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
 * @param interest how much the user wants to do it, -5..5 (0 = apathetic); null = not set yet.
 * @param windowStart time of day (minutes) from which it can be worked on, -1 = any; see [windowEnd].
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
    val interest: Int? = null,
    val size: TaskSize = TaskSize.UNSET,
    val difficulty: Difficulty = Difficulty.UNSET,
    val windowStart: Int = -1,
    val windowEnd: Int = -1,
) {
    val isCompleted get() = status == TaskStatus.DONE
    val isActive get() = !isCompleted
    val isInProgress get() = status == TaskStatus.IN_PROGRESS
    val isSystem get() = system != null

    /** Missing details (see [missingFields]). Parked in the unfinished area until filled in. */
    val isUnfinished get() = !isSystem && !isCompleted && missingFields.isNotEmpty()

    /** Which required details are still missing, in display order. */
    val missingFields: List<String> get() = missingFields(priority, tags, size, difficulty, interest)

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
        interest = interest ?: 0,
        size = size.level,
        difficulty = difficulty.level,
        windowStart = windowStart,
        windowEnd = windowEnd,
    )
}

/** Every task needs all of these before it can be suggested. */
fun missingFields(priority: Priority, tags: List<String>, size: TaskSize, difficulty: Difficulty, interest: Int?): List<String> =
    listOfNotNull(
        "PRIORITY".takeIf { priority == Priority.NONE },
        "TAGS".takeIf { tags.isEmpty() },
        "SIZE".takeIf { size == TaskSize.UNSET },
        "DIFFICULTY".takeIf { difficulty == Difficulty.UNSET },
        "INTEREST".takeIf { interest == null },
    )

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
    val interest: Int? = null,
    val size: TaskSize = TaskSize.UNSET,
    val difficulty: Difficulty = Difficulty.UNSET,
    val windowStart: Int = -1,
    val windowEnd: Int = -1,
) {
    val missingFields: List<String> get() = missingFields(priority, tags, size, difficulty, interest)
}

fun Task.toDraft() = TaskDraft(
    title, description, priority, dueAt, startAt, days, tags, blockedBy, reminder, interest, size, difficulty, windowStart, windowEnd,
)
