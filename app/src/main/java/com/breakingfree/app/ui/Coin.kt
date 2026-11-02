package com.breakingfree.app.ui

import android.graphics.Paint
import android.graphics.Path as NativePath
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import com.breakingfree.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private val CoinRed = Color(0xFFB3000C)
private val CoinBlack = Color(0xFF0A0606)

/** Proportions, as fractions of the coin's diameter (matching the 100-unit ic_coin vector). */
private const val R_FACE = 0.47f
private const val HALF_THICK = 0.045f

private const val RIM_TEXT = "NON SERVIAM · ANNO DOMINI MMXXX · MEMENTO MORI · TEMPUS FUGIT · "
private val NUMERALS = listOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/**
 * The coin, spinning on its vertical axis like it was just flipped into the air.
 * It's drawn as a short cylinder: the hidden face's offset plus a shaded, reeded edge band
 * give it real thickness as it turns. Front: goat skull on the inverted star. Back: a doomsday
 * dial — Latin around the rim, Roman hours, the Leviathan cross at the centre.
 */
@Composable
fun SpinningCoin(size: Dp, modifier: Modifier = Modifier, periodMs: Int = 5200) {
    val angle by rememberInfiniteTransition(label = "coin").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "coin",
    )
    val front = painterResource(R.drawable.ic_coin)
    val context = LocalContext.current
    val paints = remember {
        val serif = runCatching { ResourcesCompat.getFont(context, R.font.cormorant) }.getOrNull() ?: Typeface.SERIF
        BackPaints(
            rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = serif; color = CoinRed.toArgb(); isFakeBoldText = true
            },
            numeral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = serif; color = CoinRed.toArgb(); isFakeBoldText = true; textAlign = Paint.Align.CENTER
            },
        )
    }

    Canvas(modifier.size(size).semantics { contentDescription = "BREAKING FREE coin" }) {
        val d = this.size.minDimension
        val c = center
        val r = d * R_FACE
        val h = d * HALF_THICK
        val th = Math.toRadians(angle.toDouble())
        val s = sin(th).toFloat()
        val k = cos(th).toFloat()           // how squarely the face is turned to us
        val a = r * abs(k)                  // the face's horizontal semi-axis on screen
        val frontShowing = k >= 0f
        val xFront = c.x + h * s
        val xBack = c.x - h * s
        val xVisible = if (frontShowing) xFront else xBack
        val xHidden = if (frontShowing) xBack else xFront

        // ---- the edge: the face swept through the coin's thickness ----
        val edge = Brush.verticalGradient(
            listOf(Color(0xFF140003), Color(0xFF4A060C), Color(0xFF9A1A22), Color(0xFF3A0408), Color(0xFF0E0002)),
            startY = c.y - r, endY = c.y + r,
        )
        drawOval(edge, topLeft = Offset(xHidden - a, c.y - r), size = Size(2 * a, 2 * r))
        val left = min(xHidden, xVisible)
        val right = max(xHidden, xVisible)
        if (right - left > 0.5f) {
            drawRect(edge, topLeft = Offset(left, c.y - r), size = Size(right - left, 2 * r))
            // Reeding: ridges running across the thickness, bunching up towards top and bottom.
            val side = sign(xHidden - xVisible)
            val ridges = 46
            for (i in 1 until ridges) {
                val phi = -PI / 2 + PI * i / ridges
                val y = c.y + r * sin(phi).toFloat()
                val w = cos(phi).toFloat()
                val off = side * a * w
                drawLine(
                    Color.Black.copy(alpha = 0.45f),
                    Offset(xHidden + off, y), Offset(xVisible + off, y),
                    strokeWidth = max(1f, d * 0.004f),
                )
            }
        }

        // ---- the visible face, foreshortened ----
        withTransform({
            translate(left = xVisible - c.x)
            scale(scaleX = max(abs(k), 0.001f), scaleY = 1f, pivot = c)
        }) {
            if (frontShowing) {
                with(front) { draw(Size(d, d)) }
            } else {
                drawCoinBack(d, paints)
            }
            // Light falls off as it turns away; a sheen slides across as it spins.
            drawCircle(Color.Black.copy(alpha = 0.5f * (1f - abs(k))), radius = r, center = c)
            val sheenX = c.x + r * 1.6f * s
            drawCircle(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent),
                    start = Offset(sheenX - r * 0.5f, c.y - r), end = Offset(sheenX + r * 0.5f, c.y + r),
                ),
                radius = r, center = c,
            )
        }
        // A bright lip where face meets edge.
        drawOval(
            CoinRed.copy(alpha = 0.35f), topLeft = Offset(xVisible - a, c.y - r), size = Size(2 * a, 2 * r),
            style = Stroke(width = max(1f, d * 0.006f)),
        )
    }
}

private class BackPaints(val rim: Paint, val numeral: Paint)

/** The reverse, drawn flat into a d×d box. */
private fun DrawScope.drawCoinBack(d: Float, paints: BackPaints) {
    val c = center
    fun rr(f: Float) = d * f

    drawCircle(CoinBlack, radius = rr(R_FACE), center = c)
    // Beaded border.
    val beads = 72
    for (i in 0 until beads) {
        val t = 2 * PI * i / beads
        drawCircle(CoinRed, radius = rr(0.0055f),
            center = Offset(c.x + rr(0.445f) * cos(t).toFloat(), c.y + rr(0.445f) * sin(t).toFloat()))
    }
    drawCircle(CoinRed, radius = rr(0.418f), center = c, style = Stroke(rr(0.012f)))

    // Latin around the rim, fitted exactly to one full turn.
    val baseR = rr(0.352f)
    val circumference = (2 * PI * baseR).toFloat()
    val rim = paints.rim
    rim.textSize = rr(0.062f)
    rim.letterSpacing = 0f
    val natural = rim.measureText(RIM_TEXT)
    if (natural > circumference) {
        rim.textSize *= circumference / natural * 0.985f
    } else {
        rim.letterSpacing = (circumference - natural) / RIM_TEXT.length / rim.textSize
    }
    val ring = NativePath().apply {
        addCircle(c.x, c.y, baseR, NativePath.Direction.CW)
    }
    // The path starts at 3 o'clock; turn it so "NON SERVIAM" sits centred at the top.
    val lead = rim.measureText("NON SERVIAM") / baseR * (180f / PI.toFloat())
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.rotate(-90f - lead / 2f, c.x, c.y)
    canvas.drawTextOnPath(RIM_TEXT, ring, 0f, 0f, rim)
    canvas.restore()

    drawCircle(CoinRed, radius = rr(0.338f), center = c, style = Stroke(rr(0.007f)))

    // Roman hours, each standing on its own radius: the dial counting down.
    val num = paints.numeral
    num.textSize = rr(0.043f)
    val numR = rr(0.292f)
    val native = drawContext.canvas.nativeCanvas
    for ((i, n) in NUMERALS.withIndex()) {
        val deg = i * 30f
        native.save()
        native.rotate(deg, c.x, c.y)
        native.drawText(n, c.x, c.y - numR + num.textSize * 0.35f, num)
        native.restore()
        // a small dot between numerals
        val t = Math.toRadians((deg + 15f - 90f).toDouble())
        drawCircle(CoinRed.copy(alpha = 0.8f), radius = rr(0.006f),
            center = Offset(c.x + numR * cos(t).toFloat(), c.y + numR * sin(t).toFloat()))
    }
    drawCircle(CoinRed, radius = rr(0.248f), center = c, style = Stroke(rr(0.006f)))

    // A dull ember behind the sigil.
    drawCircle(
        Brush.radialGradient(listOf(CoinRed.copy(alpha = 0.28f), Color.Transparent), center = c, radius = rr(0.24f)),
        radius = rr(0.24f), center = c,
    )

    // The Leviathan cross: a double-barred cross standing on the infinite.
    val stroke = rr(0.017f)
    val baseY = c.y + rr(0.115f)
    val lemniscate = Path()
    val la = rr(0.105f)
    val steps = 72
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val den = 1 + sin(t) * sin(t)
        val x = (c.x + la * cos(t) / den).toFloat()
        val y = (baseY + la * sin(t) * cos(t) / den * 1.25).toFloat()
        if (i == 0) lemniscate.moveTo(x, y) else lemniscate.lineTo(x, y)
    }
    lemniscate.close()
    drawPath(lemniscate, CoinRed, style = Stroke(stroke))
    drawLine(CoinRed, Offset(c.x, baseY), Offset(c.x, c.y - rr(0.195f)), strokeWidth = stroke, cap = StrokeCap.Square)
    drawLine(CoinRed, Offset(c.x - rr(0.05f), c.y - rr(0.125f)), Offset(c.x + rr(0.05f), c.y - rr(0.125f)),
        strokeWidth = stroke, cap = StrokeCap.Square)
    drawLine(CoinRed, Offset(c.x - rr(0.08f), c.y - rr(0.045f)), Offset(c.x + rr(0.08f), c.y - rr(0.045f)),
        strokeWidth = stroke, cap = StrokeCap.Square)
    // Small inverted triangle crowning the cross.
    val tri = Path().apply {
        moveTo(c.x - rr(0.025f), c.y - rr(0.225f))
        lineTo(c.x + rr(0.025f), c.y - rr(0.225f))
        lineTo(c.x, c.y - rr(0.195f))
        close()
    }
    drawPath(tri, CoinRed)
}
