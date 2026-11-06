package com.breakingfree.app.todo.domain

/** Stored workflow state of a task. */
enum class TaskStatus(val code: Int) {
    TODO(0),
    IN_PROGRESS(1),
    DONE(2);

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: TODO
    }
}

/** What the user sees: the stored status plus what's stopping the task right now. */
enum class DisplayStatus(val label: String) {
    TODO("TO DO"),
    UNFINISHED("UNFINISHED"),
    WAITING("WAITING"),
    IN_PROGRESS("IN PROGRESS"),
    BLOCKED("BLOCKED"),
    DONE("DONE");

    companion object {
        /**
         * An in-progress task that can't currently be worked on (wrong weekday, an open
         * prerequisite, not yet available) is BLOCKED; it goes back to IN PROGRESS by itself.
         */
        fun of(status: TaskStatus, unfinished: Boolean, block: Block?): DisplayStatus = when {
            status == TaskStatus.DONE -> DONE
            status == TaskStatus.IN_PROGRESS -> if (block != null) BLOCKED else IN_PROGRESS
            unfinished -> UNFINISHED
            block != null -> WAITING
            else -> TODO
        }
    }
}
