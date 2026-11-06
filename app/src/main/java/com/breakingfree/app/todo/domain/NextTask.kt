package com.breakingfree.app.todo.domain

import kotlin.random.Random

/** Which button the user last pressed on the "next task" widget / card. */
enum class NextMode { MOST_IMPORTANT, SHUFFLE }

/**
 * What the widget is showing. [rank] is the position in the importance ranking (0 = most
 * important) used in [NextMode.MOST_IMPORTANT]; [shuffledId] is the task drawn in [NextMode.SHUFFLE].
 *
 * The default state is "most important, rank 0": the widget opens on the most important task and
 * its first button already reads "Next most important".
 */
data class NextSelection(
    val mode: NextMode = NextMode.MOST_IMPORTANT,
    val rank: Int = 0,
    val shuffledId: String? = null,
) {
    /** Label of the first button: "Most important" turns into "Next most important" once selected. */
    val importantButtonLabel: String
        get() = if (mode == NextMode.MOST_IMPORTANT) "NEXT MOST IMPORTANT" else "MOST IMPORTANT"
}

data class Resolved(
    val task: Candidate?,
    /** 0-based position of [task] in the importance ranking, or -1 if nothing to show. */
    val rank: Int,
    val total: Int,
)

object NextTask {

    /** Works out which task [selection] currently points at. */
    fun resolve(tasks: List<Candidate>, selection: NextSelection, now: Long, w: Weights = Weights()): Resolved {
        val ranked = Recommender.rankByImportance(tasks, now, w)
        if (ranked.isEmpty()) return Resolved(null, -1, 0)
        if (selection.mode == NextMode.SHUFFLE) {
            val i = ranked.indexOfFirst { it.id == selection.shuffledId }
            // The shuffled task may since have been completed or blocked: fall back to the top task.
            return if (i >= 0) Resolved(ranked[i], i, ranked.size) else Resolved(ranked[0], 0, ranked.size)
        }
        val i = selection.rank.mod(ranked.size)
        return Resolved(ranked[i], i, ranked.size)
    }

    /** "Most important" / "Next most important" button. Wraps back to the top after the last task. */
    fun pressImportant(tasks: List<Candidate>, selection: NextSelection, now: Long, w: Weights = Weights()): NextSelection {
        if (selection.mode != NextMode.MOST_IMPORTANT) return NextSelection(NextMode.MOST_IMPORTANT, 0)
        val total = Recommender.rankByImportance(tasks, now, w).size
        return NextSelection(NextMode.MOST_IMPORTANT, if (total == 0) 0 else (selection.rank + 1).mod(total))
    }

    /** "Shuffle" button: weighted random draw, never repeating the task on screen if there's another. */
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

    /**
     * After the shown task is marked done. Most-important mode keeps its rank so the next task
     * slides into place; shuffle mode draws again.
     */
    fun afterDone(tasks: List<Candidate>, selection: NextSelection, now: Long, w: Weights = Weights()): NextSelection =
        if (selection.mode == NextMode.SHUFFLE) pressShuffle(tasks, selection, now, w = w) else selection
}
