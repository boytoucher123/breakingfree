package com.breakingfree.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.breakingfree.app.R

/** Typefaces: gothic/theological script vs. oppressive/technical, plus the quiet serif of the Lattice. */
object Fonts {
    val Gothic = FontFamily(Font(R.font.unifraktur_cook, FontWeight.Bold))
    val GothicAlt = FontFamily(Font(R.font.pirata_one))
    val Oppressive = FontFamily(Font(R.font.anton))
    val Technical = FontFamily(Font(R.font.share_tech_mono))
    val Quiet = FontFamily(
        Font(R.font.cormorant, FontWeight.Normal),
        Font(R.font.cormorant_italic, FontWeight.Normal, FontStyle.Italic),
    )
}

@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentDim: Color,
    val text: Color,
    val textDim: Color,
    val line: Color,
)

val RedPalette = Palette(
    background = Color(0xFF080606),
    surface = Color(0xFF130D0D),
    surfaceHigh = Color(0xFF1E1212),
    accent = Color(0xFFC4001A),
    accentDim = Color(0xFF6E0010),
    text = Color(0xFFEDE3DC),
    textDim = Color(0xFF8F7C78),
    line = Color(0xFF3A1416),
)

/** The Lattice at its most awake (2-4am): strong, glowing blue. */
val BluePalette = Palette(
    background = Color(0xFF04102E),
    surface = Color(0xFF0A1838),
    surfaceHigh = Color(0xFF102248),
    accent = Color(0xFF6FA8FF),
    accentDim = Color(0xFF2A5096),
    text = Color(0xFFB8D2FA),
    textDim = Color(0xFF6A84B4),
    line = Color(0xFF1C3060),
)

/** The Lattice in its dead zone (9am-7pm): dark, earthy stone. No blue. */
val StonePalette = Palette(
    background = Color(0xFF0F0E0C),
    surface = Color(0xFF181613),
    surfaceHigh = Color(0xFF221F1B),
    accent = Color(0xFF9A8E7E),
    accentDim = Color(0xFF5A5148),
    text = Color(0xFFB8AE9F),
    textDim = Color(0xFF7A7064),
    line = Color(0xFF2C2824),
)

fun lerpPalette(a: Palette, b: Palette, t: Float): Palette {
    val f = t.coerceIn(0f, 1f)
    return Palette(
        background = lerp(a.background, b.background, f),
        surface = lerp(a.surface, b.surface, f),
        surfaceHigh = lerp(a.surfaceHigh, b.surfaceHigh, f),
        accent = lerp(a.accent, b.accent, f),
        accentDim = lerp(a.accentDim, b.accentDim, f),
        text = lerp(a.text, b.text, f),
        textDim = lerp(a.textDim, b.textDim, f),
        line = lerp(a.line, b.line, f),
    )
}

/** The Lattice palette at a given vigor: stone when dead, blue when awake. */
fun latticePalette(vigor: Float) = lerpPalette(StonePalette, BluePalette, vigor)

val LocalPalette = staticCompositionLocalOf { RedPalette }

@Composable
private fun animated(c: Color): Color = animateColorAsState(c, tween(900), label = "palette").value

@Composable
fun BreakingFreeTheme(target: Palette = RedPalette, content: @Composable () -> Unit) {
    val p = Palette(
        background = animated(target.background),
        surface = animated(target.surface),
        surfaceHigh = animated(target.surfaceHigh),
        accent = animated(target.accent),
        accentDim = animated(target.accentDim),
        text = animated(target.text),
        textDim = animated(target.textDim),
        line = animated(target.line),
    )
    val scheme = darkColorScheme(
        primary = p.accent,
        onPrimary = p.background,
        secondary = p.accentDim,
        background = p.background,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textDim,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHigh,
        outline = p.line,
        error = Color(0xFFFF4D5E),
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
