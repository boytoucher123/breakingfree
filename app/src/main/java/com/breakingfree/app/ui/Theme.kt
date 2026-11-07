package com.breakingfree.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.breakingfree.app.R

/**
 * Typefaces. The whole app now speaks in the Lattice's quiet serif (Cormorant). The old names are
 * kept as roles: titles (Gothic, GothicAlt) take the italic, everything else the upright face.
 */
object Fonts {
    val Quiet = FontFamily(
        Font(R.font.cormorant, FontWeight.Normal),
        Font(R.font.cormorant_italic, FontWeight.Normal, FontStyle.Italic),
    )
    /** Cormorant italic only, so titles are italic without having to ask. */
    val QuietItalic = FontFamily(Font(R.font.cormorant_italic, FontWeight.Normal))
    val Gothic = QuietItalic
    val GothicAlt = QuietItalic
    val Oppressive = Quiet
    val Technical = Quiet
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

/**
 * The app's colours: the blue of the Ice roadmap scheme (origin 0A2A6B, midway 2F7DE0, goal F2FBFF),
 * kept soft rather than white so it sits like the Lattice at night.
 */
val IcePalette = Palette(
    background = Color(0xFF040C1F),
    surface = Color(0xFF081732),
    surfaceHigh = Color(0xFF0D2045),
    accent = Color(0xFF2F7DE0),
    accentDim = Color(0xFF1A4A8C),
    text = Color(0xFFC4DAF5),
    textDim = Color(0xFF7891B8),
    line = Color(0xFF15294D),
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

val LocalPalette = staticCompositionLocalOf { IcePalette }

/** The faint blue glow the Lattice gives its text, for titles and headers across the app. */
val LocalGlow = staticCompositionLocalOf<Shadow?> { null }

fun glowShadow(strength: Float, color: Color = Color(0xFF5CA8FF)): Shadow? {
    val g = strength.coerceIn(0f, 1f)
    if (g <= 0.01f) return null
    return Shadow(color = color.copy(alpha = 0.95f * g), offset = Offset.Zero, blurRadius = 6f + 26f * g)
}

/** Every Material text style in Cormorant, a touch larger since it runs small. */
private val QuietTypography: Typography = Typography().run {
    fun TextStyle.q() = copy(fontFamily = Fonts.Quiet, fontSize = fontSize * 1.12f, letterSpacing = 0.3.sp)
    copy(
        displayLarge = displayLarge.q(), displayMedium = displayMedium.q(), displaySmall = displaySmall.q(),
        headlineLarge = headlineLarge.q(), headlineMedium = headlineMedium.q(), headlineSmall = headlineSmall.q(),
        titleLarge = titleLarge.q(), titleMedium = titleMedium.q(), titleSmall = titleSmall.q(),
        bodyLarge = bodyLarge.q(), bodyMedium = bodyMedium.q(), bodySmall = bodySmall.q(),
        labelLarge = labelLarge.q(), labelMedium = labelMedium.q(), labelSmall = labelSmall.q(),
    )
}

@Composable
private fun animated(c: Color): Color = animateColorAsState(c, tween(900), label = "palette").value

@Composable
fun BreakingFreeTheme(target: Palette = IcePalette, glow: Float = 0.35f, content: @Composable () -> Unit) {
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
    CompositionLocalProvider(LocalPalette provides p, LocalGlow provides glowShadow(glow, lerp(p.accent, Color.White, 0.25f))) {
        MaterialTheme(colorScheme = scheme, typography = QuietTypography, content = content)
    }
}
