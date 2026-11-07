package com.breakingfree.app.roadmap

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.breakingfree.app.ui.Palette
import com.breakingfree.app.ui.IcePalette
import com.breakingfree.app.ui.RedPalette
import kotlin.math.pow

/**
 * A roadmap's colours. The climb runs [origin] → [midway] → [goal] (the end goal); past it, the
 * Beyond runs [beyondStart] → [beyondMid] → [abyss]. Stored as ARGB ints.
 */
data class RoadScheme(
    val name: String,
    val origin: Int,
    val midway: Int,
    val goal: Int,
    val beyondStart: Int,
    val beyondMid: Int,
    val abyss: Int,
) {
    private fun c(argb: Int) = Color(argb)

    /** Colour by proximity to the end goal (t in 0..1), brightened by tier. */
    fun progressColor(t: Float, tier: Tier): Color {
        val e = t.coerceIn(0f, 1f).pow(1.15f)
        val base = if (e < 0.5f) lerp(c(origin), c(midway), e * 2f) else lerp(c(midway), c(goal), (e - 0.5f) * 2f)
        return lerp(base, Color.White, tierBoost(tier))
    }

    /** Past the end goal (u in 0..1), brightened by tier. */
    fun beyondColor(u: Float, tier: Tier): Color {
        val e = u.coerceIn(0f, 1f)
        val base = if (e < 0.4f) lerp(c(beyondStart), c(beyondMid), e / 0.4f) else lerp(c(beyondMid), c(abyss), (e - 0.4f) / 0.6f)
        return lerp(base, lerp(c(beyondStart), Color.White, 0.4f), tierBoost(tier) * 0.9f)
    }

    /** Colour for path position [i] of [n], where [goalIndex] is the end goal's index. */
    fun colorAt(i: Int, n: Int, goalIndex: Int, tier: Tier): Color =
        if (i <= goalIndex) progressColor(if (goalIndex <= 0) 1f else i / goalIndex.toFloat(), tier)
        else beyondColor(if (n - 1 - goalIndex <= 1) 1f else (i - goalIndex - 1) / (n - 2f - goalIndex), tier)

    // Backgrounds and headings, derived so a custom scheme stays coherent.
    val climbFloor: Color get() = lerp(c(origin), Color.Black, 0.88f)
    val beyondSky: Color get() = lerp(c(abyss), Color.Black, 0.82f)
    val beyondHaze: Color get() = lerp(c(beyondMid), Color.Black, 0.7f)
    val beyondTitle: Color get() = lerp(c(beyondStart), c(beyondMid), 0.5f)
    val beyondSubtitle: Color get() = lerp(c(beyondMid), c(abyss), 0.3f)

    /**
     * The app's palette while this roadmap is on screen: accents (the + button, page squares,
     * the line above the tab bar) in the scheme's own colour. Blood keeps the app's red exactly.
     */
    fun palette(): Palette {
        if (this == BLOOD) return RedPalette
        if (this == ICE) return IcePalette
        val accent = c(midway)
        val dark = lerp(c(origin), Color.Black, 0.6f)
        return Palette(
            background = lerp(c(origin), Color.Black, 0.95f),
            surface = lerp(c(origin), Color.Black, 0.9f),
            surfaceHigh = lerp(c(origin), Color.Black, 0.85f),
            accent = accent,
            accentDim = lerp(accent, Color.Black, 0.5f),
            text = lerp(c(goal), Color(0xFFEDE3DC), 0.5f),
            textDim = lerp(lerp(c(midway), Color.Gray, 0.6f), Color.Black, 0.2f),
            line = lerp(dark, Color.Black, 0.5f),
        )
    }

    /** The six editable colours, in order, with their labels. */
    val slots: List<Pair<String, Int>>
        get() = listOf(
            "ORIGIN" to origin, "MIDWAY" to midway, "END GOAL" to goal,
            "BEYOND" to beyondStart, "DEEPER" to beyondMid, "ABYSS" to abyss,
        )

    fun withSlot(index: Int, argb: Int): RoadScheme = when (index) {
        0 -> copy(origin = argb)
        1 -> copy(midway = argb)
        2 -> copy(goal = argb)
        3 -> copy(beyondStart = argb)
        4 -> copy(beyondMid = argb)
        else -> copy(abyss = argb)
    }.copy(name = CUSTOM)

    companion object {
        const val CUSTOM = "CUSTOM"

        val BLOOD = RoadScheme("BLOOD", 0xFF9E000D.toInt(), 0xFFE0303A.toInt(), 0xFFFFF6F3.toInt(), 0xFFD9C2FF.toInt(), 0xFF9B30FF.toInt(), 0xFF5A0FA0.toInt())
        val ICE = RoadScheme("ICE", 0xFF0A2A6B.toInt(), 0xFF2F7DE0.toInt(), 0xFFF2FBFF.toInt(), 0xFFC2F0E8.toInt(), 0xFF1FBF9F.toInt(), 0xFF0B5E4F.toInt())
        val VENOM = RoadScheme("VENOM", 0xFF1E5A00.toInt(), 0xFF6BE01F.toInt(), 0xFFF7FFE8.toInt(), 0xFFFFD6E8.toInt(), 0xFFFF2E7E.toInt(), 0xFF7A0035.toInt())
        val GOLD = RoadScheme("GOLD", 0xFF5E3A00.toInt(), 0xFFD9A21B.toInt(), 0xFFFFF8DC.toInt(), 0xFFC9D4FF.toInt(), 0xFF4D6BFF.toInt(), 0xFF1A2280.toInt())
        val ASH = RoadScheme("ASH", 0xFF3A3A3A.toInt(), 0xFF8C8C8C.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC9C2.toInt(), 0xFFC4001A.toInt(), 0xFF4A0008.toInt())

        val PRESETS = listOf(BLOOD, ICE, VENOM, GOLD, ASH)
    }
}

fun tierBoost(tier: Tier): Float = when (tier) {
    Tier.MINOR -> 0.07f
    Tier.REGULAR -> 0.17f
    Tier.MAJOR -> 0.42f
}
