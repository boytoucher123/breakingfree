package com.breakingfree.app.lattice

import java.time.LocalTime

/**
 * How alive the Lattice is at a given time of day. [vigor] runs from 0 (noon to 5pm: dim, almost
 * still) to 1 (around 2am: bright, strong blue, moving at full speed). [line] is the text under
 * the title.
 */
data class LatticeMood(val vigor: Float, val line: String)

object LatticeMoods {

    /** (hour of day, vigor) points; the curve is eased between them and wraps at midnight. */
    private val CURVE = listOf(
        0.0 to 0.88f,
        2.0 to 1.0f,
        3.5 to 0.95f,
        5.0 to 0.75f,
        7.0 to 0.5f,
        9.0 to 0.3f,
        11.0 to 0.14f,
        12.0 to 0.05f,
        17.0 to 0.05f,
        18.5 to 0.2f,
        20.0 to 0.4f,
        22.0 to 0.65f,
        24.0 to 0.88f,
    )

    /** (from hour, line), in order through the day. Each line runs until the next one starts. */
    private val LINES = listOf(
        1 to "The veil is thin...",
        4 to "Dawn is stitching the veil shut...",
        7 to "The signal is fading...",
        10 to "Only static now...",
        12 to "There's nothing here, at least for now.",
        17 to "Something stirs beneath the surface...",
        19 to "The threads are waking...",
        22 to "The noise is receding...",
    )

    fun at(time: LocalTime): LatticeMood {
        val hour = time.hour + time.minute / 60.0 + time.second / 3600.0
        return LatticeMood(vigor(hour), line(time.hour))
    }

    fun now(): LatticeMood = at(LocalTime.now())

    internal fun vigor(hour: Double): Float {
        val h = hour.mod(24.0)
        for (i in 0 until CURVE.size - 1) {
            val (h0, v0) = CURVE[i]
            val (h1, v1) = CURVE[i + 1]
            if (h in h0..h1) {
                val t = ((h - h0) / (h1 - h0)).toFloat()
                val eased = t * t * (3f - 2f * t)
                return v0 + (v1 - v0) * eased
            }
        }
        return CURVE.last().second
    }

    internal fun line(hour: Int): String =
        // Before 1am still belongs to the late-night line that started at 10pm.
        LINES.lastOrNull { hour >= it.first }?.second ?: LINES.last().second
}
