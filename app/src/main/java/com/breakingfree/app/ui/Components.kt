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

/* SpinningCoin lives in Coin.kt */

/**
 * A title that convulses between gothic script and the oppressive technical face.
 * Between full swaps it twitches with smaller chromatic glitches. Both faces are laid out
 * (one invisibly) so the title always occupies the same space and nothing around it moves.
 */
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
    var gothic by remember { mutableStateOf(startGothic) }
    var glitch by remember { mutableIntStateOf(0) } // 0 = clean, otherwise a seed for the jitter
    LaunchedEffect(Unit) {
        launch {
            while (true) {
                delay(Random.nextLong(900, 2600))
                repeat(Random.nextInt(1, 4)) {
                    glitch = Random.nextInt(1, Int.MAX_VALUE)
                    delay(Random.nextLong(40, 120))
                    glitch = 0
                    delay(Random.nextLong(30, 110))
                }
            }
        }
        while (true) {
            delay(Random.nextLong(4200, 7800))
            repeat(5) {
                glitch = Random.nextInt(1, Int.MAX_VALUE)
                delay(Random.nextLong(40, 110))
                gothic = !gothic
            }
            delay(90)
            glitch = 0
        }
    }
    val gothicStyle = TextStyle(fontFamily = Fonts.Gothic, fontSize = fontSize * 1.05f, textAlign = textAlign)
    val techStyle = TextStyle(fontFamily = Fonts.Oppressive, fontSize = fontSize, letterSpacing = 2.sp, textAlign = textAlign)
    val text = if (gothic) gothicText else technicalText
    val style = if (gothic) gothicStyle else techStyle
    val align = when (textAlign) {
        TextAlign.Start, TextAlign.Left -> Alignment.CenterStart
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        else -> Alignment.Center
    }
    Box(modifier, contentAlignment = align) {
        // Space reservation: the larger of the two faces decides the size, always.
        Text(gothicText, style = gothicStyle, maxLines = 1, softWrap = false, modifier = Modifier.alpha(0f))
        Text(technicalText, style = techStyle, maxLines = 1, softWrap = false, modifier = Modifier.alpha(0f))
        if (glitch != 0) {
            val r = Random(glitch)
            val dx = r.nextInt(2, 7).dp
            val dy = r.nextInt(-2, 3).dp
            Text(text, style = style, color = glitchColor.copy(alpha = 0.85f), maxLines = 1, softWrap = false,
                modifier = Modifier.offset(x = dx, y = dy))
            Text(text, style = style, color = Color.White.copy(alpha = 0.3f), maxLines = 1, softWrap = false,
                modifier = Modifier.offset(x = -dx, y = -dy))
            Text(text, style = style, color = color, maxLines = 1, softWrap = false,
                modifier = Modifier.offset(x = r.nextInt(-2, 3).dp))
        } else {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
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
        style = TextStyle(fontFamily = Fonts.Technical, fontSize = fontSize, letterSpacing = 1.5.sp),
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
