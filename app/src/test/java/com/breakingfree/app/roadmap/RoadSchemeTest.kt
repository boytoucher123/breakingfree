package com.breakingfree.app.roadmap

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoadSchemeTest {
    @Test
    fun climbRunsFromOriginToGoalAndBeyondToAbyss() {
        val s = RoadScheme.ICE
        // Minor nodes get the smallest tier boost; check the ends are in the scheme's own hues.
        val start = s.colorAt(0, 6, 3, Tier.MINOR)
        val beyondEnd = s.colorAt(5, 6, 3, Tier.MINOR)
        assertNotEquals(start, beyondEnd)
        assertEquals(Color(s.origin).blue > Color(s.origin).red, start.blue > start.red)
    }

    @Test
    fun editingASlotMakesItCustom() {
        val s = RoadScheme.BLOOD.withSlot(4, 0xFF00FF00.toInt())
        assertEquals(RoadScheme.CUSTOM, s.name)
        assertEquals(0xFF00FF00.toInt(), s.beyondMid)
        assertEquals(RoadScheme.BLOOD.origin, s.origin)
        assertEquals(6, s.slots.size)
    }

    @Test
    fun presetNamesAreUnique() {
        assertEquals(RoadScheme.PRESETS.size, RoadScheme.PRESETS.map { it.name }.toSet().size)
    }
}
