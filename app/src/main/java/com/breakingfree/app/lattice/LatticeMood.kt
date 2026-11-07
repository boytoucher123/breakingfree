package com.breakingfree.app.lattice

import java.time.LocalTime

/**
 * How alive the Lattice is at a given time of day.
 *
 * - [vigor] (0..1): how blue and how fast. 0 in the dead zone (9am-7pm: dull stone, almost
 *   still, no blue). Hints of blue 7-9pm, rising 9-11pm, fully blue from midnight to 4am.
 * - [glow] (0..1): text glow. A little from midnight, strong 2-4am.
 * - [waves] (0..1): the slow distortion field over the background. Only 2-4am, at full.
 * - [open]: whether the questions can be answered. Closed in the dead zone.
 * - [line]: the text under the title.
 */
data class LatticeMood(val vigor: Float, val line: String, val glow: Float = 0f, val waves: Float = 0f, val open: Boolean = true)

object LatticeMoods {

    private val BLUE = listOf(
        0.0 to 1.0f,
        4.0 to 1.0f,
        6.0 to 0.55f,
        8.0 to 0.15f,
        9.0 to 0.0f,
        19.0 to 0.0f,
        21.0 to 0.15f,
        23.0 to 0.5f,
        24.0 to 1.0f,
    )

    private val GLOW = listOf(
        0.0 to 0.3f,
        1.0 to 0.3f,
        2.0 to 1.0f,
        4.0 to 1.0f,
        5.5 to 0.0f,
        23.0 to 0.0f,
        24.0 to 0.3f,
    )

    private val WAVES = listOf(
        0.0 to 0.0f,
        1.0 to 0.0f,
        2.0 to 1.0f,
        4.0 to 1.0f,
        6.0 to 0.0f,
        24.0 to 0.0f,
    )

    /** (from hour, line), in order through the day. Each line runs until the next one starts. */
    private val LINES = listOf(
        1 to "The veil is thinning...",
        2 to "The veil is thin...",
        4 to "Dawn is stitching the veil shut...",
        7 to "The signal is fading...",
        9 to "There's nothing here, at least for now.",
        19 to "Something stirs beneath the surface...",
        21 to "The threads are waking...",
        22 to "The noise is receding...",
    )

    /** The questions are closed from 9am until 7pm. */
    private const val CLOSE_HOUR = 9
    private const val OPEN_HOUR = 19

    fun at(time: LocalTime): LatticeMood {
        val hour = time.hour + time.minute / 60.0 + time.second / 3600.0
        return LatticeMood(
            vigor = curve(BLUE, hour),
            line = line(time.hour),
            glow = curve(GLOW, hour),
            waves = curve(WAVES, hour),
            open = time.hour !in CLOSE_HOUR until OPEN_HOUR,
        )
    }

    fun now(): LatticeMood = at(LocalTime.now())

    internal fun vigor(hour: Double): Float = curve(BLUE, hour)

    /** Eased between points; wraps at midnight. */
    private fun curve(points: List<Pair<Double, Float>>, hour: Double): Float {
        val h = hour.mod(24.0)
        for (i in 0 until points.size - 1) {
            val (h0, v0) = points[i]
            val (h1, v1) = points[i + 1]
            if (h in h0..h1) {
                val t = ((h - h0) / (h1 - h0)).toFloat()
                val eased = t * t * (3f - 2f * t)
                return v0 + (v1 - v0) * eased
            }
        }
        return points.last().second
    }

    internal fun line(hour: Int): String =
        // Before 1am still belongs to the late-night line that started at 10pm.
        LINES.lastOrNull { hour >= it.first }?.second ?: LINES.last().second
}
