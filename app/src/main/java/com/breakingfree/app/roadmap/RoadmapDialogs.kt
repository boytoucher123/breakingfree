package com.breakingfree.app.roadmap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.TagChip

@Composable
private fun Panel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, p.accentDim)
                .background(p.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) { content() }
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = when {
        !enabled -> p.line
        danger -> p.accent
        else -> p.text
    }
    Box(
        modifier
            .border(1.dp, if (enabled) (if (danger) p.accent else p.line) else p.line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { MonoLabel(text, color = c, fontSize = 13.sp) }
}

@Composable
fun NodeDialog(
    map: Roadmap,
    node: RoadNode,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddInside: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    var confirmDelete by remember { mutableStateOf(false) }
    val parent = map.effectiveParent(node)
    val kids = map.children(node.id)
    val siblings = map.children(parent?.id)
    val si = siblings.indexOfFirst { it.id == node.id }
    val path = map.path()
    val idx = path.indexOfFirst { it.node.id == node.id }
    val color = colorAt(idx, path.size, map.endGoalIndex(path), node.tier)

    Panel(onDismiss) {
        MonoLabel(
            node.tier.label.uppercase() + (if (node.endGoal) "  //  END GOAL" else "") +
                (if (node.done) "  //  CLEARED" else ""),
            color = color,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            node.title,
            style = when (node.tier) {
                Tier.MAJOR -> TextStyle(fontFamily = Fonts.Gothic, fontSize = 30.sp)
                Tier.REGULAR -> TextStyle(fontFamily = Fonts.Oppressive, fontSize = 24.sp, letterSpacing = 1.sp)
                Tier.MINOR -> TextStyle(fontFamily = Fonts.Technical, fontSize = 20.sp)
            },
            color = p.text,
        )
        if (parent != null) {
            Spacer(Modifier.height(4.dp))
            MonoLabel("PART OF: ${parent.title.uppercase()}")
        }
        if (kids.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            MonoLabel("${kids.count { it.done }} / ${kids.size} STEPS WITHIN CLEARED")
        }
        if (node.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(node.notes, color = p.textDim, style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp))
        }
        Spacer(Modifier.height(18.dp))
        ActionButton(if (node.done) "MARK AS NOT CLEARED" else "MARK AS CLEARED", Modifier.fillMaxWidth(),
            danger = !node.done) { onToggle(); onDismiss() }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("EDIT", Modifier.weight(1f), onClick = onEdit)
            if (node.tier != Tier.MINOR) ActionButton("+ STEP INSIDE", Modifier.weight(1f), onClick = onAddInside)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("▼ EARLIER", Modifier.weight(1f), enabled = si > 0) { onMove(-1) }
            ActionButton("▲ LATER", Modifier.weight(1f), enabled = si in 0 until siblings.size - 1) { onMove(1) }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton("DELETE", Modifier.fillMaxWidth(), danger = true) { confirmDelete = true }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Erase \"${node.title}\"?",
            message = when {
                node.tier == Tier.MAJOR && kids.isNotEmpty() ->
                    "This epoch and all ${map.descendants(node.id).size} milestone(s) inside it will be erased."
                kids.isNotEmpty() -> "Its ${kids.size} inner step(s) will be kept and moved up a level."
                else -> "This milestone will be removed from the map."
            },
            confirm = "ERASE",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun EditNodeDialog(
    map: Roadmap,
    existing: RoadNode?,
    presetParent: String?,
    presetTier: Tier,
    onDismiss: () -> Unit,
    onSave: (title: String, notes: String, tier: Tier, parentId: String?, endGoal: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var tier by remember { mutableStateOf(existing?.tier ?: presetTier) }
    var parentId by remember { mutableStateOf(existing?.let { map.effectiveParent(it)?.id } ?: presetParent) }
    var parentMenu by remember { mutableStateOf(false) }
    var endGoal by remember { mutableStateOf(existing?.endGoal ?: false) }

    val noMajors = map.nodes.none { it.tier == Tier.MAJOR && it.id != existing?.id }
    // A major that holds milestones can't be demoted — they'd have nowhere to live.
    val lockedMajor = existing != null && existing.tier == Tier.MAJOR && map.children(existing.id).isNotEmpty()
    if (noMajors || lockedMajor) tier = Tier.MAJOR
    val eligible = map.eligibleParents(tier, existing?.id)
    if (tier != Tier.MAJOR && eligible.none { it.id == parentId }) {
        parentId = eligible.firstOrNull { it.tier == Tier.MAJOR }?.id ?: eligible.firstOrNull()?.id
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
        focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
        focusedLabelColor = p.accent, unfocusedLabelColor = p.textDim,
    )

    Panel(onDismiss) {
        Text(
            if (existing == null) "New Milestone" else "Amend Milestone",
            style = TextStyle(fontFamily = Fonts.Gothic, fontSize = 28.sp), color = p.text,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it }, singleLine = true,
            label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), colors = fieldColors,
        )
        Spacer(Modifier.height(14.dp))
        MonoLabel("RANK")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TagChip("MAJOR", tier == Tier.MAJOR, { tier = Tier.MAJOR })
            if (!noMajors && !lockedMajor) {
                TagChip("MILESTONE", tier == Tier.REGULAR, { tier = Tier.REGULAR })
                TagChip("MINOR", tier == Tier.MINOR, { tier = Tier.MINOR })
            }
        }
        Spacer(Modifier.height(4.dp))
        MonoLabel(
            when {
                noMajors -> "THE FIRST STONE MUST BE A MAJOR — AN EPOCH."
                lockedMajor -> "HOLDS MILESTONES — STAYS A MAJOR."
                tier == Tier.MAJOR -> "AN EPOCH. A CHAPTER TITLE."
                tier == Tier.REGULAR -> "MEASURABLE. AN INCOME, A HOUSE, A CAR."
                else -> "A GROUNDED TASK. SOMETHING YOU CAN DO."
            },
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(14.dp))
        if (tier == Tier.MAJOR) {
            TagChip(if (endGoal) "◆ THE END GOAL" else "◇ MARK AS THE END GOAL", endGoal, { endGoal = !endGoal })
            Spacer(Modifier.height(4.dp))
            MonoLabel("MAJORS AFTER THE END GOAL FALL INTO THE PURPLE BEYOND.", fontSize = 10.sp)
        } else {
        MonoLabel("INSIDE")
        Spacer(Modifier.height(6.dp))
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, p.line)
                    .clickable(enabled = eligible.isNotEmpty()) { parentMenu = true }
                    .padding(12.dp)
            ) {
                Text(
                    (eligible.firstOrNull { it.id == parentId }?.title ?: "Choose…") + "  ▾",
                    color = p.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                eligible.forEach { e ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (e.tier == Tier.MAJOR) "◆ " else "    ◇ ") + e.title,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        onClick = { parentId = e.id; parentMenu = false },
                    )
                }
            }
        }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("CANCEL", Modifier.weight(1f), onClick = onDismiss)
            ActionButton("SAVE", Modifier.weight(1f), danger = true,
                enabled = title.isNotBlank() && (tier == Tier.MAJOR || parentId != null)) {
                onSave(title, notes, tier, if (tier == Tier.MAJOR) null else parentId, tier == Tier.MAJOR && endGoal)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Panel(onDismiss) {
        Text(title, style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 22.sp), color = p.text)
        Spacer(Modifier.height(8.dp))
        Text(message, color = p.textDim)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("CANCEL", Modifier.weight(1f), onClick = onDismiss)
            ActionButton(confirm, Modifier.weight(1f), danger = true, onClick = onConfirm)
        }
    }
}
