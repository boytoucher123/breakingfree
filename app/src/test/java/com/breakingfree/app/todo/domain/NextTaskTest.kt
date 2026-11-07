package com.breakingfree.app.todo.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextTaskTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    // a: due in 1h (most important), b: high priority, c: plain
    private val tasks = listOf(
        Candidate("c", 0, null, null, false),
        Candidate("b", 3, null, null, false),
        Candidate("a", 0, now + hour, null, false),
    )

    @Test
    fun defaultShowsMostImportantWithNextLabel() {
        val sel = NextSelection()
        assertEquals("a", NextTask.resolve(tasks, sel, now).task!!.id)
    }

    @Test
    fun nextMostImportantWalksDownTheRankingAndWraps() {
        var sel = NextSelection()
        val seen = mutableListOf<String>()
        repeat(4) {
            sel = NextTask.pressImportant(tasks, sel, now)
            seen += NextTask.resolve(tasks, sel, now).task!!.id
        }
        assertEquals(listOf("b", "c", "a", "b"), seen)
    }

    @Test
    fun shuffleRestoresMostImportantButton() {
        val shuffled = NextTask.pressShuffle(tasks, NextSelection(), now, Random(1))
        assertEquals(NextMode.SHUFFLE, shuffled.mode)
        // Pressing "Most important" after a shuffle goes back to the top task.
        val back = NextTask.pressImportant(tasks, shuffled, now)
        assertEquals("a", NextTask.resolve(tasks, back, now).task!!.id)
    }

    @Test
    fun shuffledTaskThatIsDoneFallsBackToTop() {
        val sel = NextSelection(NextMode.SHUFFLE, shuffledId = "c")
        val done = tasks.map { if (it.id == "c") it.copy(completed = true) else it }
        assertEquals("a", NextTask.resolve(done, sel, now).task!!.id)
    }

    @Test
    fun emptyListShowsNothing() {
        assertNull(NextTask.resolve(emptyList(), NextSelection(), now).task)
        assertEquals(0, NextTask.pressImportant(emptyList(), NextSelection(), now).rank)
    }
}
