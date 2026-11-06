package com.breakingfree.app.todo.domain

import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.random.Random

/**
 * Plain-Kotlin snapshot of a task, as the recommendation engine sees it.
 * Kept free of Android/Room types so it can be unit tested on the JVM.
 */
data class Candidate(
    val id: String,
    /** 0 = none, 1 = low, 2 = medium, 3 = high. */
    val priority: Int,
    /** Epoch millis, or null when there is no deadline. */
    val dueAt: Long?,
    /** Epoch millis before which the task can't be started, or null. */
    val startAt: Long?,
    val completed: Boolean,
    /** Ids of tasks that must be completed before this one can start. */
    val blockedBy: Set<String> = emptySet(),
    val inProgress: Boolean = false,
    /** Missing details (tags / priority): parked in the unfinished area, never suggested. */
    val unfinished: Boolean = false,
    /** Weekdays the task can be worked on, see [Weekdays]. 0 = any day. */
    val days: Int = 0,
)

/** Tunable numbers behind the ranking and the shuffle. Edited on the TODO settings screen. */
data class Weights(
    val priorityHigh: Double = 6.0,
    val priorityMedium: Double = 3.0,
    val priorityLow: Double = 1.5,
    val priorityNone: Double = 0.0,
    /** Score for a missed deadline. */
    val overdue: Double = 12.0,
    /** Score for a deadline within [dueSoonHours]. */
    val dueSoon: Double = 10.0,
    /** Deadlines closer than this get the full [dueSoon] score; further ones fade as hours/this. */
    val dueSoonHours: Double = 24.0,
    /** Shuffle only: every startable task's minimum weight, so anything can come up. */
    val shuffleBase: Double = 1.0,
    /** Shuffle only: multiplier for tasks already in progress. */
    val inProgressBoost: Double = 2.0,
) {
    fun priority(level: Int) = when (level) {
        3 -> priorityHigh
        2 -> priorityMedium
        1 -> priorityLow
        else -> priorityNone
    }
}

/**
 * Decides which task the user should do next.
 *
 * - [rankByImportance] orders every task that can be started right now, most important first.
 *   That ordering drives the widget's "Most important" / "Next most important" button.
 * - [pickWeighted] draws one startable task at random, with more important, sooner-due and
 *   in-progress tasks more likely to come up. That drives the "Shuffle" button.
 *
 * See TODO.md for planned time-of-day and location weighting.
 */
object Recommender {
    private const val HOUR = 60 * 60 * 1000L

    /** Why a task can't be started right now, or null if it can. */
    fun blockReason(task: Candidate, openIds: Set<String>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Block? = when {
        task.startAt != null && task.startAt > now -> Block.NOT_YET
        task.blockedBy.any { it in openIds } -> Block.PREREQUISITE
        !Weekdays.allows(task.days, Instant.ofEpochMilli(now).atZone(zone).dayOfWeek) -> Block.WRONG_DAY
        else -> null
    }

    /**
     * Startable: open, finished being set up, its start date has passed, it's an allowed weekday
     * and nothing it depends on is still open.
     */
    fun startable(tasks: List<Candidate>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Candidate> {
        val openIds = tasks.filter { !it.completed }.map { it.id }.toSet()
        return tasks.filter { !it.completed && !it.unfinished && blockReason(it, openIds, now, zone) == null }
    }

    /**
     * How important a task is right now. Priority and deadline both count; a deadline that's
     * close (or already missed) can outrank a high priority with no deadline.
     */
    fun score(task: Candidate, now: Long, w: Weights = Weights()): Double {
        val priorityScore = w.priority(task.priority)
        val due = task.dueAt ?: return priorityScore
        val hoursLeft = (due - now).toDouble() / HOUR
        val urgency = when {
            hoursLeft < 0 -> w.overdue
            hoursLeft <= w.dueSoonHours -> w.dueSoon
            else -> w.dueSoon * w.dueSoonHours / hoursLeft // fades as the deadline gets further away
        }
        return priorityScore + urgency
    }

    fun rankByImportance(
        tasks: List<Candidate>,
        now: Long,
        w: Weights = Weights(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Candidate> =
        startable(tasks, now, zone).sortedWith(
            compareByDescending<Candidate> { score(it, now, w) }
                .thenBy { it.dueAt ?: Long.MAX_VALUE }
                .thenBy { it.id },
        )

    /** Draw weight: every startable task gets a chance, important and in-progress ones a bigger one. */
    fun weight(task: Candidate, now: Long, w: Weights = Weights()): Double =
        (w.shuffleBase + score(task, now, w)) * (if (task.inProgress) w.inProgressBoost else 1.0)

    /**
     * Weighted random pick among startable tasks. Avoids returning [avoidId] (the task currently
     * on screen) when there is anything else to pick, so pressing Shuffle always changes the task.
     */
    fun pickWeighted(
        tasks: List<Candidate>,
        now: Long,
        avoidId: String? = null,
        random: Random = Random.Default,
        w: Weights = Weights(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Candidate? {
        val pool = startable(tasks, now, zone).let { all ->
            if (all.size > 1 && avoidId != null) all.filter { it.id != avoidId } else all
        }
        if (pool.isEmpty()) return null
        val weights = pool.map { max(weight(it, now, w), 0.0001) }
        var roll = random.nextDouble() * weights.sum()
        for ((i, wt) in weights.withIndex()) {
            roll -= wt
            if (roll < 0) return pool[i]
        }
        return pool.last()
    }
}

enum class Block { NOT_YET, PREREQUISITE, WRONG_DAY }
