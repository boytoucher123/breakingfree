package com.breakingfree.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.withFrameMillis
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Ice blues: the deep origin, the midway blue of the flames, and the near-white goal.
private val HILL = Color(0xFF0A1C3D)
private val HILL_FAR = Color(0xFF07152E)
private val FLAME = Color(0xFF2F7DE0)
private val FLAME_HOT = Color(0xFF7DB6FF)
private val CORE = Color(0xFFE4F1FF)
private val TREE = Color(0xFFDCEAFB)
private val TREE_SHADE = Color(0xFF8FAAD2)

/** One branch, in tree units: the base of the trunk is (0, 0), up is negative y, height is 1. */
internal class Seg(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val width: Float, val depth: Int)

/** Where a flame sits on the tree, how big it is, and its own flicker rhythm. */
internal class Flame(val x: Float, val y: Float, val size: Float, val phase: Float, val rate: Float)

private class Ember(val x: Float, val speed: Float, val drift: Float, val phase: Float, val size: Float)

internal class Tree(val segs: List<Seg>, val flames: List<Flame>, val blaze: List<Flame>, val crown: Offset)

private const val MAX_DEPTH = 7

/** A bare, slightly crooked tree, the same every time. */
internal fun growTree(): Tree {
    val r = Random(1312)
    val segs = ArrayList<Seg>()
    fun grow(x: Float, y: Float, angle: Float, len: Float, width: Float, depth: Int) {
        // Branches wander a little as they go, so they aren't ruler-straight.
        val bend = (r.nextFloat() - 0.5f) * 0.25f
        val a = angle + bend
        val x1 = x + cos(a) * len
        val y1 = y + sin(a) * len
        segs += Seg(x, y, x1, y1, width, depth)
        if (depth >= MAX_DEPTH) return
        val kids = when {
            depth == 0 -> 2
            depth < 3 -> if (r.nextFloat() < 0.3f) 3 else 2
            else -> if (r.nextFloat() < 0.25f) 1 else 2
        }
        val spread = 0.5f + r.nextFloat() * 0.35f
        for (k in 0 until kids) {
            val t = if (kids == 1) 0f else k / (kids - 1f) - 0.5f
            // Lean back toward upright, so the crown stays rounded rather than splaying flat.
            val target = a + t * spread * 2f + (-PI.toFloat() / 2f - a) * 0.1f + (r.nextFloat() - 0.5f) * 0.2f
            grow(x1, y1, target, len * (0.68f + r.nextFloat() * 0.14f), width * 0.68f, depth + 1)
        }
    }
    grow(0f, 0f, -PI.toFloat() / 2f + 0.04f, 0.3f, 0.07f, 0)

    // Normalise to height 1 with the trunk base at the origin.
    val top = segs.minOf { minOf(it.y0, it.y1) }
    val k = 1f / -top
    val scaled = segs.map { Seg(it.x0 * k, it.y0 * k, it.x1 * k, it.y1 * k, it.width * k, it.depth) }

    // Fire: thickest in the crown, licking down the upper branches, a few low on the trunk.
    val flames = ArrayList<Flame>()
    scaled.forEach { s ->
        val chance = when {
            s.depth >= 5 -> 0.55f
            s.depth >= 3 -> 0.8f
            s.depth >= 1 -> 0.6f
            else -> 0.35f
        }
        if (r.nextFloat() < chance) {
            val t = r.nextFloat()
            val size = when {
                s.depth >= 5 -> 0.08f + r.nextFloat() * 0.07f
                s.depth >= 3 -> 0.11f + r.nextFloat() * 0.1f
                else -> 0.08f + r.nextFloat() * 0.06f
            }
            flames += Flame(s.x0 + (s.x1 - s.x0) * t, s.y0 + (s.y1 - s.y0) * t, size, r.nextFloat() * 6.28f, 0.8f + r.nextFloat() * 0.9f)
        }
    }
    // The blaze: a few big tongues rising out of the crown, behind everything else.
    val blaze = scaled.filter { it.depth in 2..4 && r.nextFloat() < 0.45f }
        .map { Flame(it.x1, it.y1, 0.2f + r.nextFloat() * 0.14f, r.nextFloat() * 6.28f, 0.6f + r.nextFloat() * 0.5f) }
    val crownSegs = scaled.filter { it.depth >= 3 }
    val crown = Offset(crownSegs.map { it.x1 }.average().toFloat(), crownSegs.map { it.y1 }.average().toFloat())
    return Tree(scaled, flames, blaze, crown)
}

/**
 * A distant tree on a hill, burning blue, in place of the old spinning coin. The tree is pale,
 * nearly white, only slightly shaded toward a silhouette; the fire flickers, sparks rise and
 * the hill catches the light. Everything fades out at the edges, so it sits on any background.
 */
@Composable
fun BurningTree(modifier: Modifier = Modifier) {
    val tree = remember { growTree() }
    val embers = remember {
        val r = Random(77)
        List(46) { Ember(r.nextFloat() * 2f - 1f, 0.06f + r.nextFloat() * 0.1f, 0.3f + r.nextFloat() * 0.8f, r.nextFloat(), 0.6f + r.nextFloat() * 1.1f) }
    }
    val time by produceState(0f) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000f }
    }
    Canvas(modifier) { drawScene(tree, embers, time) }
}

private fun DrawScope.drawScene(tree: Tree, embers: List<Ember>, t: Float) {
    val w = size.width
    val h = size.height
    val base = Offset(w * 0.5f, h * 0.7f)       // where the trunk meets the hilltop
    val th = h * 0.4f                           // tree height: small, it's some way off
    fun at(x: Float, y: Float) = Offset(base.x + x * th, base.y + y * th)
    val crown = at(tree.crown.x, tree.crown.y)

    // The fire's overall brightness wavers.
    val flicker = 0.86f + 0.08f * sin(t * 7.3f) + 0.06f * sin(t * 12.9f + 1.3f)

    // Glow in the air around the fire.
    val haloR = w * 0.42f
    drawCircle(
        Brush.radialGradient(listOf(FLAME.copy(alpha = 0.28f * flicker), FLAME.copy(alpha = 0.08f), Color.Transparent), crown, haloR),
        haloR, crown,
    )

    // A farther ridge, then the hill, both fading out toward the edges.
    val far = Path().apply {
        moveTo(-0.1f * w, h * 0.8f)
        cubicTo(w * 0.15f, h * 0.66f, w * 0.3f, h * 0.7f, w * 0.42f, h * 0.76f)
        cubicTo(w * 0.6f, h * 0.84f, w * 0.8f, h * 0.68f, w * 1.1f, h * 0.74f)
        lineTo(w * 1.1f, h); lineTo(-0.1f * w, h); close()
    }
    drawPath(far, Brush.radialGradient(listOf(HILL_FAR, HILL_FAR.copy(alpha = 0.6f), Color.Transparent), Offset(w * 0.5f, h * 0.85f), w * 0.62f))
    val hill = Path().apply {
        moveTo(-0.1f * w, h * 1.02f)
        cubicTo(w * 0.18f, h * 0.9f, w * 0.34f, base.y + h * 0.005f, base.x, base.y)
        cubicTo(w * 0.66f, base.y + h * 0.005f, w * 0.82f, h * 0.9f, w * 1.1f, h * 1.02f)
        close()
    }
    drawPath(hill, Brush.radialGradient(listOf(HILL, HILL.copy(alpha = 0.85f), Color.Transparent), base, w * 0.55f))
    // The hilltop catches the firelight.
    drawPath(
        Path().apply {
            moveTo(w * 0.22f, h * 0.86f)
            cubicTo(w * 0.34f, base.y + h * 0.04f, w * 0.42f, base.y + h * 0.002f, base.x, base.y)
            cubicTo(w * 0.58f, base.y + h * 0.002f, w * 0.66f, base.y + h * 0.04f, w * 0.78f, h * 0.86f)
        },
        Brush.radialGradient(listOf(FLAME_HOT.copy(alpha = 0.55f * flicker), Color.Transparent), base, w * 0.24f),
        style = Stroke(width = 1.2f * density),
    )
    drawCircle(Brush.radialGradient(listOf(FLAME.copy(alpha = 0.22f * flicker), Color.Transparent), base, th * 0.5f), th * 0.5f, base)

    // Smoke: a few faint, slow, rising wisps.
    for (i in 0 until 5) {
        val life = ((t * 0.035f + i / 5f) % 1f)
        val c = Offset(crown.x + sin(t * 0.2f + i * 1.9f) * th * 0.25f + life * th * 0.3f, crown.y - life * h * 0.7f)
        val rr = th * (0.25f + life * 0.6f)
        drawCircle(Brush.radialGradient(listOf(TREE_SHADE.copy(alpha = 0.06f * sin(life * PI.toFloat())), Color.Transparent), c, rr), rr, c)
    }

    // The blaze, then smaller flames, behind the branches.
    tree.blaze.forEach { f -> drawFlame(at(f.x, f.y), f, th, t, flicker, back = true) }
    tree.flames.forEachIndexed { i, f -> if (i % 2 == 0) drawFlame(at(f.x, f.y), f, th, t, flicker, back = true) }

    // The tree: pale, nearly white, with only a slight shaded core.
    tree.segs.forEach { s ->
        val a = at(s.x0, s.y0)
        val b = at(s.x1, s.y1)
        val sw = maxOf(s.width * th, 0.7f * density)
        drawLine(TREE.copy(alpha = 0.92f), a, b, sw, StrokeCap.Round)
        if (sw > 2.2f * density) drawLine(TREE_SHADE.copy(alpha = 0.45f), a, b, sw * 0.45f, StrokeCap.Round)
    }

    // Flames in front.
    tree.flames.forEachIndexed { i, f -> if (i % 2 == 1) drawFlame(at(f.x, f.y), f, th, t, flicker, back = false, core = i % 3 == 0) }

    // Sparks rising off the crown and drifting away.
    embers.forEach { e ->
        val life = ((t * e.speed + e.phase) % 1f)
        val x = crown.x + e.x * th * 0.45f + sin(t * e.drift + e.phase * 6f) * th * 0.12f + life * th * 0.2f * e.drift
        val y = crown.y + th * 0.1f - life * h * 0.75f
        val fade = (1f - life) * minOf(1f, life * 6f)
        val c = if (e.size > 1.3f) CORE else FLAME_HOT
        drawCircle(c.copy(alpha = 0.85f * fade), e.size * density * (1f - life * 0.5f), Offset(x, y))
        drawCircle(FLAME.copy(alpha = 0.25f * fade), e.size * density * 3f, Offset(x, y))
    }
}

/** One tongue of fire: a glowing teardrop that stretches, shrinks and sways on its own rhythm. */
private fun DrawScope.drawFlame(p: Offset, f: Flame, th: Float, t: Float, flicker: Float, back: Boolean, core: Boolean = false) {
    val s = f.size * th * (if (back) 1.25f else 0.9f)
    val ph = f.phase
    val tall = s * (2.1f + 0.55f * sin(t * 5.1f * f.rate + ph) + 0.3f * sin(t * 8.7f * f.rate + ph * 1.7f))
    val wide = s * (0.42f + 0.06f * sin(t * 6.3f * f.rate + ph * 2.1f))
    val lean = s * 0.3f * sin(t * 2.4f * f.rate + ph) + s * 0.15f * sin(t * 4.9f + ph * 0.6f) + s * 0.25f * sin(t * 0.7f)

    // Soft light under each flame.
    val glowR = s * 1.6f
    drawCircle(Brush.radialGradient(listOf(FLAME.copy(alpha = (if (back) 0.22f else 0.14f) * flicker), Color.Transparent), p, glowR), glowR, p)

    fun tongue(scale: Float): Path = Path().apply {
        val bw = wide * scale
        val top = Offset(p.x + lean * scale, p.y - tall * scale)
        moveTo(p.x - bw, p.y)
        cubicTo(p.x - bw * 1.1f, p.y - tall * scale * 0.45f, top.x - bw * 0.25f, top.y + tall * scale * 0.3f, top.x, top.y)
        cubicTo(top.x + bw * 0.25f, top.y + tall * scale * 0.3f, p.x + bw * 1.1f, p.y - tall * scale * 0.45f, p.x + bw, p.y)
        cubicTo(p.x + bw * 0.6f, p.y + bw * 0.55f, p.x - bw * 0.6f, p.y + bw * 0.55f, p.x - bw, p.y)
        close()
    }
    val outer = tongue(1f)
    drawPath(
        outer,
        Brush.verticalGradient(
            // Flames in front are thinner, so the pale branches still show through them.
            listOf(Color.Transparent, FLAME.copy(alpha = (if (back) 0.55f else 0.35f) * flicker), FLAME_HOT.copy(alpha = (if (back) 0.75f else 0.5f) * flicker)),
            startY = p.y - tall, endY = p.y + wide * 0.5f,
        ),
    )
    if (core) {
        val inner = tongue(0.45f)
        drawPath(
            inner,
            Brush.verticalGradient(
                listOf(Color.Transparent, CORE.copy(alpha = 0.4f * flicker), CORE.copy(alpha = 0.6f)),
                startY = p.y - tall * 0.5f, endY = p.y + wide * 0.3f,
            ),
        )
    }
    // Now and then a tip flares white for a moment.
    val flare = abs(sin(t * 3.1f * f.rate + ph * 3f))
    if (flare > 0.97f) drawCircle(CORE.copy(alpha = (flare - 0.97f) * 20f), s * 0.08f, Offset(p.x + lean, p.y - tall))
}
