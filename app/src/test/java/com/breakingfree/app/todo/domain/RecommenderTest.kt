package com.breakingfree.app.todo.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun task(
        id: Long,
        priority: Int = 0,
        dueAt: Long? = null,
        startAt: Long? = null,
        completed: Boolean = false,
        blockedBy: Set<Long> = emptySet(),
    ) = Candidate(id.toString(), priority, dueAt, startAt, completed, blockedBy.map { it.toString() }.toSet())

    @Test
    fun deadlineTomorrowBeatsHighPriorityWithNoDeadline() {
        val ranked = Recommender.rankByImportance(
            listOf(task(1, priority = 3), task(2, priority = 0, dueAt = now + 20 * hour)),
            now,
        )
        assertEquals(listOf(2L, 1L), ranked.map { it.id.toLong() })
    }

    @Test
    fun higherPriorityWinsWhenDeadlinesMatch() {
        val ranked = Recommender.rankByImportance(
            listOf(task(1, priority = 1, dueAt = now + 5 * day), task(2, priority = 3, dueAt = now + 5 * day)),
            now,
        )
        assertEquals(listOf(2L, 1L), ranked.map { it.id.toLong() })
    }

    @Test
    fun overdueRanksAboveDueSoon() {
        val ranked = Recommender.rankByImportance(
            listOf(task(1, dueAt = now + 2 * hour), task(2, dueAt = now - hour)),
            now,
        )
        assertEquals(listOf(2L, 1L), ranked.map { it.id.toLong() })
    }

    @Test
    fun unstartableTasksAreExcluded() {
        val tasks = listOf(
            task(1, completed = true),
            task(2, startAt = now + day, priority = 3), // not available yet
            task(3, blockedBy = setOf(4)), // waits on open task 4
            task(4),
            task(5, blockedBy = setOf(1)), // its prerequisite is done
        )
        assertEquals(setOf(4L, 5L), Recommender.startable(tasks, now).map { it.id.toLong() }.toSet())
        repeat(200) { seed ->
            val pick = Recommender.pickWeighted(tasks, now, random = Random(seed))!!
            assertTrue(pick.id.toLong() in setOf(4L, 5L))
        }
    }

    @Test
    fun weightedPickFavoursImportantTasks() {
        val tasks = listOf(task(1), task(2, priority = 3, dueAt = now + hour))
        val rng = Random(42)
        val counts = (1..5000).groupingBy { Recommender.pickWeighted(tasks, now, random = rng)!!.id.toLong() }.eachCount()
        assertTrue("important task should win most draws: $counts", counts.getValue(2L) > counts.getValue(1L) * 5)
        assertTrue("unimportant task should still come up: $counts", counts.getValue(1L) > 0)
    }

    @Test
    fun shuffleAvoidsCurrentTaskWhenPossible() {
        val tasks = listOf(task(1, priority = 3), task(2))
        repeat(100) { seed ->
            assertNotEquals(1L, Recommender.pickWeighted(tasks, now, avoidId = "1", random = Random(seed))!!.id.toLong())
        }
        assertEquals(1L, Recommender.pickWeighted(listOf(task(1)), now, avoidId = "1")!!.id.toLong())
    }

    @Test
    fun nothingToDoGivesNull() {
        assertNull(Recommender.pickWeighted(listOf(task(1, completed = true)), now))
        assertTrue(Recommender.rankByImportance(emptyList(), now).isEmpty())
    }
}
