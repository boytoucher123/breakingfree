package com.breakingfree.app.lattice

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatticeMoodTest {
    private fun at(h: Int, m: Int = 0) = LatticeMoods.at(LocalTime.of(h, m))

    @Test
    fun peaksAtTwoAndIsWeakestInTheAfternoon() {
        assertEquals(1f, at(2).vigor, 1e-6f)
        for (h in 12..17) assertTrue("$h", at(h).vigor <= 0.051f)
        (0..23).forEach { h -> assertTrue(at(h).vigor in 0f..1f) }
    }

    @Test
    fun changesGraduallyAllDay() {
        var prev = at(0).vigor
        for (minute in 1 until 24 * 60) {
            val v = at(minute / 60, minute % 60).vigor
            assertTrue("jump at minute $minute", kotlin.math.abs(v - prev) < 0.01f)
            prev = v
        }
        assertTrue(kotlin.math.abs(at(23, 59).vigor - at(0).vigor) < 0.01f) // wraps smoothly at midnight
    }

    @Test
    fun linesFollowTheClock() {
        assertEquals("The veil is thin...", at(2).line)
        assertEquals("There's nothing here, at least for now.", at(12).line)
        assertEquals("There's nothing here, at least for now.", at(16, 59).line)
        assertEquals("The noise is receding...", at(22).line)
        assertEquals("The noise is receding...", at(0, 30).line)
        assertEquals("The veil is thin...", at(1).line)
    }
}
