package com.breakingfree.app.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.breakingfree.app.todo.data.SystemKind
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.DisplayStatus
import com.breakingfree.app.todo.domain.Weekdays
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.TagChip
import java.time.DayOfWeek

/** The system task's title includes the live count. */
fun displayTitle(task: Task, all: List<Task>): String = when (task.system) {
    SystemKind.FINISH_UNFINISHED -> {
        val n = all.count { it.isUnfinished }
        if (n == 0) "Finish unfinished tasks" else "Finish $n unfinished task${if (n == 1) "" else "s"}"
    }
    null -> task.titleForList
}

/** Square bordered dialog surface (the BreakingFree roadmap dialog style). */
@Composable
fun TodoPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, p.accentDim)
                .background(p.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) { content() }
    }
}

@Composable
fun todoFieldColors(): TextFieldColors {
    val p = LocalPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
        focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent,
        focusedLabelColor = p.accent, unfocusedLabelColor = p.textDim,
    )
}

/** Anton section header followed by a rule, as on the roadmap. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 18.sp, letterSpacing = 3.sp), color = p.text)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(p.line))
    }
}

@Composable
fun statusColor(s: DisplayStatus): Color {
    val p = LocalPalette.current
    return when (s) {
        DisplayStatus.IN_PROGRESS -> p.accent
        DisplayStatus.BLOCKED, DisplayStatus.UNFINISHED -> p.accent.copy(alpha = 0.75f)
        DisplayStatus.WAITING -> p.accentDim
        DisplayStatus.TODO, DisplayStatus.DONE -> p.textDim
    }
}

/** Small bordered status label. */
@Composable
fun StatusBadge(s: DisplayStatus, modifier: Modifier = Modifier) {
    val c = statusColor(s)
    Box(modifier.border(1.dp, c).padding(horizontal = 6.dp, vertical = 2.dp)) {
        MonoLabel(s.label, color = c, fontSize = 9.sp)
    }
}

/** Seven toggle chips. Nothing ticked means "any day". */
@Composable
fun WeekdayPicker(mask: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { d ->
            TagChip(
                Weekdays.short(d).take(2),
                selected = Weekdays.contains(mask, d),
                onClick = { onChange(Weekdays.toggle(mask, d)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
