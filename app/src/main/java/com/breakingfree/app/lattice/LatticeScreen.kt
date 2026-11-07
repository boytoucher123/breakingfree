package com.breakingfree.app.lattice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.roadmap.ConfirmDialog
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Blue glow around the Lattice's text, from nothing in the dead zone to strong at 2-4am. */
private val LocalLatticeGlow = staticCompositionLocalOf<Shadow?> { null }

private fun glowFor(vigor: Float): Shadow? {
    val g = ((vigor - 0.45f) / 0.55f).coerceIn(0f, 1f)
    if (g <= 0f) return null
    return Shadow(color = Color(0xFF5CA8FF).copy(alpha = 0.95f * g), offset = Offset.Zero, blurRadius = 6f + 26f * g)
}

@Composable
private fun questionStyle(size: Int = 22) =
    TextStyle(
        fontFamily = Fonts.Quiet, fontStyle = FontStyle.Italic, fontSize = size.sp, lineHeight = (size + 6).sp,
        shadow = LocalLatticeGlow.current,
    )

@Composable
private fun answerStyle(p: Palette) =
    TextStyle(fontFamily = Fonts.Quiet, fontSize = 20.sp, lineHeight = 25.sp, color = p.text, shadow = LocalLatticeGlow.current)

private fun roman(n: Int): String {
    val v = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val s = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var x = n; val sb = StringBuilder()
    for (i in v.indices) while (x >= v[i]) { sb.append(s[i]); x -= v[i] }
    return sb.toString()
}

@Composable
private fun QuietLabel(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color? = null) {
    val p = LocalPalette.current
    Text(
        text, modifier = modifier, color = color ?: p.textDim,
        style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 14.sp, letterSpacing = 2.sp, shadow = LocalLatticeGlow.current),
    )
}

private const val MODE_QUESTIONS = 0
private const val MODE_ARCHIVE = 1
private const val MODE_MANAGE = 2

@Composable
fun LatticeScreen(vm: LatticeViewModel = viewModel()) {
    val p = LocalPalette.current
    val s by vm.state.collectAsState()
    val order by vm.order.collectAsState()
    var mode by rememberSaveable { mutableIntStateOf(MODE_QUESTIONS) }
    var kept by remember { mutableStateOf(false) }

    // The lattice reacts to you.
    val scope = rememberCoroutineScope()
    val energy = remember { Animatable(0f) }
    val ripple = remember { Animatable(1f) }
    var lastRipple by remember { mutableLongStateOf(0L) }
    val resonance by animateFloatAsState(
        if (s.questions.isEmpty()) 0f else s.answeredCount / s.questions.size.toFloat(), tween(1500), label = "res",
    )
    fun react(strong: Boolean = false) {
        scope.launch {
            energy.snapTo((energy.value + if (strong) 1f else 0.16f).coerceAtMost(1f))
            energy.animateTo(0f, tween(if (strong) 3000 else 1700))
        }
        val now = System.currentTimeMillis()
        if (strong || now - lastRipple > 1500) {
            lastRipple = now
            scope.launch {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(if (strong) 2600 else 2000, easing = LinearOutSlowInEasing))
            }
        }
    }

    // Time of day: how alive the lattice is, and the line under the title.
    val mood = rememberLatticeMood()

    LaunchedEffect(Unit) { vm.shuffle() }
    LaunchedEffect(kept) { if (kept) { delay(2600); kept = false } }
    BackHandler(enabled = mode != MODE_QUESTIONS) { mode = MODE_QUESTIONS }

    CompositionLocalProvider(LocalLatticeGlow provides glowFor(mood.vigor)) {
    Box(Modifier.fillMaxSize()) {
        LatticeBackground(energy.value, ripple.value, resonance, mood.vigor, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mode != MODE_QUESTIONS) {
                    IconButton(onClick = { mode = MODE_QUESTIONS }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = p.textDim)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        when (mode) {
                            MODE_ARCHIVE -> "What was kept"
                            MODE_MANAGE -> "The Questions"
                            else -> "The Lattice"
                        },
                        style = questionStyle(34), color = p.text,
                    )
                    QuietLabel(
                        when (mode) {
                            MODE_ARCHIVE -> "${s.entries.size} reflections"
                            MODE_MANAGE -> "${s.questions.size} threads in the lattice"
                            else -> mood.line
                        }
                    )
                }
                if (mode == MODE_QUESTIONS) {
                    IconButton(onClick = vm::shuffle) { Icon(Icons.Filled.Refresh, "Shuffle", tint = p.textDim) }
                    IconButton(onClick = { mode = MODE_ARCHIVE }) { Icon(Icons.Filled.List, "Kept reflections", tint = p.textDim) }
                    IconButton(onClick = { mode = MODE_MANAGE }) { Icon(Icons.Filled.Settings, "Edit questions", tint = p.textDim) }
                }
            }
            Spacer(Modifier.height(4.dp))
            when (mode) {
                MODE_ARCHIVE -> Archive(s.entries, vm::deleteEntry)
                MODE_MANAGE -> ManageQuestions(vm, s.questions)
                else -> QuestionPager(vm, s, order, onReact = { react() },
                    onKeep = { if (vm.keep()) { kept = true; react(strong = true) } })
            }
        }
        AnimatedVisibility(
            kept, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .border(1.dp, p.accentDim)
                    .background(p.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 26.dp, vertical = 14.dp)
            ) { Text("kept. the lattice remembers.", style = questionStyle(20), color = p.text) }
        }
    }
    }
}

@Composable
private fun QuestionPager(
    vm: LatticeViewModel,
    s: LatticeState,
    order: List<String>,
    onReact: () -> Unit,
    onKeep: () -> Unit,
) {
    val p = LocalPalette.current
    val byId = remember(s.questions) { s.questions.associateBy { it.id } }
    val ids = order.filter { it in byId }
    if (ids.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("the lattice is empty.\nadd questions with the gear above.", style = questionStyle(22),
                color = p.textDim, textAlign = TextAlign.Center)
        }
        return
    }
    val pager = rememberPagerState { ids.size }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { ids.getOrElse(it) { "x$it" } }) { page ->
            val q = byId[ids.getOrNull(page)] ?: return@HorizontalPager
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val answered = s.isAnswered(q.id)
                    val qColor by animateColorAsState(if (answered) p.accent else p.text, tween(1200), label = "q")
                    QuietLabel("— ${roman(page + 1)} —", color = if (answered) p.accent else p.textDim)
                    Spacer(Modifier.height(14.dp))
                    if (q.type == QType.TEXT) {
                        Text(q.prompt, style = questionStyle(30), color = qColor, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(26.dp))
                        QuietBox(s.singles[q.id].orEmpty(), { vm.setSingle(q.id, it); onReact() }, Modifier.fillMaxWidth(), 170)
                    } else {
                        Text(q.prompt, style = questionStyle(26), color = qColor, textAlign = TextAlign.Center)
                        QuietLabel("and", Modifier.padding(vertical = 4.dp))
                        Text(q.right, style = questionStyle(26), color = qColor, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(22.dp))
                        TableQuestion(vm, q, s.tables[q.id].orEmpty().ifEmpty { listOf("" to "") }, onReact)
                    }
                }
            }
        }

        // navigation
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                enabled = pager.currentPage > 0,
            ) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous", tint = if (pager.currentPage > 0) p.text else p.line) }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ids.size <= 24) {
                    ids.forEachIndexed { i, id ->
                        val current = i == pager.currentPage
                        val done = s.isAnswered(id)
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (current) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (done) p.accent else if (current) p.text.copy(alpha = 0.6f) else p.line)
                                .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        )
                    }
                } else {
                    QuietLabel("${pager.currentPage + 1} of ${ids.size}")
                }
            }
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(ids.size - 1)) } },
                enabled = pager.currentPage < ids.size - 1,
            ) { Icon(Icons.Filled.KeyboardArrowRight, "Next", tint = if (pager.currentPage < ids.size - 1) p.text else p.line) }
        }
        Box(
            Modifier
                .padding(horizontal = 28.dp, vertical = 10.dp)
                .fillMaxWidth()
                .border(1.dp, if (s.hasDraft) p.accent else p.line)
                .background(p.background.copy(alpha = 0.55f))
                .clickable(enabled = s.hasDraft, onClick = onKeep)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (s.hasDraft) "keep these thoughts  ·  ${s.answeredCount} answered" else "keep these thoughts",
                style = questionStyle(19), color = if (s.hasDraft) p.accent else p.textDim,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun QuietBox(value: String, onChange: (String) -> Unit, modifier: Modifier, minHeight: Int) {
    val p = LocalPalette.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = answerStyle(p),
        cursorBrush = SolidColor(p.accent),
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .border(1.dp, if (value.isNotBlank()) p.accentDim else p.line)
            .background(p.background.copy(alpha = 0.6f))
            .padding(14.dp),
    )
}

@Composable
private fun TableQuestion(vm: LatticeViewModel, q: Question, rows: List<Pair<String, String>>, onReact: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().border(1.dp, p.line).background(p.background.copy(alpha = 0.6f))) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(p.surfaceHigh.copy(alpha = 0.5f))) {
            Text(q.prompt, style = questionStyle(17), color = p.textDim, modifier = Modifier.weight(1f).padding(10.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
            Text(q.right, style = questionStyle(17), color = p.textDim, modifier = Modifier.weight(1f).padding(10.dp))
        }
        rows.forEachIndexed { i, (l, r) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BasicTextField(
                    value = l, onValueChange = { vm.setCell(q.id, i, true, it); onReact() },
                    textStyle = answerStyle(p), cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).padding(12.dp),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = r, onValueChange = { vm.setCell(q.id, i, false, it); onReact() },
                        textStyle = answerStyle(p), cursorBrush = SolidColor(p.accent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 28.dp),
                    )
                    if (rows.size > 1) {
                        Icon(
                            Icons.Filled.Close, "Remove row", tint = p.textDim.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(16.dp)
                                .clickable { vm.removeRow(q.id, i) },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Text(
            "+ another",
            style = questionStyle(16), color = p.accent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.addRow(q.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ManageQuestions(vm: LatticeViewModel, questions: List<Question>) {
    val p = LocalPalette.current
    var toDelete by remember { mutableStateOf<Question?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(QType.TEXT) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var right by rememberSaveable { mutableStateOf("") }
    val canAdd = prompt.isNotBlank() && (type == QType.TEXT || right.isNotBlank())

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(questions, key = { it.id }) { q ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, p.line)
                    .background(p.background.copy(alpha = 0.6f))
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    QuietLabel(if (q.type == QType.TABLE) "table" else "text box", color = p.accentDim)
                    Text(
                        if (q.type == QType.TABLE) "${q.prompt}  →  ${q.right}" else q.prompt,
                        style = questionStyle(18), color = p.text,
                    )
                }
                IconButton(onClick = { toDelete = q }) { Icon(Icons.Filled.Close, "Delete question", tint = p.textDim) }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, p.accentDim)
                    .background(p.surface.copy(alpha = 0.75f))
                    .padding(16.dp)
            ) {
                Text("a new thread", style = questionStyle(22), color = p.text)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormatChip("text box", type == QType.TEXT) { type = QType.TEXT }
                    FormatChip("table", type == QType.TABLE) { type = QType.TABLE }
                }
                Spacer(Modifier.height(12.dp))
                QuietLabel(if (type == QType.TABLE) "left column question" else "question")
                Spacer(Modifier.height(4.dp))
                QuietBox(prompt, { prompt = it }, Modifier.fillMaxWidth(), 50)
                if (type == QType.TABLE) {
                    Spacer(Modifier.height(10.dp))
                    QuietLabel("right column question")
                    Spacer(Modifier.height(4.dp))
                    QuietBox(right, { right = it }, Modifier.fillMaxWidth(), 50)
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (canAdd) p.accent else p.line)
                        .clickable(enabled = canAdd) {
                            vm.addQuestion(type, prompt, right)
                            prompt = ""; right = ""
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("weave it in", style = questionStyle(18), color = if (canAdd) p.accent else p.textDim) }
            }
        }
        item {
            Spacer(Modifier.height(10.dp))
            Text(
                "restore the default questions",
                style = questionStyle(16), color = p.textDim,
                modifier = Modifier.fillMaxWidth().clickable { confirmRestore = true }.padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
    toDelete?.let { q ->
        ConfirmDialog(
            title = "Remove this question?",
            message = "\"${q.prompt}\" will leave the lattice. Reflections you've already kept stay as they are.",
            confirm = "REMOVE",
            onConfirm = { vm.deleteQuestion(q.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
    if (confirmRestore) {
        ConfirmDialog(
            title = "Restore the default questions?",
            message = "Your question list will be replaced with the originals. Kept reflections are not affected.",
            confirm = "RESTORE",
            onConfirm = { vm.restoreDefaultQuestions(); confirmRestore = false },
            onDismiss = { confirmRestore = false },
        )
    }
}

@Composable
private fun FormatChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .border(1.dp, if (selected) p.accent else p.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text(text, style = questionStyle(16), color = if (selected) p.accent else p.textDim) }
}

@Composable
private fun Archive(entries: List<Reflection>, onDelete: (String) -> Unit) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf<String?>(entries.firstOrNull()?.id) }
    var toDelete by remember { mutableStateOf<Reflection?>(null) }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("nothing kept yet.", style = questionStyle(22), color = p.textDim)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.id }) { e ->
            val open = expanded == e.id
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (open) p.accentDim else p.line)
                    .background(p.background.copy(alpha = 0.7f))
                    .clickable { expanded = if (open) null else e.id }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            DateFormat.getDateInstance(DateFormat.FULL).format(Date(e.timestamp)),
                            style = questionStyle(20), color = p.text,
                        )
                        QuietLabel(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.timestamp)) +
                                "  ·  ${e.answers.size} answered"
                        )
                    }
                    if (open) {
                        IconButton(onClick = { toDelete = e }) {
                            Icon(Icons.Filled.Close, "Delete", tint = p.textDim)
                        }
                    }
                }
                if (open) {
                    e.answers.forEach { a ->
                        Spacer(Modifier.height(16.dp))
                        if (a.rows.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth()) {
                                Text(a.left, style = questionStyle(16), color = p.accent, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Text(a.right, style = questionStyle(16), color = p.accent, modifier = Modifier.weight(1f))
                            }
                            a.rows.forEach { (l, r) ->
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Text(l.ifBlank { "—" }, style = answerStyle(p), modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(12.dp))
                                    Text(r.ifBlank { "—" }, style = answerStyle(p), modifier = Modifier.weight(1f))
                                }
                            }
                        } else {
                            Text(a.question, style = questionStyle(16), color = p.accent)
                            Spacer(Modifier.height(4.dp))
                            Text(a.text, style = answerStyle(p))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    toDelete?.let { e ->
        ConfirmDialog(
            title = "Let this one go?",
            message = "This reflection will be deleted.",
            confirm = "DELETE",
            onConfirm = { onDelete(e.id); toDelete = null },
            onDismiss = { toDelete = null },
        )
    }
}
