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
    /** How much the user wants to do it, 0..[Recommender.MAX_INTEREST]. */
    val interest: Int = 0,
    /** Rough size, 1 (tiny) .. 5 (huge); 0 = not set. */
    val size: Int = 0,
    /** How hard it is, 1 (trivial) .. 5 (brutal); 0 = not set. */
    val difficulty: Int = 0,
)

/** Tunable numbers behind the ranking and the shuffle. Edited on the Tasks settings screen. */
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
    /** Shuffle and Easy: a task at full interest is drawn (1 + this) times as often. */
    val interestBoost: Double = 0.5,
    /**
     * How strongly size scales the deadline part of the score. At 1, a tiny task's urgency is
     * halved and a huge one's doubled; at 0 size is ignored. No deadline, no effect.
     */
    val sizeEffect: Double = 1.0,
    /** Easy button: the biggest size (1 tiny .. 5 huge) it will pick. */
    val easyMaxSize: Double = 2.0,
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
    const val MAX_INTEREST = 10

    /** Per size level 1..5: how far the deadline urgency moves, scaled by [Weights.sizeEffect]. */
    private val SIZE_OFFSET = doubleArrayOf(0.0, -0.5, -0.25, 0.0, 0.5, 1.0)

    /** Multiplier on deadline urgency: big tasks need starting earlier, tiny ones can wait. */
    fun sizeFactor(size: Int, w: Weights = Weights()): Double =
        if (size !in 1..5) 1.0 else max(0.1, 1.0 + w.sizeEffect * SIZE_OFFSET[size])

    /** Multiplier on draw weight for how interested the user is. */
    fun interestFactor(interest: Int, w: Weights = Weights()): Double =
        1.0 + w.interestBoost * interest.coerceIn(0, MAX_INTEREST) / MAX_INTEREST

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
        return priorityScore + urgency * sizeFactor(task.size, w)
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

    /**
     * Draw weight: every startable task gets a chance; important, in-progress and interesting
     * ones a bigger one.
     */
    fun weight(task: Candidate, now: Long, w: Weights = Weights()): Double =
        (w.shuffleBase + score(task, now, w)) *
            (if (task.inProgress) w.inProgressBoost else 1.0) *
            interestFactor(task.interest, w)

    /** Startable tasks small enough for the Easy button. Tasks with no size set never count. */
    fun easyPool(tasks: List<Candidate>, now: Long, w: Weights = Weights(), zone: ZoneId = ZoneId.systemDefault()): List<Candidate> =
        startable(tasks, now, zone).filter { it.size in 1..w.easyMaxSize.toInt().coerceIn(1, 5) }

    /**
     * Easy draw weight: smaller and easier is likelier. Ease runs 2..10 (size and difficulty each
     * give 5 for the lowest level, 1 for the highest; unset difficulty counts as the middle),
     * squared so the easiest tasks clearly lead.
     */
    fun easyWeight(task: Candidate, w: Weights = Weights()): Double {
        val ease = (6 - task.size.coerceIn(1, 5)) + (6 - (task.difficulty.takeIf { it in 1..5 } ?: 3))
        return ease.toDouble() * ease * interestFactor(task.interest, w)
    }

    /** Easy button: weighted pick among small tasks, avoiding the one on screen if possible. */
    fun pickEasy(
        tasks: List<Candidate>,
        now: Long,
        avoidId: String? = null,
        random: Random = Random.Default,
        w: Weights = Weights(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Candidate? {
        val pool = easyPool(tasks, now, w, zone).let { all ->
            if (all.size > 1 && avoidId != null) all.filter { it.id != avoidId } else all
        }
        return draw(pool, random) { easyWeight(it, w) }
    }

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
        return draw(pool, random) { weight(it, now, w) }
    }

    private fun draw(pool: List<Candidate>, random: Random, weightOf: (Candidate) -> Double): Candidate? {
        if (pool.isEmpty()) return null
        val weights = pool.map { max(weightOf(it), 0.0001) }
        var roll = random.nextDouble() * weights.sum()
        for ((i, wt) in weights.withIndex()) {
            roll -= wt
            if (roll < 0) return pool[i]
        }
        return pool.last()
    }
}

enum class Block { NOT_YET, PREREQUISITE, WRONG_DAY }
