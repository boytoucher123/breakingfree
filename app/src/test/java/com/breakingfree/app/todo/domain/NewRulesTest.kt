package com.breakingfree.app.todo.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewRulesTest {
    private val utc = ZoneOffset.UTC
    // 2001-09-09 01:46:40 UTC, a Sunday.
    private val now = 1_000_000_000_000L
    private val sunday = DayOfWeek.SUNDAY

    private fun c(id: String, priority: Int = 2, inProgress: Boolean = false, unfinished: Boolean = false, days: Int = 0) =
        Candidate(id, priority, null, null, false, inProgress = inProgress, unfinished = unfinished, days = days)

    @Test
    fun nowIsASunday() {
        assertEquals(sunday, Instant.ofEpochMilli(now).atZone(utc).dayOfWeek)
    }

    @Test
    fun unfinishedTasksAreNeverSuggested() {
        val tasks = listOf(c("a", priority = 3, unfinished = true), c("b"))
        assertEquals(listOf("b"), Recommender.startable(tasks, now, utc).map { it.id })
        repeat(50) { assertEquals("b", Recommender.pickWeighted(tasks, now, random = Random(it), zone = utc)!!.id) }
    }

    @Test
    fun weekdayRestrictionBlocksOtherDays() {
        val mondayOnly = Weekdays.bit(DayOfWeek.MONDAY)
        val sundayOnly = Weekdays.bit(sunday)
        val tasks = listOf(c("mon", days = mondayOnly), c("sun", days = sundayOnly), c("any"))
        assertEquals(setOf("sun", "any"), Recommender.startable(tasks, now, utc).map { it.id }.toSet())
        assertEquals(Block.WRONG_DAY, Recommender.blockReason(tasks[0], emptySet(), now, utc))
    }

    @Test
    fun inProgressButBlockedShowsBlocked() {
        val block = Recommender.blockReason(c("x", inProgress = true, days = Weekdays.bit(DayOfWeek.MONDAY)), emptySet(), now, utc)
        assertEquals(DisplayStatus.BLOCKED, DisplayStatus.of(TaskStatus.IN_PROGRESS, false, block))
        assertEquals(DisplayStatus.IN_PROGRESS, DisplayStatus.of(TaskStatus.IN_PROGRESS, false, null))
        assertEquals(DisplayStatus.WAITING, DisplayStatus.of(TaskStatus.TODO, false, block))
        assertEquals(DisplayStatus.UNFINISHED, DisplayStatus.of(TaskStatus.TODO, true, null))
    }

    @Test
    fun inProgressIsWeightedHigherInShuffle() {
        val w = Weights(inProgressBoost = 3.0)
        val plain = c("plain")
        val started = c("started", inProgress = true)
        assertEquals(3.0 * Recommender.weight(plain, now, w), Recommender.weight(started, now, w), 1e-9)
        val rng = Random(7)
        val counts = (1..6000).groupingBy { Recommender.pickWeighted(listOf(plain, started), now, random = rng, w = w, zone = utc)!!.id }.eachCount()
        assertTrue("in-progress should win about 3:1: $counts", counts.getValue("started") > counts.getValue("plain") * 2.5)
    }

    @Test
    fun weightsChangeTheRanking() {
        val high = c("high", priority = 3)
        val due = Candidate("due", 0, now + 3_600_000, null, false)
        assertEquals("due", Recommender.rankByImportance(listOf(high, due), now, zone = utc).first().id)
        val priorityHeavy = Weights(priorityHigh = 20.0)
        assertEquals("high", Recommender.rankByImportance(listOf(high, due), now, priorityHeavy, utc).first().id)
    }

    @Test
    fun weekdayToggleAllSevenMeansAny() {
        var mask = Weekdays.ANY
        DayOfWeek.entries.forEach { mask = Weekdays.toggle(mask, it) }
        assertEquals(Weekdays.ANY, mask)
        assertTrue(Weekdays.allows(Weekdays.ANY, sunday))
        assertFalse(Weekdays.contains(Weekdays.ANY, sunday))
    }

    @Test
    fun tagsNormalizeSpacesAndHyphens() {
        assertEquals("home_chores", Tags.normalize("Home - Chores"))
        assertEquals("deep_work", Tags.normalize("#deep-work"))
        assertEquals("a_b_c", Tags.normalize("  a b-c  "))
        assertEquals("x", Tags.normalize("x_"))
        assertEquals("my_tag_", Tags.typing("My tag-"))
    }

    @Test
    fun atTimeReminderFiresNextAllowedDay() {
        val r = Reminder(ReminderMode.AT_TIME, startMinute = 9 * 60)
        // 01:46 Sunday → 09:00 same day
        assertEquals(LocalDate.of(2001, 9, 9).atTime(9, 0).toInstant(utc).toEpochMilli(), ReminderPlanner.next(r, now, zone = utc))
        // Already fired today → tomorrow
        assertEquals(
            LocalDate.of(2001, 9, 10).atTime(9, 0).toInstant(utc).toEpochMilli(),
            ReminderPlanner.next(r, now, firedOn = LocalDate.of(2001, 9, 9), zone = utc),
        )
        // Wednesdays only
        val wed = r.copy(days = Weekdays.bit(DayOfWeek.WEDNESDAY))
        assertEquals(LocalDate.of(2001, 9, 12).atTime(9, 0).toInstant(utc).toEpochMilli(), ReminderPlanner.next(wed, now, zone = utc))
    }

    @Test
    fun randomReminderLandsInsideTheWindow() {
        val r = Reminder(ReminderMode.RANDOM_WINDOW, startMinute = 10 * 60, endMinute = 12 * 60)
        val start = LocalDate.of(2001, 9, 9).atTime(10, 0).toInstant(utc).toEpochMilli()
        val end = LocalDate.of(2001, 9, 9).atTime(12, 0).toInstant(utc).toEpochMilli()
        repeat(100) {
            val at = ReminderPlanner.next(r, now, zone = utc, random = Random(it))!!
            assertTrue(at in start until end)
        }
        // Inside the window: from now (+1 min) to the end, never in the past.
        val mid = start + 3_600_000
        repeat(100) {
            val at = ReminderPlanner.next(r, mid, zone = utc, random = Random(it))!!
            assertTrue(at > mid && at < end)
        }
        // After the window: tomorrow's window.
        val next = ReminderPlanner.next(r, end + 1, zone = utc)!!
        assertEquals(LocalDate.of(2001, 9, 10), Instant.ofEpochMilli(next).atZone(utc).toLocalDate())
    }

    @Test
    fun reminderOffOrEmptyWindowNeverFires() {
        assertNull(ReminderPlanner.next(Reminder(), now, zone = utc))
        assertNull(ReminderPlanner.next(Reminder(ReminderMode.RANDOM_WINDOW, 600, 600), now, zone = utc))
        assertNotNull(ReminderPlanner.next(Reminder(ReminderMode.AT_TIME, 600), now, zone = utc))
    }
}
