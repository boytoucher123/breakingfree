package com.breakingfree.app.todo.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.roadmap.ActionButton
import com.breakingfree.app.roadmap.ConfirmDialog
import com.breakingfree.app.todo.domain.Weights
import com.breakingfree.app.todo.ui.SectionHeader
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import kotlin.math.roundToInt

@Composable
fun TodoSettingsScreen(onBack: () -> Unit, viewModel: TodoSettingsViewModel = viewModel()) {
    val p = LocalPalette.current
    val w by viewModel.weights.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<TagUse?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text) }
            MonoLabel("TASKS // SETTINGS", Modifier.weight(1f), color = p.accent)
        }
        Box(Modifier.height(1.dp).fillMaxWidth().background(p.line))

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            SectionHeader("PRIORITY")
            MonoLabel("POINTS EACH PRIORITY ADDS TO A TASK'S IMPORTANCE", Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 10.sp)
            WeightSlider(viewModel::weightsSettled, "HIGH", w.priorityHigh, 0f..20f) { viewModel.setWeights(w.copy(priorityHigh = it)) }
            WeightSlider(viewModel::weightsSettled, "MEDIUM", w.priorityMedium, 0f..20f) { viewModel.setWeights(w.copy(priorityMedium = it)) }
            WeightSlider(viewModel::weightsSettled, "LOW", w.priorityLow, 0f..20f) { viewModel.setWeights(w.copy(priorityLow = it)) }

            Spacer(Modifier.height(18.dp))
            SectionHeader("DEADLINES")
            MonoLabel("POINTS FOR HOW CLOSE THE DEADLINE IS", Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 10.sp)
            WeightSlider(viewModel::weightsSettled, "OVERDUE", w.overdue, 0f..30f) { viewModel.setWeights(w.copy(overdue = it)) }
            WeightSlider(viewModel::weightsSettled, "DUE SOON", w.dueSoon, 0f..30f) { viewModel.setWeights(w.copy(dueSoon = it)) }
            WeightSlider(viewModel::weightsSettled, "\"SOON\" MEANS WITHIN (HOURS)", w.dueSoonHours, 1f..168f, step = 1.0, decimals = 0) {
                viewModel.setWeights(w.copy(dueSoonHours = it))
            }
            MonoLabel("LATER DEADLINES FADE: DUE SOON × SOON HOURS ÷ HOURS LEFT", fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            WeightSlider(viewModel::weightsSettled, "SIZE EFFECT", w.sizeEffect, 0f..2f, step = 0.1) { viewModel.setWeights(w.copy(sizeEffect = it)) }
            MonoLabel(
                "DEADLINE POINTS × SIZE. AT 1: TINY ×0.5, SMALL ×0.75, MEDIUM ×1, LARGE ×1.5, HUGE ×2. NO DEADLINE, NO EFFECT",
                fontSize = 10.sp,
            )

            Spacer(Modifier.height(18.dp))
            SectionHeader("RANDOM")
            MonoLabel("ONLY AFFECTS THE RANDOM PICK", Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 10.sp)
            WeightSlider(viewModel::weightsSettled, "BASE CHANCE", w.shuffleBase, 0f..10f) { viewModel.setWeights(w.copy(shuffleBase = it)) }
            WeightSlider(viewModel::weightsSettled, "IN PROGRESS BOOST (×)", w.inProgressBoost, 1f..10f) { viewModel.setWeights(w.copy(inProgressBoost = it)) }
            WeightSlider(viewModel::weightsSettled, "INTEREST EFFECT (±)", w.interestBoost, 0f..2f, step = 0.1) { viewModel.setWeights(w.copy(interestBoost = it)) }
            MonoLabel("CHANCE ∝ (BASE + IMPORTANCE) × BOOST IF IN PROGRESS × (1 + EFFECT × INTEREST ÷ 5). INTEREST −5 .. +5, NEVER BELOW ×0.1", fontSize = 10.sp)

            Spacer(Modifier.height(18.dp))
            SectionHeader("EASY")
            MonoLabel("PICKS SMALL TASKS, SMALLER AND EASIER ONES LIKELIER", Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 10.sp)
            WeightSlider(viewModel::weightsSettled, "BIGGEST SIZE (1 TINY .. 5 HUGE)", w.easyMaxSize, 1f..5f, step = 1.0, decimals = 0) {
                viewModel.setWeights(w.copy(easyMaxSize = it))
            }
            MonoLabel("TASKS WITH NO SIZE SET ARE NEVER PICKED. INTEREST BOOSTS THIS TOO", fontSize = 10.sp)
            Spacer(Modifier.height(12.dp))
            ActionButton("RESET TO DEFAULTS", Modifier.fillMaxWidth(), enabled = w != Weights(), onClick = viewModel::resetWeights)

            Spacer(Modifier.height(26.dp))
            SectionHeader("TAGS")
            MonoLabel("DELETING A TAG REMOVES IT FROM EVERY TASK", Modifier.padding(top = 4.dp, bottom = 6.dp), fontSize = 10.sp)
            if (tags.isEmpty()) MonoLabel("NO TAGS YET", Modifier.padding(vertical = 8.dp))
            tags.forEach { t ->
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .border(1.dp, p.line)
                        .background(p.surface)
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#${t.tag}", Modifier.weight(1f), color = p.text, style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 17.sp))
                    MonoLabel("${t.count} TASK${if (t.count == 1) "" else "S"}", fontSize = 10.sp)
                    IconButton(onClick = { deleting = t }) { Icon(Icons.Filled.Delete, "Delete tag", tint = p.accent) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    deleting?.let { t ->
        ConfirmDialog(
            title = "DELETE #${t.tag}",
            message = "Removes it from ${t.count} task${if (t.count == 1) "" else "s"}. Tasks left with no tags move to Unfinished.",
            confirm = "DELETE",
            onConfirm = { viewModel.deleteTag(t.tag); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun WeightSlider(
    onSettled: () -> Unit,
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    step: Double = 0.5,
    decimals: Int = 1,
    onChange: (Double) -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(label, Modifier.weight(1f), fontSize = 11.sp)
            MonoLabel(if (decimals == 0) value.roundToInt().toString() else "%.1f".format(value), color = p.text, fontSize = 12.sp)
        }
        Slider(
            value = value.toFloat().coerceIn(range),
            onValueChange = { v -> onChange((v / step).roundToInt() * step) },
            valueRange = range,
            onValueChangeFinished = onSettled,
            colors = SliderDefaults.colors(
                thumbColor = p.accent,
                activeTrackColor = p.accent,
                inactiveTrackColor = p.line,
            ),
        )
    }
}
