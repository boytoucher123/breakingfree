package com.breakingfree.app.lattice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.roadmap.ConfirmDialog
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.Palette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private fun questionStyle(size: Int = 22) =
    TextStyle(fontFamily = Fonts.Quiet, fontStyle = FontStyle.Italic, fontSize = size.sp, lineHeight = (size + 5).sp)

private fun answerStyle(p: Palette) =
    TextStyle(fontFamily = Fonts.Quiet, fontSize = 19.sp, lineHeight = 24.sp, color = p.text)

@Composable
private fun QuietLabel(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text, modifier = modifier, color = p.textDim,
        style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 14.sp, letterSpacing = 2.sp),
    )
}

@Composable
fun LatticeScreen(vm: LatticeViewModel = viewModel()) {
    val p = LocalPalette.current
    val s by vm.state.collectAsState()
    val order by vm.order.collectAsState()
    var archive by rememberSaveable { mutableStateOf(false) }
    var kept by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.shuffle() }
    LaunchedEffect(kept) { if (kept) { delay(2600); kept = false } }
    BackHandler(enabled = archive) { archive = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(p.surfaceHigh, p.background, p.background)))
    ) {
        LatticeLines(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (archive) {
                    IconButton(onClick = { archive = false }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = p.textDim)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (archive) "What was kept" else "The Lattice",
                        style = questionStyle(36), color = p.text,
                    )
                    QuietLabel(if (archive) "${s.entries.size} reflections" else "quiet questions. no one is watching.")
                }
                if (!archive) {
                    IconButton(onClick = vm::shuffle) { Icon(Icons.Filled.Refresh, "Shuffle", tint = p.textDim) }
                    IconButton(onClick = { archive = true }) { Icon(Icons.Filled.List, "Saved reflections", tint = p.accent) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (archive) Archive(s.entries, vm::deleteEntry)
            else Questions(vm, s, order, onKeep = { if (vm.keep()) kept = true })
        }
        AnimatedVisibility(
            kept, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .border(1.dp, p.accentDim)
                    .background(p.surface)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) { Text("kept.", style = questionStyle(18), color = p.text) }
        }
    }
}

@Composable
private fun LatticeLines(modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val step = 64.dp.toPx()
        val c = p.line.copy(alpha = 0.35f)
        var x = -size.height
        while (x < size.width) {
            drawLine(c, Offset(x, 0f), Offset(x + size.height, size.height), 1f)
            drawLine(c, Offset(x + size.height, 0f), Offset(x, size.height), 1f)
            x += step
        }
    }
}

@Composable
private fun Questions(vm: LatticeViewModel, s: LatticeState, order: List<Prompt>, onKeep: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        order.forEach { prompt ->
            Spacer(Modifier.height(26.dp))
            when (prompt) {
                is Prompt.Single -> {
                    Text(prompt.question, style = questionStyle(), color = p.text)
                    Spacer(Modifier.height(10.dp))
                    QuietBox(s.singles[prompt.id].orEmpty(), { vm.setSingle(prompt.id, it) }, Modifier.fillMaxWidth(), 84)
                }
                is Prompt.Table -> TableQuestion(vm, prompt, s.tables[prompt.id].orEmpty().ifEmpty { listOf("" to "") })
            }
        }
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, if (s.hasDraft) p.accent else p.line)
                .clickable(enabled = s.hasDraft, onClick = onKeep)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("keep these thoughts", style = questionStyle(20), color = if (s.hasDraft) p.accent else p.textDim)
        }
        Spacer(Modifier.height(10.dp))
        QuietLabel("drafts stay here until you keep them.", Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(48.dp))
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
            .border(1.dp, p.line)
            .background(p.surface.copy(alpha = 0.55f))
            .padding(12.dp),
    )
}

@Composable
private fun TableQuestion(vm: LatticeViewModel, prompt: Prompt.Table, rows: List<Pair<String, String>>) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().border(1.dp, p.line)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(p.surfaceHigh.copy(alpha = 0.6f))) {
            Text(prompt.left, style = questionStyle(19), color = p.text,
                modifier = Modifier.weight(1f).padding(12.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
            Text(prompt.right, style = questionStyle(19), color = p.text,
                modifier = Modifier.weight(1f).padding(12.dp))
        }
        rows.forEachIndexed { i, (l, r) ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                BasicTextField(
                    value = l, onValueChange = { vm.setCell(prompt.id, i, true, it) },
                    textStyle = answerStyle(p), cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).padding(12.dp),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(p.line))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = r, onValueChange = { vm.setCell(prompt.id, i, false, it) },
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
                                .width(16.dp)
                                .clickable { vm.removeRow(prompt.id, i) },
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
                .clickable { vm.addRow(prompt.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
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
                    .background(p.surface.copy(alpha = 0.7f))
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
