package com.breakingfree.app.todo.taskdetail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.breakingfree.app.roadmap.ActionButton
import com.breakingfree.app.roadmap.ConfirmDialog
import com.breakingfree.app.todo.data.Priority
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.Block
import com.breakingfree.app.todo.domain.DisplayStatus
import com.breakingfree.app.todo.domain.ReminderMode
import com.breakingfree.app.todo.domain.ReminderPlanner
import com.breakingfree.app.todo.domain.TaskStatus
import com.breakingfree.app.todo.domain.Weekdays
import com.breakingfree.app.todo.ui.SectionHeader
import com.breakingfree.app.todo.ui.StatusBadge
import com.breakingfree.app.todo.util.formatDue
import com.breakingfree.app.todo.util.formatWhen
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel

@Composable
fun TaskDetailScreen(
    taskId: String,
    vmKey: String,
    onEditTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: TaskDetailViewModel = viewModel(key = vmKey, factory = viewModelFactory { initializer { TaskDetailViewModel(app, taskId) } })
    val p = LocalPalette.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onDeleted() }

    val task = state.task
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text) }
            MonoLabel(if (task?.isSystem == true) "TASK // SYSTEM" else "TASK // DETAIL", Modifier.weight(1f), color = p.accent)
            if (task != null && !task.isSystem) {
                IconButton(onClick = { onEditTask(taskId) }) { Icon(Icons.Filled.Edit, "Edit", tint = p.text) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = p.accent) }
            }
        }
        Box(Modifier.height(1.dp).fillMaxWidth().background(p.line))

        if (task == null) {
            if (!state.isLoading) MonoLabel("TASK NOT FOUND", Modifier.padding(20.dp))
            return@Column
        }

        // Scrolling content; the action bar below stays pinned to the bottom of the screen.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(
                state.title,
                color = if (task.isCompleted) p.textDim else p.text,
                style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 28.sp, letterSpacing = 1.sp, lineHeight = 34.sp),
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.height(10.dp))
            StatusBadge(state.status)
            Spacer(Modifier.height(8.dp))
            val now = System.currentTimeMillis()
            val why = when (state.block) {
                Block.NOT_YET -> "NOT AVAILABLE UNTIL " + formatWhen(task.startAt!!, now)
                Block.PREREQUISITE -> "WAITING ON ANOTHER TASK"
                Block.WRONG_DAY -> "ONLY ON " + Weekdays.label(task.days)
                null -> null
            }
            val facts = listOfNotNull(
                why?.takeIf { !task.isCompleted },
                if (state.status == DisplayStatus.UNFINISHED) "MISSING " + listOfNotNull(
                    "PRIORITY".takeIf { task.priority == Priority.NONE },
                    "TAGS".takeIf { task.tags.isEmpty() },
                ).joinToString(" + ") + " // EDIT TO FINISH" else null,
                task.priority.takeIf { it != Priority.NONE && !task.isSystem }?.let { "${it.label} PRIORITY" },
                task.dueAt?.let { formatDue(it, now) },
                task.startAt?.takeIf { state.block != Block.NOT_YET }?.let { "AVAILABLE FROM " + formatWhen(it, now) },
                task.days.takeIf { it != Weekdays.ANY && state.block != Block.WRONG_DAY }?.let { "ONLY ON " + Weekdays.label(it) },
                reminderLine(task),
            )
            facts.forEach { MonoLabel(it, color = if (it == why || it.startsWith("MISSING")) p.accent else p.textDim) }
            if (task.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                MonoLabel(task.tags.joinToString("  ") { "#$it" }, color = p.text)
            }
            if (task.description.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(task.description, color = p.text, style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp))
            }
            if (state.unfinished.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("TO FINISH")
                state.unfinished.forEach { LinkedTask(it, onClick = { onEditTask(it.id) }) }
            }
            if (state.blockedBy.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("AFTER")
                state.blockedBy.forEach { LinkedTask(it, onClick = { onOpenTask(it.id) }) }
            }
            if (state.blocking.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("UNLOCKS")
                state.blocking.forEach { LinkedTask(it, onClick = { onOpenTask(it.id) }) }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Sticky action bar. The system task resolves itself, so it has none.
        if (!task.isSystem) {
            Box(Modifier.height(1.dp).fillMaxWidth().background(p.line))
            Row(
                Modifier.fillMaxWidth().background(p.background).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (task.status) {
                    TaskStatus.TODO -> ActionButton("START", Modifier.weight(1f), onClick = { viewModel.setStatus(TaskStatus.IN_PROGRESS) })
                    TaskStatus.IN_PROGRESS -> ActionButton("PAUSE", Modifier.weight(1f), onClick = { viewModel.setStatus(TaskStatus.TODO) })
                    TaskStatus.DONE -> {}
                }
                ActionButton(
                    if (task.isCompleted) "MARK NOT DONE" else "MARK DONE",
                    Modifier.weight(1f),
                    danger = !task.isCompleted,
                    onClick = { viewModel.setStatus(if (task.isCompleted) TaskStatus.TODO else TaskStatus.DONE) },
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "DELETE TASK",
            message = "This can't be undone.",
            confirm = "DELETE",
            onConfirm = { confirmDelete = false; viewModel.deleteTask() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun reminderLine(task: Task): String? {
    val r = task.reminder
    val days = if (r.days == Weekdays.ANY) "DAILY" else Weekdays.label(r.days)
    return when (r.mode) {
        ReminderMode.OFF -> null
        ReminderMode.AT_TIME -> "REMINDER // $days AT ${ReminderPlanner.formatMinute(r.startMinute)}"
        ReminderMode.RANDOM_WINDOW ->
            "REMINDER // $days, RANDOM ${ReminderPlanner.formatMinute(r.startMinute)}–${ReminderPlanner.formatMinute(r.endMinute)}"
    }
}

@Composable
private fun LinkedTask(task: Task, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .border(1.dp, p.line)
            .background(p.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(if (task.isCompleted) "[X]" else "[ ]", color = if (task.isCompleted) p.accent else p.textDim)
        Text(
            task.titleForList,
            Modifier.padding(start = 10.dp),
            color = if (task.isCompleted) p.textDim else p.text,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
        )
    }
}
