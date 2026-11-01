package com.breakingfree.app.roadmap

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.ShiftingTitle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow
import kotlin.math.sin

// ---------- colour: red at the origin, white at the end goal ----------

private val ORIGIN_RED = Color(0xFF9E000D)
private val MID_RED = Color(0xFFE0303A)
private val GOAL_WHITE = Color(0xFFFFF6F3)

fun tierBoost(tier: Tier): Float = when (tier) {
    Tier.MINOR -> 0.07f
    Tier.REGULAR -> 0.17f
    Tier.MAJOR -> 0.42f
}

/** Colour by proximity to the end goal (t in 0..1), brightened by tier. */
fun progressColor(t: Float, tier: Tier): Color {
    val e = t.coerceIn(0f, 1f).pow(1.15f)
    val base = if (e < 0.5f) lerp(ORIGIN_RED, MID_RED, e * 2f) else lerp(MID_RED, GOAL_WHITE, (e - 0.5f) * 2f)
    return lerp(base, Color.White, tierBoost(tier))
}

fun radiusFor(tier: Tier): Float = when (tier) {
    Tier.MINOR -> 13f
    Tier.REGULAR -> 21f
    Tier.MAJOR -> 32f
}

private fun roman(n: Int): String {
    val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var x = n; val sb = StringBuilder()
    for (i in v.indices) while (x >= v[i]) { sb.append(s[i]); x -= v[i] }
    return sb.toString()
}

// ---------- layout ----------

private data class Placed(val pn: PathNode, val x: Float, val y: Float, val r: Float, val color: Color)
private data class Zone(val number: Int, val title: String, val top: Float, val bottom: Float, val color: Color)
private data class MapLayout(val height: Float, val placed: List<Placed>, val zones: List<Zone>)

private fun computeLayout(map: Roadmap, path: List<PathNode>, width: Float, viewport: Float): MapLayout {
    if (path.isEmpty()) return MapLayout(viewport, emptyList(), emptyList())
    val gap = 58f
    val topPad = 130f
    val bottomPad = 120f
    val n = path.size
    val rs = path.map { radiusFor(it.node.tier) }
    val cum = FloatArray(n)
    for (i in 1 until n) cum[i] = cum[i - 1] + rs[i - 1] + rs[i] + gap
    val content = bottomPad + rs[0] + cum[n - 1] + rs[n - 1] + topPad
    val height = maxOf(content, viewport)
    val baseY = height - bottomPad - rs[0]
    val cx = width / 2f
    val amp = width * 0.24f
    val placed = path.mapIndexed { i, pn ->
        val x = if (pn.node.tier == Tier.MAJOR) cx else cx + amp * sin(i * 0.95f + 0.6f)
        val t = if (n <= 1) 1f else i / (n - 1f)
        Placed(pn, x, baseY - cum[i], rs[i], progressColor(t, pn.node.tier))
    }
    // Zones: every top-level node that gathers others beneath it.
    val zones = ArrayList<Zone>()
    var zoneNo = 0
    placed.forEach { p ->
        if (p.pn.depth == 0) {
            val size = map.descendants(p.pn.node.id).size
            if (size > 0) {
                zoneNo++
                val first = placed[p.pn.index - size]
                zones += Zone(zoneNo, p.pn.node.title, p.y - p.r - 34f, first.y + first.r + 22f, p.color)
            }
        }
    }
    return MapLayout(height, placed, zones)
}

// ---------- screen ----------

private data class EditRequest(val node: RoadNode?, val presetParent: String? = null, val presetTier: Tier = Tier.REGULAR)

@Composable
fun RoadmapScreen(vm: RoadmapViewModel = viewModel()) {
    val p = LocalPalette.current
    val map by vm.map.collectAsState()
    var outlineMode by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val total = map.nodes.size
    val cleared = map.nodes.count { it.done }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                ShiftingTitle("Roadmap", "ROADMAP", fontSize = 34.sp)
                MonoLabel("$cleared / $total CLEARED", color = p.accent)
            }
            IconButton(onClick = { outlineMode = !outlineMode }) {
                Icon(if (outlineMode) Icons.Filled.Place else Icons.Filled.List, "Toggle view", tint = p.text)
            }
            IconButton(onClick = { editing = EditRequest(null) }) {
                Icon(Icons.Filled.Add, "Add milestone", tint = p.accent)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = p.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { MonoLabel("LOAD EXAMPLE ROADMAP", color = p.text) },
                        onClick = { menuOpen = false; vm.loadExample() })
                    DropdownMenuItem(text = { MonoLabel("CLEAR ENTIRE ROADMAP", color = p.accent) },
                        onClick = { menuOpen = false; confirmClear = true })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))

        if (map.nodes.isEmpty()) {
            EmptyRoadmap { editing = EditRequest(null, presetTier = Tier.MAJOR) }
        } else if (outlineMode) {
            OutlineView(map, onOpen = { selectedId = it.id }, onToggle = { vm.toggleDone(it.id) })
        } else {
            CampaignMap(map, onOpen = { selectedId = it.id })
        }
    }

    val selected = map.get(selectedId)
    if (selected != null) {
        NodeDialog(
            map = map,
            node = selected,
            onDismiss = { selectedId = null },
            onToggle = { vm.toggleDone(selected.id) },
            onEdit = { selectedId = null; editing = EditRequest(selected) },
            onAddInside = {
                selectedId = null
                editing = EditRequest(null, presetParent = selected.id,
                    presetTier = if (selected.tier == Tier.MAJOR) Tier.REGULAR else Tier.MINOR)
            },
            onMove = { vm.move(selected.id, it) },
            onDelete = { selectedId = null; vm.delete(selected.id) },
        )
    }
    editing?.let { req ->
        EditNodeDialog(
            map = map,
            existing = req.node,
            presetParent = req.presetParent,
            presetTier = req.presetTier,
            onDismiss = { editing = null },
            onSave = { title, notes, tier, parent ->
                if (req.node == null) vm.add(title, notes, tier, parent)
                else vm.update(req.node.copy(title = title.trim(), notes = notes.trim(), tier = tier, parentId = parent))
                editing = null
            },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Burn the map?",
            message = "Every milestone will be erased. This cannot be undone.",
            confirm = "ERASE",
            onConfirm = { confirmClear = false; vm.clear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun EmptyRoadmap(onCreate: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("The map is blank.", style = TextStyle(fontFamily = Fonts.Gothic, fontSize = 32.sp), color = p.text)
        Spacer(Modifier.height(10.dp))
        MonoLabel("NO ROUTE. NO ZONES. NO END GOAL.", color = p.textDim)
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier
                .border(1.dp, p.accent)
                .clickable(onClick = onCreate)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) { MonoLabel("LAY THE FIRST STONE", color = p.accent, fontSize = 14.sp) }
    }
}

@Composable
private fun CampaignMap(map: Roadmap, onOpen: (RoadNode) -> Unit) {
    val p = LocalPalette.current
    val path = remember(map) { map.path() }
    val current = remember(map) { map.currentIndex() }
    val allDone = remember(map) { map.nodes.all { it.done } }
    val pulse by rememberInfiniteTransition(label = "here").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "here",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthDp = maxWidth.value
        val viewportDp = maxHeight.value
        val layout = remember(map, widthDp, viewportDp) { computeLayout(map, path, widthDp, viewportDp) }
        val scroll = rememberScrollState()

        // Open the tab where you currently stand on the path.
        LaunchedEffect(Unit) {
            withTimeoutOrNull(1500) { snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE } }
            val here = layout.placed.getOrNull(current) ?: return@LaunchedEffect
            val target = with(density) { (here.y - viewportDp * 0.55f).dp.toPx() }.toInt()
            scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
        }

        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(layout.height.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1414), p.background, Color(0xFF1A0003))
                        )
                    )
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val px = { v: Float -> v.dp.toPx() }
                    // zones
                    layout.zones.forEach { z ->
                        drawRoundRect(
                            color = z.color.copy(alpha = 0.06f),
                            topLeft = Offset(px(10f), px(z.top)),
                            size = Size(size.width - px(20f), px(z.bottom - z.top)),
                            cornerRadius = CornerRadius(px(6f)),
                        )
                        drawLine(
                            z.color.copy(alpha = 0.45f),
                            Offset(px(10f), px(z.top)), Offset(px(10f), px(z.bottom)),
                            strokeWidth = px(2f),
                        )
                    }
                    // links
                    for (i in 1 until layout.placed.size) {
                        val a = layout.placed[i - 1]
                        val b = layout.placed[i]
                        val start = Offset(px(a.x), px(a.y - a.r))
                        val end = Offset(px(b.x), px(b.y + b.r))
                        val midY = (start.y + end.y) / 2f
                        val link = Path().apply {
                            moveTo(start.x, start.y)
                            cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                        }
                        val walked = i <= current && a.pn.node.done
                        drawPath(
                            link,
                            brush = Brush.linearGradient(listOf(a.color, b.color), start, end),
                            alpha = if (walked) 0.95f else 0.45f,
                            style = Stroke(
                                width = px(if (walked) 4f else 2.5f),
                                cap = StrokeCap.Round,
                                pathEffect = if (walked) null else PathEffect.dashPathEffect(floatArrayOf(px(7f), px(7f))),
                            ),
                        )
                    }
                    // nodes
                    layout.placed.forEach { pl ->
                        val c = Offset(px(pl.x), px(pl.y))
                        val r = px(pl.r)
                        val done = pl.pn.node.done
                        if (pl.pn.node.tier == Tier.MAJOR) {
                            drawCircle(
                                Brush.radialGradient(listOf(pl.color.copy(alpha = 0.35f), Color.Transparent), c, r * 2.4f),
                                r * 2.4f, c,
                            )
                        }
                        drawCircle(Color(0xFF070505), r, c)
                        drawCircle(pl.color.copy(alpha = if (done) 1f else 0.22f), r, c)
                        drawCircle(pl.color, r, c, style = Stroke(px(if (pl.pn.node.tier == Tier.MINOR) 2f else 2.5f)))
                        if (pl.pn.node.tier == Tier.MAJOR) {
                            drawCircle(pl.color.copy(alpha = 0.7f), r + px(6f), c, style = Stroke(px(1.5f)))
                        }
                        if (pl.pn.index == current && !allDone) {
                            drawCircle(
                                pl.color.copy(alpha = 1f - pulse),
                                r + px(4f + 18f * pulse), c, style = Stroke(px(2f)),
                            )
                        }
                    }
                }

                // zone labels
                layout.zones.forEach { z ->
                    MonoLabel(
                        "ZONE ${roman(z.number)}",
                        color = z.color.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        modifier = Modifier.offset(x = 18.dp, y = (z.top + 6f).dp),
                    )
                }
                // end goal / origin markers
                layout.placed.lastOrNull()?.let { last ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = (last.y - last.r - 100f).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MonoLabel("▲ THE END GOAL ▲", color = last.color, fontSize = 11.sp)
                    }
                }
                layout.placed.firstOrNull()?.let { first ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .offset(y = (first.y + first.r + 50f).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MonoLabel("ORIGIN", color = first.color.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }

                // node hit targets + labels
                layout.placed.forEach { pl ->
                    val node = pl.pn.node
                    val hit = maxOf(pl.r, 22f)
                    Box(
                        Modifier
                            .offset(x = (pl.x - hit).dp, y = (pl.y - hit).dp)
                            .size((hit * 2).dp)
                            .clip(CircleShape)
                            .clickable { onOpen(node) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (node.done) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = Color(0xFF0A0606),
                                modifier = Modifier.size((pl.r * 1.1f).dp),
                            )
                        }
                    }
                    NodeLabel(pl, widthDp, isCurrent = pl.pn.index == current && !allDone, onClick = { onOpen(node) })
                }
            }
        }
    }
}

@Composable
private fun NodeLabel(pl: Placed, width: Float, isCurrent: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val node = pl.pn.node
    val style = when (node.tier) {
        Tier.MAJOR -> TextStyle(fontFamily = Fonts.Gothic, fontSize = 24.sp, lineHeight = 26.sp)
        Tier.REGULAR -> TextStyle(fontFamily = Fonts.Oppressive, fontSize = 15.sp, letterSpacing = 1.sp, lineHeight = 18.sp)
        Tier.MINOR -> TextStyle(fontFamily = Fonts.Technical, fontSize = 12.sp, lineHeight = 14.sp)
    }
    val text = if (node.tier == Tier.REGULAR) node.title.uppercase() else node.title
    val color = if (node.done) pl.color.copy(alpha = 0.75f) else pl.color
    val center = width / 2f
    val mod: Modifier
    val align: TextAlign
    if (node.tier == Tier.MAJOR) {
        val w = width - 48f
        mod = Modifier.offset(x = 24f.dp, y = (pl.y - pl.r - 44f).dp).width(w.dp)
        align = TextAlign.Center
    } else if (pl.x >= center) {
        val right = pl.x - pl.r - 10f
        val w = (right - 14f).coerceAtLeast(60f)
        mod = Modifier.offset(x = (right - w).dp, y = (pl.y - 11f).dp).width(w.dp)
        align = TextAlign.End
    } else {
        val left = pl.x + pl.r + 10f
        val w = (width - left - 14f).coerceAtLeast(60f)
        mod = Modifier.offset(x = left.dp, y = (pl.y - 11f).dp).width(w.dp)
        align = TextAlign.Start
    }
    Column(mod.clickable(onClick = onClick), horizontalAlignment = when (align) {
        TextAlign.End -> Alignment.End
        TextAlign.Center -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }) {
        Text(
            text,
            style = style.copy(
                textAlign = align,
                textDecoration = if (node.done && node.tier == Tier.MINOR) TextDecoration.LineThrough else null,
            ),
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (isCurrent) {
            MonoLabel("◆ YOU ARE HERE", color = p.text, fontSize = 10.sp)
        }
    }
}

@Composable
private fun OutlineView(map: Roadmap, onOpen: (RoadNode) -> Unit, onToggle: (RoadNode) -> Unit) {
    val p = LocalPalette.current
    val outline = remember(map) { map.outline() }
    val pathIndex = remember(map) { map.path().associate { it.node.id to it.index } }
    val n = map.nodes.size
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)) {
        item {
            MonoLabel(
                "OUTLINE // ORIGIN AT TOP, END GOAL AT BOTTOM",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 10.sp,
            )
        }
        items(outline, key = { it.node.id }) { pn ->
            val node = pn.node
            val idx = pathIndex[node.id] ?: 0
            val color = progressColor(if (n <= 1) 1f else idx / (n - 1f), node.tier)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(node) }
                    .padding(start = (20 + pn.depth * 22).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pn.depth > 0) {
                    MonoLabel("-".repeat(pn.depth) + " ", color = p.textDim)
                }
                Box(
                    Modifier
                        .size(if (node.tier == Tier.MAJOR) 16.dp else if (node.tier == Tier.REGULAR) 12.dp else 9.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = if (node.done) 1f else 0.3f))
                        .border(1.5.dp, color, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    node.title,
                    modifier = Modifier.weight(1f).alpha(if (node.done) 0.6f else 1f),
                    color = color,
                    style = when (node.tier) {
                        Tier.MAJOR -> TextStyle(fontFamily = Fonts.Gothic, fontSize = 22.sp)
                        Tier.REGULAR -> TextStyle(fontFamily = Fonts.Oppressive, fontSize = 15.sp, letterSpacing = 1.sp)
                        Tier.MINOR -> TextStyle(fontFamily = Fonts.Technical, fontSize = 13.sp)
                    },
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .border(1.dp, if (node.done) color else p.line)
                        .clickable { onToggle(node) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (node.done) Icon(Icons.Filled.Check, "Cleared", tint = color, modifier = Modifier.size(18.dp))
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}
