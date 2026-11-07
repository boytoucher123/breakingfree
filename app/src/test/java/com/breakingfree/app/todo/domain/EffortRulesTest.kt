package com.breakingfree.app.todo.domain

import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interest, size, difficulty and the Easy button. */
class EffortRulesTest {
    private val utc = ZoneOffset.UTC
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun c(id: String, dueAt: Long? = null, interest: Int = 0, size: Int = 0, difficulty: Int = 0) =
        Candidate(id, 2, dueAt, null, false, interest = interest, size = size, difficulty = difficulty)

    @Test
    fun sizeScalesOnlyTheDeadlinePart() {
        val w = Weights()
        val due = now + 2 * hour
        val medium = Recommender.score(c("m", due, size = 3), now, w)
        val huge = Recommender.score(c("h", due, size = 5), now, w)
        val tiny = Recommender.score(c("t", due, size = 1), now, w)
        assertEquals(w.priorityMedium + w.dueSoon, medium, 1e-9)
        assertEquals(w.priorityMedium + w.dueSoon * 2, huge, 1e-9)
        assertEquals(w.priorityMedium + w.dueSoon * 0.5, tiny, 1e-9)
        // No deadline: size does nothing.
        assertEquals(Recommender.score(c("a", size = 1), now, w), Recommender.score(c("b", size = 5), now, w), 1e-9)
    }

    @Test
    fun sizeEffectZeroIgnoresSize() {
        val w = Weights(sizeEffect = 0.0)
        val due = now + 2 * hour
        assertEquals(Recommender.score(c("a", due, size = 1), now, w), Recommender.score(c("b", due, size = 5), now, w), 1e-9)
    }

    @Test
    fun interestRaisesDrawWeight() {
        val w = Weights()
        val bored = Recommender.weight(c("a", interest = 0), now, w)
        val keen = Recommender.weight(c("b", interest = 5), now, w)
        val loath = Recommender.weight(c("c", interest = -5), now, w)
        assertEquals(bored * (1 + w.interestBoost), keen, 1e-9)
        assertEquals(bored * (1 - w.interestBoost), loath, 1e-9)
        // Never below 0.1 however strong the effect.
        assertEquals(bored * 0.1, Recommender.weight(c("d", interest = -5), now, Weights(interestBoost = 2.0)), 1e-9)
        // But not importance: Most important stays predictable.
        assertEquals(Recommender.score(c("a", interest = 0), now, w), Recommender.score(c("b", interest = 5), now, w), 1e-9)
    }

    @Test
    fun easyOnlyPicksSmallSizedTasks() {
        val tasks = listOf(c("unset"), c("medium", size = 3), c("small", size = 2), c("tiny", size = 1))
        assertEquals(setOf("small", "tiny"), Recommender.easyPool(tasks, now, zone = utc).map { it.id }.toSet())
        repeat(50) {
            val pick = Recommender.pickEasy(tasks, now, random = Random(it), zone = utc)!!.id
            assertTrue(pick == "small" || pick == "tiny")
        }
        assertEquals(
            setOf("small", "tiny", "medium"),
            Recommender.easyPool(tasks, now, Weights(easyMaxSize = 3.0), utc).map { it.id }.toSet(),
        )
    }

    @Test
    fun easierAndSmallerComeUpMoreOften() {
        val tasks = listOf(c("tinyTrivial", size = 1, difficulty = 1), c("smallHard", size = 2, difficulty = 4))
        val counts = (0 until 2000).map { Recommender.pickEasy(tasks, now, random = Random(it), zone = utc)!!.id }.groupingBy { it }.eachCount()
        // Weights 100 vs 36.
        assertTrue(counts.getValue("tinyTrivial") > counts.getValue("smallHard") * 2)
    }

    @Test
    fun easyButtonCyclesAndRestoresMostImportant() {
        val tasks = listOf(c("a", size = 1), c("b", size = 2), c("big", size = 5))
        var sel = NextTask.pressEasy(tasks, NextSelection(), now, Random(1))
        assertEquals(NextMode.EASY, sel.mode)
        val first = NextTask.resolve(tasks, sel, now).task!!.id
        sel = NextTask.pressEasy(tasks, sel, now, Random(2))
        val second = NextTask.resolve(tasks, sel, now).task!!.id
        assertTrue(first != second && first != "big" && second != "big")
        sel = NextTask.pressImportant(tasks, sel, now)
        assertEquals(NextSelection(NextMode.MOST_IMPORTANT, 0), sel)
    }

    @Test
    fun easyWithNothingSmallShowsNothingButKeepsTheCount() {
        val tasks = listOf(c("big", size = 5), c("unset"))
        val sel = NextTask.pressEasy(tasks, NextSelection(), now)
        val r = NextTask.resolve(tasks, sel, now)
        assertNull(r.task)
        assertEquals(2, r.total)
    }

    @Test
    fun timeWindowBlocksOutsideItsHours() {
        // now is 01:46 UTC.
        fun w(start: Int, end: Int) = Candidate("t", 2, null, null, false, windowStart = start, windowEnd = end)
        assertNull(Recommender.blockReason(w(60, 120), emptySet(), now, utc))          // 01:00-02:00
        assertEquals(Block.WRONG_TIME, Recommender.blockReason(w(9 * 60, 17 * 60), emptySet(), now, utc))
        assertNull(Recommender.blockReason(w(22 * 60, 2 * 60), emptySet(), now, utc))  // wraps midnight
        assertNull(Recommender.blockReason(w(-1, -1), emptySet(), now, utc))
        assertEquals("09:00–17:00", onlyOnLabel(0, 9 * 60, 17 * 60))
    }

    @Test
    fun modeNamesRoundTrip() {
        NextMode.entries.forEach { assertEquals(it, NextMode.of(it.name)) }
        assertEquals(NextMode.MOST_IMPORTANT, NextMode.of(null))
        assertEquals(NextMode.MOST_IMPORTANT, NextMode.of("bogus"))
    }
}
