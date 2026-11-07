package com.breakingfree.app.todo.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakingfree.app.roadmap.ActionButton
import com.breakingfree.app.todo.data.Difficulty
import com.breakingfree.app.todo.data.Priority
import com.breakingfree.app.todo.data.TaskSize
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.DisplayStatus
import com.breakingfree.app.todo.domain.NextMode
import com.breakingfree.app.todo.domain.onlyOnLabel
import com.breakingfree.app.todo.ui.SectionHeader
import com.breakingfree.app.todo.ui.StatusBadge
import com.breakingfree.app.todo.util.formatDue
import com.breakingfree.app.todo.util.formatWhen
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.ShiftingTitle
import com.breakingfree.app.ui.TagChip

@Composable
fun TasksScreen(
    onAddTask: () -> Unit,
    onTaskClick: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: TasksViewModel = viewModel(),
) {
    val p = LocalPalette.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        ShiftingTitle("Tasks", "TASKS", fontSize = 34.sp, textAlign = TextAlign.Start)
                        MonoLabel(
                            "${state.openCount} OPEN" +
                                if (state.unfinishedCount > 0) " // ${state.unfinishedCount} UNFINISHED" else "",
                            color = p.accent,
                        )
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Tasks settings", tint = p.textDim) }
                }
            }
            item {
                NextUpCard(
                    card = state.next,
                    hasAnyTasks = state.hasAnyTasks,
                    onOpen = onTaskClick,
                    onImportant = viewModel::pressImportant,
                    onShuffle = viewModel::pressShuffle,
                    onEasy = viewModel::pressEasy,
                    onAdd = onAddTask,
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TasksFilterType.entries.forEach { f ->
                        val label = if (f == TasksFilterType.UNFINISHED && state.unfinishedCount > 0) "${f.label} (${state.unfinishedCount})" else f.label
                        TagChip(label, selected = state.filter == f, onClick = { viewModel.setFiltering(f) })
                    }
                    if (state.filter == TasksFilterType.COMPLETED && state.items.isNotEmpty()) {
                        TagChip("CLEAR DONE", selected = false, onClick = { viewModel.clearCompletedTasks() })
                    }
                }
            }
            if (state.allTags.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.allTags.forEach { tag ->
                            TagChip("#$tag", selected = state.tagFilter == tag, onClick = { viewModel.toggleTag(tag) })
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    if (state.filter == TasksFilterType.UNFINISHED) "UNFINISHED" else "TASKS",
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (state.filter == TasksFilterType.UNFINISHED && state.items.isNotEmpty()) {
                item {
                    MonoLabel(
                        "MISSING PRIORITY, TAGS, SIZE, DIFFICULTY OR INTEREST // NOT SUGGESTED UNTIL FINISHED",
                        Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
                        fontSize = 10.sp,
                    )
                }
            }
            if (!state.isLoading && state.items.isEmpty()) {
                item {
                    MonoLabel(
                        when {
                            state.filter == TasksFilterType.UNFINISHED -> "NOTHING UNFINISHED"
                            state.hasAnyTasks -> "NOTHING HERE // CHANGE THE FILTER"
                            else -> "NO TASKS YET // TAP + TO ADD ONE"
                        },
                        Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
            items(state.items, key = { it.task.id }) { row ->
                TaskItem(row, onClick = { onTaskClick(row.task.id) }, onCheckedChange = { viewModel.completeTask(row.task, it) })
            }
        }

        // Square FAB in the BreakingFree style.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp)
                .background(p.accent)
                .clickable(onClick = onAddTask),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add task", tint = p.background)
        }
    }
}

@Composable
private fun NextUpCard(
    card: NextCard,
    hasAnyTasks: Boolean,
    onOpen: (String) -> Unit,
    onImportant: () -> Unit,
    onShuffle: () -> Unit,
    onEasy: () -> Unit,
    onAdd: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .border(1.dp, p.accentDim)
            .background(p.surface)
            .padding(14.dp),
    ) {
        val task = card.task
        val header = when {
            card.selection.mode == NextMode.EASY && card.total > 0 -> "NEXT // EASY"
            task == null -> "NEXT //"
            card.selection.mode == NextMode.SHUFFLE -> "NEXT // RANDOM"
            else -> "NEXT // #${card.rank + 1} OF ${card.total}"
        }
        MonoLabel(header, Modifier.padding(bottom = 8.dp), color = p.accent, fontSize = 11.sp)
        if (task == null && card.total > 0) {
            // Easy found nothing small enough; the other buttons still work.
            Text("Nothing small to pick.", color = p.text, style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 20.sp))
            Spacer(Modifier.height(4.dp))
            MonoLabel("GIVE SOME TASKS A SIZE OF TINY OR SMALL", fontSize = 11.sp)
        } else if (task == null) {
            Text(
                if (hasAnyTasks) "Nothing you can start right now." else "Nothing to do.",
                color = p.text,
                style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 20.sp),
            )
            Spacer(Modifier.height(10.dp))
            ActionButton("+ ADD A TASK", onClick = onAdd)
            return@Column
        } else Column(Modifier.fillMaxWidth().clickable { onOpen(task.id) }) {
            Text(
                card.title,
                color = p.text,
                style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 22.sp, lineHeight = 25.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaLine(task)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MonoLabel(meta, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Equal thirds.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("IMPORTANT", Modifier.weight(1f), onClick = onImportant)
            ActionButton("RANDOM", Modifier.weight(1f), onClick = onShuffle)
            ActionButton("EASY", Modifier.weight(1f), onClick = onEasy)
        }
    }
}

internal fun metaLine(task: Task): String = listOfNotNull(
    if (task.isInProgress) "IN PROGRESS" else null,
    task.dueAt?.let { formatDue(it) },
    task.priority.takeIf { it != Priority.NONE && !task.isSystem }?.let { "${it.label} PRIORITY" },
    task.size.takeIf { it != TaskSize.UNSET }?.let { "~${it.label}" },
    task.difficulty.takeIf { it != Difficulty.UNSET }?.let { it.label },
    onlyOnLabel(task.days, task.windowStart, task.windowEnd),
    task.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
).joinToString("  //  ")

@Composable
private fun TaskItem(row: TaskRow, onClick: () -> Unit, onCheckedChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val task = row.task
    val heat = when (task.priority) {
        Priority.HIGH -> 1f
        Priority.MEDIUM -> 0.6f
        Priority.LOW -> 0.25f
        Priority.NONE -> 0f
    }
    val dim = task.isCompleted || row.status == DisplayStatus.WAITING || row.status == DisplayStatus.BLOCKED
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .border(1.dp, if (row.status == DisplayStatus.IN_PROGRESS) p.accentDim else p.line)
            .background(p.surface)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left "heat" strip, as on the Chaos news cards: wider and redder for higher priority.
        Box(
            Modifier
                .width((3 + heat * 5).dp)
                .height(60.dp)
                .background(if (heat == 0f) p.line else p.accent.copy(alpha = 0.45f + heat * 0.55f)),
        )
        // Square checkbox (the system task resolves itself, so it has none).
        if (!task.isSystem) {
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(22.dp)
                    .border(1.dp, if (task.isCompleted) p.accent else p.textDim)
                    .clickable { onCheckedChange(!task.isCompleted) },
                contentAlignment = Alignment.Center,
            ) {
                if (task.isCompleted) Icon(Icons.Filled.Check, contentDescription = "Done", tint = p.accent, modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
            MonoLabel("SYS", color = p.accent, fontSize = 10.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                row.title,
                color = if (dim) p.textDim else p.text,
                style = TextStyle(fontFamily = Fonts.Quiet, fontSize = 18.sp, lineHeight = 21.sp),
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val now = System.currentTimeMillis()
            val when_ = task.startAt?.takeIf { it > now && !task.isCompleted }?.let { "FROM " + formatWhen(it, now) }
            val meta = listOfNotNull(when_, metaLine(task).replace("IN PROGRESS  //  ", "").replace("IN PROGRESS", "").ifEmpty { null })
                .joinToString("  //  ")
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                MonoLabel(meta, fontSize = 10.sp)
            }
        }
        if (row.status != DisplayStatus.TODO && row.status != DisplayStatus.DONE) {
            StatusBadge(row.status, Modifier.padding(end = 10.dp))
        }
    }
}
