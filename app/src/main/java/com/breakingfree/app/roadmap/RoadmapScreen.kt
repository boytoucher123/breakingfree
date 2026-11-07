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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow
import kotlin.math.sin

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
private data class MapLayout(
    val height: Float, val placed: List<Placed>, val zones: List<Zone>, val goal: Int, val beyondFrom: Float?,
    /** Where the Beyond page ends and the climb page begins (null when there is no Beyond). */
    val split: Float?,
)

private const val BEYOND_GAP = 190f

private fun computeLayout(map: Roadmap, scheme: RoadScheme, path: List<PathNode>, width: Float, viewport: Float): MapLayout {
    if (path.isEmpty()) return MapLayout(viewport, emptyList(), emptyList(), 0, null, null)
    val goal = map.endGoalIndex(path)
    val gap = 58f
    val topPad = 130f
    val bottomPad = 120f
    val n = path.size
    val rs = path.map { radiusFor(it.node.tier) }
    val cum = FloatArray(n)
    for (i in 1 until n) cum[i] = cum[i - 1] + rs[i - 1] + rs[i] + gap + if (i == goal + 1) BEYOND_GAP else 0f
    val content = bottomPad + rs[0] + cum[n - 1] + rs[n - 1] + topPad
    val height = maxOf(content, viewport)
    val baseY = height - bottomPad - rs[0]
    val cx = width / 2f
    val amp = width * 0.24f
    val placed = path.mapIndexed { i, pn ->
        val x = if (pn.node.tier == Tier.MAJOR) cx else cx + amp * sin(i * 0.95f + 0.6f)
        Placed(pn, x, baseY - cum[i], rs[i], scheme.colorAt(i, n, goal, pn.node.tier))
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
    val beyondFrom = if (goal < n - 1) placed[goal].y - placed[goal].r - 120f else null
    // Below the "Beyond" heading, above "THE END GOAL".
    val split = if (goal < n - 1) placed[goal].y - placed[goal].r - 115f else null
    return MapLayout(height, placed, zones, goal, beyondFrom, split)
}

// ---------- screen ----------

private data class EditRequest(val node: RoadNode?, val presetParent: String? = null, val presetTier: Tier = Tier.REGULAR)

@Composable
fun RoadmapScreen(vm: RoadmapViewModel = viewModel()) {
    val p = LocalPalette.current
    val books by vm.books.collectAsState()
    val pager = rememberPagerState(initialPage = vm.page.value.coerceIn(0, books.lastIndex)) { books.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.setPage(it) } }
    val scope = rememberCoroutineScope()
    val book = books[pager.currentPage.coerceIn(0, books.lastIndex)]
    val map = book.map
    var outlineMode by rememberSaveable { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<EditRequest?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDeleteBook by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    var editingScheme by remember { mutableStateOf(false) }
    // A newly added roadmap: swipe to it once the list has caught up.
    var goToPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(goToPage, books.size) {
        val target = goToPage ?: return@LaunchedEffect
        if (target < books.size) {
            pager.animateScrollToPage(target)
            goToPage = null
        }
    }

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
                ShiftingTitle("Roadmap", "ROADMAP", fontSize = 34.sp, textAlign = TextAlign.Start)
                MonoLabel(
                    "${book.name.uppercase()}  //  $cleared / $total CLEARED",
                    color = p.accent,
                )
                if (books.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    PageDots(books.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
                }
            }
            IconButton(onClick = { outlineMode = !outlineMode }) {
                Icon(if (outlineMode) Icons.Filled.Place else Icons.Filled.List, "Toggle view", tint = p.text)
            }
            IconButton(onClick = {
                val path = map.path()
                val here = path.getOrNull(map.currentIndex())?.node
                val major = here?.let { map.majorOf(it) }
                editing = if (major == null) EditRequest(null, presetTier = Tier.MAJOR)
                else EditRequest(null, presetParent = major.id, presetTier = Tier.MINOR)
            }) {
                Icon(Icons.Filled.Add, "Add milestone", tint = p.accent)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, "More", tint = p.textDim)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { MonoLabel("NEW ROADMAP", color = p.text) },
                        onClick = { menuOpen = false; naming = NameRequest(null, "") })
                    DropdownMenuItem(text = { MonoLabel("RENAME", color = p.text) },
                        onClick = { menuOpen = false; naming = NameRequest(book.id, book.name) })
                    DropdownMenuItem(text = { MonoLabel("COLOURS", color = p.text) },
                        onClick = { menuOpen = false; editingScheme = true })
                    DropdownMenuItem(text = { MonoLabel("RESTORE DEFAULTS", color = p.text) },
                        onClick = { menuOpen = false; confirmRestore = true })
                    DropdownMenuItem(text = { MonoLabel("CLEAR THIS ROADMAP", color = p.accent) },
                        onClick = { menuOpen = false; confirmClear = true })
                    if (books.size > 1) {
                        DropdownMenuItem(text = { MonoLabel("DELETE THIS ROADMAP", color = p.accent) },
                            onClick = { menuOpen = false; confirmDeleteBook = true })
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))

        // Swipe left and right between roadmaps.
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { books.getOrNull(it)?.id ?: it }) { page ->
            val b = books.getOrNull(page) ?: return@HorizontalPager
            when {
                b.map.nodes.isEmpty() -> EmptyRoadmap { editing = EditRequest(null, presetTier = Tier.MAJOR) }
                outlineMode -> OutlineView(b.map, b.scheme, onOpen = { selectedId = it.id }, onToggle = { vm.toggleDone(b.id, it.id) })
                else -> CampaignMap(b.map, b.scheme, onOpen = { selectedId = it.id })
            }
        }
    }

    val selected = map.get(selectedId)
    if (selected != null) {
        NodeDialog(
            map = map,
            scheme = book.scheme,
            node = selected,
            onDismiss = { selectedId = null },
            onToggle = { vm.toggleDone(book.id, selected.id) },
            onEdit = { selectedId = null; editing = EditRequest(selected) },
            onAddInside = {
                selectedId = null
                editing = EditRequest(null, presetParent = selected.id,
                    presetTier = if (selected.tier == Tier.MAJOR) Tier.REGULAR else Tier.MINOR)
            },
            onMove = { vm.move(book.id, selected.id, it) },
            onDelete = { selectedId = null; vm.delete(book.id, selected.id) },
        )
    }
    editing?.let { req ->
        EditNodeDialog(
            map = map,
            existing = req.node,
            presetParent = req.presetParent,
            presetTier = req.presetTier,
            onDismiss = { editing = null },
            onSave = { title, notes, tier, parent, endGoal ->
                if (req.node == null) vm.add(book.id, title, notes, tier, parent, endGoal)
                else vm.update(book.id, req.node.copy(title = title.trim(), notes = notes.trim(), tier = tier,
                    parentId = parent, endGoal = endGoal))
                editing = null
            },
        )
    }
    naming?.let { req ->
        NameRoadmapDialog(
            isNew = req.bookId == null,
            initial = req.name,
            onDismiss = { naming = null },
            onSave = { name ->
                if (req.bookId == null) goToPage = vm.addBook(name, book.scheme) else vm.renameBook(req.bookId, name)
                naming = null
            },
        )
    }
    if (editingScheme) {
        SchemeDialog(
            initial = book.scheme,
            onDismiss = { editingScheme = false },
            onSave = { vm.setScheme(book.id, it); editingScheme = false },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore the default roadmap?",
            message = "\"${book.name}\" will be replaced with the default income campaign. This cannot be undone.",
            confirm = "RESTORE",
            onConfirm = { confirmRestore = false; vm.restoreDefaults(book.id) },
            onDismiss = { confirmRestore = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "Burn the map?",
            message = "Every milestone in \"${book.name}\" will be erased. This cannot be undone.",
            confirm = "ERASE",
            onConfirm = { confirmClear = false; vm.clear(book.id) },
            onDismiss = { confirmClear = false },
        )
    }
    if (confirmDeleteBook) {
        ConfirmDialog(
            title = "Delete \"${book.name}\"?",
            message = "The whole roadmap and its milestones go. The others stay. This cannot be undone.",
            confirm = "DELETE",
            onConfirm = { confirmDeleteBook = false; vm.deleteBook(book.id) },
            onDismiss = { confirmDeleteBook = false },
        )
    }
}

private data class NameRequest(val bookId: String?, val name: String)

/** One square per roadmap; the current one filled. Tap to jump. */
@Composable
private fun PageDots(count: Int, current: Int, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(9.dp)
                    .border(1.dp, if (i == current) p.accent else p.textDim)
                    .background(if (i == current) p.accent else Color.Transparent)
                    .clickable { onPick(i) },
            )
        }
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
private fun CampaignMap(map: Roadmap, scheme: RoadScheme, onOpen: (RoadNode) -> Unit) {
    val path = remember(map) { map.path() }
    val current = remember(map) { map.currentIndex() }
    val allDone = remember(map) { map.nodes.all { it.done } }
    val pulse by rememberInfiniteTransition(label = "here").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "here",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthDp = maxWidth.value
        val viewportDp = maxHeight.value
        val layout = remember(map, scheme, widthDp, viewportDp) { computeLayout(map, scheme, path, widthDp, viewportDp) }
        val here = layout.placed.getOrNull(current)?.y
        val content: @Composable (Modifier) -> Unit = { mod ->
            MapContent(layout, scheme, current, allDone, pulse, widthDp, onOpen, mod)
        }
        val split = layout.split
        if (split == null) {
            MapSection(0f, layout.height, viewportDp, focusY = here, startAtBottom = false, fill = null, content = content)
        } else {
            // The climb and the Beyond are two pages. Scrolling past the edge of one turns the page
            // to the other; you can never sit with both on screen.
            val inBeyond = current > layout.goal && !allDone
            val pages = rememberPagerState(initialPage = if (inBeyond) 0 else 1) { 2 }
            VerticalPager(pages, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                if (page == 0) {
                    MapSection(0f, split, viewportDp, focusY = here.takeIf { inBeyond }, startAtBottom = true,
                        fill = scheme.beyondSky, content = content)
                } else {
                    MapSection(split, layout.height, viewportDp, focusY = here.takeIf { !inBeyond }, startAtBottom = false,
                        fill = null, content = content)
                }
            }
        }
    }
}

/**
 * A scrollable window onto the slice [top]..[bottom] (dp) of the full map. Short slices sit at the
 * bottom of a full-height page, with [fill] above them. Opens on [focusY] if given, otherwise at
 * the bottom or the top.
 */
@Composable
private fun MapSection(
    top: Float,
    bottom: Float,
    viewportDp: Float,
    focusY: Float?,
    startAtBottom: Boolean,
    fill: Color?,
    content: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val sliceH = bottom - top
    val pageH = maxOf(sliceH, viewportDp)
    val shift = pageH - sliceH
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        withTimeoutOrNull(1500) { snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE } }
        if (scroll.maxValue == Int.MAX_VALUE) return@LaunchedEffect
        val targetDp = when {
            focusY != null -> focusY - top + shift - viewportDp * 0.55f
            startAtBottom -> pageH
            else -> 0f
        }
        scroll.scrollTo(with(density) { targetDp.dp.toPx() }.toInt().coerceIn(0, scroll.maxValue))
    }
    Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(pageH.dp)
                .then(if (fill != null) Modifier.background(fill) else Modifier)
                .clipToBounds(),
        ) {
            content(
                Modifier
                    .offset(y = (shift - top).dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true),
            )
        }
    }
}

/** The whole map, full height. [MapSection] shows a slice of it. */
@Composable
private fun MapContent(
    layout: MapLayout,
    scheme: RoadScheme,
    current: Int,
    allDone: Boolean,
    pulse: Float,
    widthDp: Float,
    onOpen: (RoadNode) -> Unit,
    modifier: Modifier,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(layout.height.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A1414), p.background, scheme.climbFloor)
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val px = { v: Float -> v.dp.toPx() }
            layout.beyondFrom?.let { from ->
                val y = px(from)
                drawRect(
                    Brush.verticalGradient(
                        0f to scheme.beyondSky, 0.75f to scheme.beyondHaze.copy(alpha = 0.55f), 1f to Color.Transparent,
                        startY = 0f, endY = y,
                    ),
                    size = Size(size.width, y),
                )
            }
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
                // The Beyond is its own page: no line crossing into it.
                if (layout.split != null && i == layout.goal + 1) continue
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
        // end goal / beyond / origin markers
        layout.placed.getOrNull(layout.goal)?.let { g ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (g.y - g.r - 84f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MonoLabel("▲ THE END GOAL ▲", color = g.color, fontSize = 11.sp)
            }
        }
        if (layout.beyondFrom != null) {
            val g = layout.placed[layout.goal]
            Column(
                Modifier
                    .fillMaxWidth()
                    .offset(y = (g.y - g.r - 190f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Beyond", style = TextStyle(fontFamily = Fonts.Gothic, fontSize = 34.sp), color = scheme.beyondTitle)
                MonoLabel("WHAT COMES AFTER FREEDOM", color = scheme.beyondSubtitle, fontSize = 10.sp)
            }
            layout.placed.lastOrNull()?.let { last ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (last.y - last.r - 100f).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MonoLabel("▲ THE ABYSS ▲", color = last.color, fontSize = 11.sp)
                }
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
private fun OutlineView(map: Roadmap, scheme: RoadScheme, onOpen: (RoadNode) -> Unit, onToggle: (RoadNode) -> Unit) {
    val p = LocalPalette.current
    val outline = remember(map) { map.outline() }
    val path = remember(map) { map.path() }
    val pathIndex = remember(map) { path.associate { it.node.id to it.index } }
    val goal = remember(map) { map.endGoalIndex(path) }
    val n = path.size
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
            val color = scheme.colorAt(idx, n, goal, node.tier)
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
