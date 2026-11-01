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

val BluePalette = Palette(
    background = Color(0xFF060A12),
    surface = Color(0xFF0C1322),
    surfaceHigh = Color(0xFF121C31),
    accent = Color(0xFF7FA6DE),
    accentDim = Color(0xFF2D4670),
    text = Color(0xFFD3DEEF),
    textDim = Color(0xFF7D8DA8),
    line = Color(0xFF1D2B45),
)

val LocalPalette = staticCompositionLocalOf { RedPalette }

@Composable
private fun animated(c: Color): Color = animateColorAsState(c, tween(900), label = "palette").value

@Composable
fun BreakingFreeTheme(quiet: Boolean, content: @Composable () -> Unit) {
    val target = if (quiet) BluePalette else RedPalette
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
