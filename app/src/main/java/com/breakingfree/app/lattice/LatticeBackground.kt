package com.breakingfree.app.lattice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val SPACE_TOP = Color(0xFF010208)
private val SPACE_MID = Color(0xFF050A1A)
private val PLAIN_BLUE = Color(0xFF07112A)
private val LINE_BLUE = Color(0xFF4F7CC4)
private val LINE_VOID = Color(0xFF7C6CF0)
private val NODE_BLUE = Color(0xFF86AEEA)

private class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

/**
 * The Lattice. Upper region: spacetime. A starfield, faint nebulae, and the grid bending into a
 * gravity well. That bleeds down a gradient into the plain blue lattice. The grid breathes on its own.
 * [energy] (0..1) brightens it while you write, [ripple] (0..1, 1 = idle) sends a ring outward,
 * [resonance] (0..1, how much has been answered) permanently lifts the glow.
 */
@Composable
fun LatticeBackground(energy: Float, ripple: Float, resonance: Float, modifier: Modifier = Modifier) {
    val time by produceState(0f) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000f }
    }
    val stars = remember {
        val r = Random(2030)
        List(170) { Star(r.nextFloat(), r.nextFloat() * 0.7f, 0.5f + r.nextFloat() * 1.4f, 0.4f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f) }
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dp = density
        drawRect(Brush.verticalGradient(0f to SPACE_TOP, 0.32f to SPACE_MID, 0.68f to PLAIN_BLUE, 1f to PLAIN_BLUE))

        // How "spacetime" a given height is: 1 at the top, gone by ~62% down.
        fun space(y: Float): Float {
            val t = (1f - y / (h * 0.62f)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        // nebulae
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF3A1E7A).copy(alpha = 0.28f), Color.Transparent), Offset(w * 0.22f, h * 0.12f), w * 0.55f),
            w * 0.55f, Offset(w * 0.22f, h * 0.12f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF123A7A).copy(alpha = 0.3f), Color.Transparent), Offset(w * 0.85f, h * 0.28f), w * 0.5f),
            w * 0.5f, Offset(w * 0.85f, h * 0.28f),
        )

        // stars
        stars.forEach { s ->
            val y = s.y * h
            val a = space(y) * (0.35f + 0.65f * (0.5f + 0.5f * sin(time * s.speed + s.phase)))
            if (a > 0.02f) drawCircle(Color(0xFFC9D6FF).copy(alpha = a * 0.8f), s.size * dp * 0.8f, Offset(s.x * w, y))
        }

        val well = Offset(w * 0.5f, h * 0.2f)
        val sigma = w * 0.34f
        val maxDist = hypot(w, h)
        val ringR = ripple * maxDist
        val breathe = 0.5f + 0.5f * sin(time * 1.3f)

        // Bends a point toward the well, more strongly the more "space" it sits in.
        fun warp(x: Float, y: Float): Offset {
            val dx = well.x - x
            val dy = well.y - y
            val d2 = dx * dx + dy * dy
            val pull = 0.55f * space(y) * exp(-d2 / (sigma * sigma))
            return Offset(x + dx * pull, y + dy * pull)
        }

        // the lattice: two families of diagonals
        val step = 54f * dp
        val seg = 16f * dp
        val baseAlpha = 0.16f + 0.10f * breathe + 0.28f * energy + 0.16f * resonance
        var c = -h
        while (c < w + h) {
            for (dir in 0..1) {
                val path = Path()
                var first = true
                var y = 0f
                while (y <= h + seg) {
                    val x = if (dir == 0) c + y else c - y + h
                    if (x > -step && x < w + step) {
                        val p = warp(x, y)
                        if (first) { path.moveTo(p.x, p.y); first = false } else path.lineTo(p.x, p.y)
                    } else first = true
                    y += seg
                }
                drawPath(path, lerp(LINE_BLUE, LINE_VOID, 0.6f * space(h * 0.15f)).copy(alpha = baseAlpha.coerceAtMost(0.75f)),
                    style = Stroke(width = 1f * dp))
            }
            c += step
        }

        // nodes at the crossings: a pulse wave rolls outward from the well
        val half = step / 2f
        var i = 0
        var gx = 0f
        while (gx <= w + half) {
            var j = 0
            var gy = 0f
            while (gy <= h + half) {
                if ((i + j) % 2 == 0) {
                    val p = warp(gx, gy)
                    val dist = hypot(gx - well.x, gy - well.y)
                    val wave = 0.5f + 0.5f * sin(time * 2.2f - dist / (90f * dp))
                    val ring = if (ripple < 1f) (1f - abs(dist - ringR) / (70f * dp)).coerceIn(0f, 1f) * (1f - ripple) else 0f
                    val a = (0.10f + 0.35f * wave * (0.4f + 0.6f * breathe) + 0.35f * energy + 0.2f * resonance + 0.9f * ring)
                        .coerceIn(0f, 1f)
                    val col = lerp(NODE_BLUE, LINE_VOID, 0.5f * space(gy))
                    drawNode(p, (1.4f + 2.2f * wave + 3f * ring) * dp, col, a)
                }
                gy += half; j++
            }
            gx += half; i++
        }

        // the ripple's own ring
        if (ripple < 1f) {
            drawCircle(
                NODE_BLUE.copy(alpha = 0.35f * (1f - ripple)), ringR, well, style = Stroke(width = 2f * dp),
            )
        }
    }
}

private fun DrawScope.drawNode(p: Offset, r: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.45f), Color.Transparent), p, r * 3.2f), r * 3.2f, p)
    drawCircle(color.copy(alpha = alpha), r * 0.55f, p)
}
