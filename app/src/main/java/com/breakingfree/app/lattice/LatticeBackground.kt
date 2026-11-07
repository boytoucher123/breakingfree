package com.breakingfree.app.lattice

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// Each colour runs from its dead-zone value (stone: warm dark grey, no blue at all) to its 2-4am
// value (deep, strong, glowing blue) as vigor goes 0 -> 1.
private val BASE_STONE = Color(0xFF131210)
private val BASE_LIVE = Color(0xFF04123A)
private val WATER_A_STONE = Color(0xFF2A2520)
private val WATER_A_LIVE = Color(0xFF0D4FB0)
private val WATER_B_STONE = Color(0xFF221F1B)
private val WATER_B_LIVE = Color(0xFF0A7FA8)
private val LINE_STONE = Color(0xFF4A443D)
private val LINE_LIVE = Color(0xFF5C9BFF)
private val VOID_STONE = Color(0xFF574C43)
private val VOID_LIVE = Color(0xFF7C6CF0)
private val NODE_STONE = Color(0xFF837869)
private val NODE_LIVE = Color(0xFFA8D0FF)
private val STAR = Color(0xFFC9DCFF)

private class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

/**
 * The Lattice: a grid bending into a gravity well near the top, over a flat base with slow water
 * currents, flowing caustics and (at night) stars. The grid breathes on its own.
 * [energy] (0..1) brightens it while you write, [ripple] (0..1, 1 = idle) sends a ring outward,
 * [resonance] (0..1, how much has been answered) permanently lifts the glow.
 * [vigor] (0..1, from [LatticeMoods]) is the time of day: at 1 it glows strong blue and flows at
 * full speed; at 0 it is dull stone with no blue, barely moving. [waves] (0..1) fades in a slow,
 * soft distortion field: wavefronts drifting across the background in several directions.
 */
@Composable
fun LatticeBackground(energy: Float, ripple: Float, resonance: Float, vigor: Float, waves: Float = 0f, modifier: Modifier = Modifier) {
    // Animation clock. It runs slower when the lattice is weak, so motion slows rather than jumps.
    val speed by rememberUpdatedState(0.15f + 0.85f * vigor.coerceIn(0f, 1f))
    val time by produceState(0f) {
        var last = withFrameMillis { it }
        var t = 0f
        while (true) withFrameMillis { now ->
            t += (now - last) / 1000f * speed
            last = now
            value = t
        }
    }
    val stars = remember {
        val r = Random(2030)
        List(200) { Star(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 1.4f, 0.4f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f) }
    }
    val distortion = remember { if (android.os.Build.VERSION.SDK_INT >= 33) Distortion() else null }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Canvas(
        modifier.graphicsLayer {
            // Android 13+: the whole background ripples gently, slightly soft, 2-4am.
            renderEffect = if (distortion != null && waves > 0.01f) {
                distortion.effect(size.width, size.height, time, waves * 9f * density, waves * 1.2f * density)
            } else null
        },
    ) {
        val w = size.width
        val h = size.height
        val dp = density
        val v = vigor.coerceIn(0f, 1f)
        val glow = 0.35f + 0.85f * v        // overall brightness
        val sway = 0.2f + 0.8f * v          // how much things pulse and twinkle
        val lineBlue = lerp(LINE_STONE, LINE_LIVE, v)
        val nodeBlue = lerp(NODE_STONE, NODE_LIVE, v)
        val voidColor = lerp(VOID_STONE, VOID_LIVE, v)

        // One flat base: no gradient. Everything else floats over it.
        drawRect(lerp(BASE_STONE, BASE_LIVE, v))

        // Gravity well near the top bends the lattice; strongest there, gone ~62% down.
        fun space(y: Float): Float {
            val t = (1f - y / (h * 0.62f)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        // Water: big soft currents drifting across the whole screen.
        for (k in 0 until 5) {
            val cx = w * (0.5f + 0.48f * sin(time * 0.07f * (k + 1) + k * 1.7f))
            val cy = h * (0.5f + 0.46f * cos(time * 0.05f * (k + 2) + k * 2.3f))
            val r = w * (0.6f + 0.18f * sin(time * 0.11f + k))
            val col = lerp(if (k % 2 == 0) WATER_A_STONE else WATER_B_STONE, if (k % 2 == 0) WATER_A_LIVE else WATER_B_LIVE, v)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.26f + 0.14f * v), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
        }
        // Pre-Android-13 stand-in for the distortion field: a faint interference of slow wavefronts
        // travelling in several directions, drawn as large soft patches.
        if (waves > 0.01f && android.os.Build.VERSION.SDK_INT < 33) {
            val cols = 6
            val rows = 11
            val cw = w / cols
            val ch = h / rows
            for (cx in 0..cols) for (cy in 0..rows) {
                val x = cx * cw
                val y = cy * ch
                val f = fieldAt(x / h, y / h, time)
                val a = (0.05f * waves * (f + 1f) / 2f).coerceIn(0f, 0.08f)
                val r = maxOf(cw, ch) * 1.1f
                drawCircle(Brush.radialGradient(listOf(nodeBlue.copy(alpha = a), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
            }
        }

        // Stars, everywhere, only when the lattice is awake.
        if (v > 0.02f) stars.forEach { s ->
            val a = v * (0.35f + 0.65f * sway * (0.5f + 0.5f * sin(time * s.speed + s.phase)))
            drawCircle(STAR.copy(alpha = a * 0.75f), s.size * dp * 0.8f, Offset(s.x * w, s.y * h))
        }

        val well = Offset(w * 0.5f, h * 0.2f)
        val sigma = w * 0.34f
        val maxDist = hypot(w, h)
        val ringR = ripple * maxDist
        val breathe = 0.5f + 0.5f * sway * sin(time * 1.3f)

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
        val baseAlpha = (0.16f + 0.10f * breathe) * glow + 0.28f * energy + 0.16f * resonance
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
                drawPath(path, lerp(lineBlue, voidColor, 0.6f * space(h * 0.15f)).copy(alpha = baseAlpha.coerceAtMost(0.75f)),
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
                    val wave = 0.5f + 0.5f * sway * sin(time * 2.2f - dist / (90f * dp))
                    val ring = if (ripple < 1f) (1f - abs(dist - ringR) / (70f * dp)).coerceIn(0f, 1f) * (1f - ripple) else 0f
                    val a = ((0.10f + 0.35f * wave * (0.4f + 0.6f * breathe)) * glow + 0.35f * energy + 0.2f * resonance + 0.9f * ring)
                        .coerceIn(0f, 1f)
                    val col = lerp(nodeBlue, voidColor, 0.5f * space(gy))
                    drawNode(p, (1.4f + 2.2f * wave + 3f * ring) * dp, col, a)
                }
                gy += half; j++
            }
            gx += half; i++
        }

        // the ripple's own ring
        if (ripple < 1f) {
            drawCircle(
                nodeBlue.copy(alpha = 0.35f * (1f - ripple)), ringR, well, style = Stroke(width = 2f * dp),
            )
        }
    }
}

private fun DrawScope.drawNode(p: Offset, r: Float, color: Color, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.45f), Color.Transparent), p, r * 3.2f), r * 3.2f, p)
    drawCircle(color.copy(alpha = alpha), r * 0.55f, p)
}

/** Sum of four slow plane waves heading different ways; about -1..1. Positions in screen heights. */
private fun fieldAt(x: Float, y: Float, t: Float): Float =
    (sin(x * 3.1f + y * 1.0f + t * 0.33f) +
        sin(-x * 1.6f + y * 2.9f - t * 0.27f) +
        sin(-x * 2.4f - y * 2.1f + t * 0.21f) +
        sin(x * 1.2f - y * 3.4f - t * 0.25f)) / 4f

/**
 * The 2-4am distortion field as an AGSL shader (Android 13+). It displaces the background along
 * the same four slow wavefronts as [fieldAt], adds a faint shimmer, then blurs slightly.
 */
@androidx.annotation.RequiresApi(33)
private class Distortion {
    private val shader = android.graphics.RuntimeShader(
        """
        uniform shader content;
        uniform float2 size;
        uniform float time;
        uniform float strength;
        float w(float2 uv, float2 k, float speed) { return sin(dot(uv, k) + time * speed); }
        half4 main(float2 p) {
            float2 uv = p / size.y;
            float a = w(uv, float2(3.1, 1.0), 0.33);
            float b = w(uv, float2(-1.6, 2.9), -0.27);
            float c = w(uv, float2(-2.4, -2.1), 0.21);
            float d = w(uv, float2(1.2, -3.4), -0.25);
            float2 shift = float2(a * 0.8 + c * 0.5 - d * 0.3, b * 0.8 - c * 0.4 + d * 0.5);
            half4 col = content.eval(clamp(p + shift * strength, float2(0.0), size - 1.0));
            float shimmer = (a + b + c + d) * 0.25;
            col.rgb *= half(1.0 + shimmer * 0.07 * clamp(strength / 10.0, 0.0, 1.0));
            return col;
        }
        """.trimIndent(),
    )

    fun effect(width: Float, height: Float, time: Float, strength: Float, blur: Float): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("strength", strength)
        val warp = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        val soft = android.graphics.RenderEffect.createBlurEffect(blur.coerceAtLeast(0.5f), blur.coerceAtLeast(0.5f), android.graphics.Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createChainEffect(soft, warp).asComposeRenderEffect()
    }
}
