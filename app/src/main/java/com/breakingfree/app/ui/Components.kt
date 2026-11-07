package com.breakingfree.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakingfree.app.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/* The burning tree that replaced the spinning coin lives in BurningTree.kt */

/**
 * A screen title in the Lattice's voice: Cormorant italic with a soft blue glow that breathes
 * slowly. (It used to convulse between gothic and technical faces; the app is quieter now.)
 * [technicalText] and [startGothic] are kept so callers needn't change.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ShiftingTitle(
    gothicText: String,
    technicalText: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    color: Color = LocalPalette.current.text,
    glitchColor: Color = LocalPalette.current.accent,
    startGothic: Boolean = true,
    textAlign: TextAlign = TextAlign.Center,
) {
    val breath by rememberInfiniteTransition(label = "title").animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "breath",
    )
    val glow = LocalGlow.current
    Text(
        gothicText,
        modifier = modifier,
        color = color,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontFamily = Fonts.QuietItalic,
            fontSize = fontSize,
            textAlign = textAlign,
            shadow = glow?.let { it.copy(color = it.color.copy(alpha = it.color.alpha * breath)) },
        ),
    )
}

/** Faint CRT scanlines laid over oppressive screens. */
@Composable
fun Scanlines(modifier: Modifier = Modifier, color: Color = Color.Black.copy(alpha = 0.22f)) {
    Canvas(modifier) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textDim,
    fontSize: TextUnit = 12.sp,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        // Cormorant runs small next to the old monospace, so labels are scaled up to match.
        style = TextStyle(fontFamily = Fonts.Quiet, fontSize = fontSize * 1.25f, letterSpacing = 1.8.sp),
    )
}

@Composable
fun TagChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .border(1.dp, if (selected) p.accent else p.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        MonoLabel(text, color = if (selected) p.accent else p.textDim)
    }
}
