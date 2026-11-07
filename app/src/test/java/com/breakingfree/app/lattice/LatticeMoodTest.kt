package com.breakingfree.app.lattice

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatticeMoodTest {
    private fun at(h: Int, m: Int = 0) = LatticeMoods.at(LocalTime.of(h, m))

    @Test
    fun deadNineToSevenAndQuestionsClosed() {
        for (h in 9..18) {
            assertEquals("$h", 0f, at(h).vigor, 1e-6f)
            assertEquals(0f, at(h).glow, 1e-6f)
            assertEquals(0f, at(h).waves, 1e-6f)
            assertFalse(at(h).open)
        }
        assertTrue(at(19).open)
        assertTrue(at(8, 59).open)
    }

    @Test
    fun nightBuildsUpInStages() {
        assertTrue(at(20).vigor in 0.01f..0.2f)       // hints
        assertTrue(at(22).vigor in 0.2f..0.6f)        // ramping
        assertEquals(1f, at(0, 30).vigor, 1e-6f)      // fully blue
        assertEquals(0.3f, at(0, 30).glow, 1e-6f)     // glowing a little
        assertEquals(0f, at(0, 30).waves, 1e-6f)
        for (h in 2..4) {
            assertEquals(1f, at(h).glow, 1e-6f)
            assertEquals(1f, at(h).waves, 1e-6f)
        }
    }

    @Test
    fun changesGraduallyAllDay() {
        var prev = at(0)
        for (minute in 1 until 24 * 60) {
            val cur = at(minute / 60, minute % 60)
            assertTrue("vigor jump at $minute", kotlin.math.abs(cur.vigor - prev.vigor) < 0.02f)
            assertTrue("glow jump at $minute", kotlin.math.abs(cur.glow - prev.glow) < 0.02f)
            assertTrue("waves jump at $minute", kotlin.math.abs(cur.waves - prev.waves) < 0.03f)
            prev = cur
        }
    }

    @Test
    fun linesFollowTheClock() {
        assertEquals("The veil is thin...", at(2).line)
        assertEquals("There's nothing here, at least for now.", at(9).line)
        assertEquals("There's nothing here, at least for now.", at(18, 59).line)
        assertEquals("The noise is receding...", at(0, 30).line)
        assertEquals("The veil is thinning...", at(1).line)
    }
}
