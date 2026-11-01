package com.breakingfree.app.ui

import androidx.compose.foundation.Canvas
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

@Composable
fun FistLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "Breaking Free logo",
        modifier = modifier.size(size),
    )
}

/**
 * A title that periodically convulses between gothic script and the oppressive
 * technical face, with a brief red/white chromatic glitch while it switches.
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
    var glitch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(3200, 6500))
            glitch = true
            repeat(5) {
                delay(Random.nextLong(40, 110))
                gothic = !gothic
            }
            delay(90)
            glitch = false
        }
    }
    val text = if (gothic) gothicText else technicalText
    val style = TextStyle(
        fontFamily = if (gothic) Fonts.Gothic else Fonts.Oppressive,
        fontSize = if (gothic) fontSize * 1.05f else fontSize,
        letterSpacing = if (gothic) 0.sp else 2.sp,
        textAlign = textAlign,
    )
    Box(modifier) {
        if (glitch) {
            Text(text, style = style, color = glitchColor.copy(alpha = 0.8f), maxLines = 1,
                modifier = Modifier.padding(start = 3.dp))
            Text(text, style = style, color = Color.White.copy(alpha = 0.35f), maxLines = 1,
                modifier = Modifier.padding(end = 3.dp, top = 1.dp))
        }
        Text(text, style = style, color = color, maxLines = 1)
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
