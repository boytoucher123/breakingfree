package com.breakingfree.app.todo.domain

import kotlin.random.Random

/** Which button the user last pressed on the "next task" widget / card. */
enum class NextMode {
    MOST_IMPORTANT,
    SHUFFLE,

    /** Small tasks only, smaller and easier ones likelier. */
    EASY;

    companion object {
        fun of(name: String?): NextMode = entries.firstOrNull { it.name == name } ?: MOST_IMPORTANT
    }
}

/**
 * What the widget is showing. [rank] is the position in the importance ranking (0 = most
 * important) used in [NextMode.MOST_IMPORTANT]; [shuffledId] is the task drawn in
 * [NextMode.SHUFFLE] or [NextMode.EASY].
 *
 * The default state is "most important, rank 0": the widget opens on the most important task.
 * The buttons are labelled IMPORTANT, RANDOM and EASY.
 */
data class NextSelection(
    val mode: NextMode = NextMode.MOST_IMPORTANT,
    val rank: Int = 0,
    val shuffledId: String? = null,
)

data class Resolved(
    /** Null when there is nothing to show: no startable task, or (Easy) no small one. */
    val task: Candidate?,
    /** 0-based position of [task] in the importance ranking, or -1 if nothing to show. */
    val rank: Int,
    /** How many startable tasks there are. */
    val total: Int,
)

object NextTask {

    /** Works out which task [selection] currently points at. */
    fun resolve(tasks: List<Candidate>, selection: NextSelection, now: Long, w: Weights = Weights()): Resolved {
        val ranked = Recommender.rankByImportance(tasks, now, w)
        if (ranked.isEmpty()) return Resolved(null, -1, 0)
        fun at(c: Candidate?) = if (c == null) Resolved(null, -1, ranked.size) else Resolved(c, ranked.indexOf(c), ranked.size)
        return when (selection.mode) {
            NextMode.MOST_IMPORTANT -> at(ranked[selection.rank.mod(ranked.size)])
            // The shuffled task may since have been completed or blocked: fall back to the top task.
            NextMode.SHUFFLE -> at(ranked.firstOrNull { it.id == selection.shuffledId } ?: ranked[0])
            // Likewise for Easy, falling back to the easiest small task (if there is one).
            NextMode.EASY -> {
                val pool = Recommender.easyPool(tasks, now, w)
                at(pool.firstOrNull { it.id == selection.shuffledId } ?: pool.maxByOrNull { Recommender.easyWeight(it, w) })
            }
        }
    }

    /** IMPORTANT button: the top task, then each press steps to the next most important. Wraps back to the top after the last task. */
    fun pressImportant(tasks: List<Candidate>, selection: NextSelection, now: Long, w: Weights = Weights()): NextSelection {
        if (selection.mode != NextMode.MOST_IMPORTANT) return NextSelection(NextMode.MOST_IMPORTANT, 0)
        val total = Recommender.rankByImportance(tasks, now, w).size
        return NextSelection(NextMode.MOST_IMPORTANT, if (total == 0) 0 else (selection.rank + 1).mod(total))
    }

    /** RANDOM button: weighted random draw, never repeating the task on screen if there's another. */
    fun pressShuffle(
        tasks: List<Candidate>,
        selection: NextSelection,
        now: Long,
        random: Random = Random.Default,
        w: Weights = Weights(),
    ): NextSelection {
        val current = resolve(tasks, selection, now, w).task?.id
        val pick = Recommender.pickWeighted(tasks, now, avoidId = current, random = random, w = w)
        return NextSelection(NextMode.SHUFFLE, rank = 0, shuffledId = pick?.id)
    }

    /** EASY button: a small task, smaller and easier likelier, never repeating the one on screen if possible. */
    fun pressEasy(
        tasks: List<Candidate>,
        selection: NextSelection,
        now: Long,
        random: Random = Random.Default,
        w: Weights = Weights(),
    ): NextSelection {
        val current = resolve(tasks, selection, now, w).task?.id
        val pick = Recommender.pickEasy(tasks, now, avoidId = current, random = random, w = w)
        return NextSelection(NextMode.EASY, rank = 0, shuffledId = pick?.id)
    }
}
