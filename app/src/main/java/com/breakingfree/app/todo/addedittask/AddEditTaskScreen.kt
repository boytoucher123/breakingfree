package com.breakingfree.app.todo.addedittask

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.breakingfree.app.roadmap.ActionButton
import com.breakingfree.app.todo.data.Priority
import com.breakingfree.app.todo.domain.Reminder
import com.breakingfree.app.todo.domain.ReminderMode
import com.breakingfree.app.todo.domain.ReminderPlanner
import com.breakingfree.app.todo.domain.Tags
import com.breakingfree.app.todo.ui.SectionHeader
import com.breakingfree.app.todo.ui.TodoPanel
import com.breakingfree.app.todo.ui.WeekdayPicker
import com.breakingfree.app.todo.ui.todoFieldColors
import com.breakingfree.app.todo.util.formatWhen
import com.breakingfree.app.ui.Fonts
import com.breakingfree.app.ui.LocalPalette
import com.breakingfree.app.ui.MonoLabel
import com.breakingfree.app.ui.TagChip
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun AddEditTaskScreen(
    taskId: String?,
    vmKey: String,
    onTaskSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: AddEditTaskViewModel = viewModel(key = vmKey, factory = viewModelFactory { initializer { AddEditTaskViewModel(app, taskId) } })
    val p = LocalPalette.current
    val s by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(s.isTaskSaved) { if (s.isTaskSaved) onTaskSaved() }

    // Leaving with unsaved changes asks first, whether by the back arrow or the system back.
    val leave: () -> Unit = {
        if (s.isDirty && !s.isTaskSaved) {
            confirmLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = leave)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text) }
            MonoLabel(if (s.isNew) "TASK // NEW" else "TASK // EDIT", Modifier.weight(1f), color = p.accent)
            IconButton(onClick = { viewModel.saveTask() }) { Icon(Icons.Filled.Check, "Save", tint = p.accent) }
        }
        Box(Modifier.height(1.dp).fillMaxWidth().background(p.line))
        if (s.isLoading) return@Column

        val d = s.draft
        val message = s.userMessage
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            OutlinedTextField(
                value = d.title, onValueChange = viewModel::updateTitle, singleLine = true,
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = todoFieldColors(),
                shape = RectangleShape,
                isError = message != null && d.title.isBlank(),
                supportingText = if (message != null) {
                    { Text(message) }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = d.description, onValueChange = viewModel::updateDescription,
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), colors = todoFieldColors(),
                shape = RectangleShape,
            )
            if (s.willBeUnfinished) {
                Spacer(Modifier.height(10.dp))
                MonoLabel(
                    "NO " + listOfNotNull("PRIORITY".takeIf { d.priority == Priority.NONE }, "TAGS".takeIf { d.tags.isEmpty() })
                        .joinToString(" OR ") + " // SAVES AS UNFINISHED, NOT SUGGESTED UNTIL FILLED IN",
                    color = p.accent, fontSize = 10.sp,
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("PRIORITY")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { pr ->
                    TagChip(pr.label, selected = d.priority == pr, onClick = { viewModel.updatePriority(pr) })
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("TAGS")
            Spacer(Modifier.height(10.dp))
            TagsEditor(d.tags, s.knownTags, onAdd = viewModel::addTag, onToggle = viewModel::toggleTag)

            Spacer(Modifier.height(22.dp))
            SectionHeader("DUE")
            Spacer(Modifier.height(10.dp))
            DateTimeField(d.dueAt, defaultTime = LocalTime.of(23, 59), onChange = viewModel::updateDueAt)

            Spacer(Modifier.height(22.dp))
            SectionHeader("AVAILABLE FROM")
            MonoLabel("WON'T BE SUGGESTED BEFORE THIS", Modifier.padding(top = 4.dp), fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            DateTimeField(d.startAt, defaultTime = LocalTime.of(0, 0), onChange = viewModel::updateStartAt)

            Spacer(Modifier.height(22.dp))
            SectionHeader("ONLY ON")
            MonoLabel("NONE TICKED = ANY DAY // OTHER DAYS IT'S BLOCKED", Modifier.padding(top = 4.dp), fontSize = 10.sp)
            Spacer(Modifier.height(10.dp))
            WeekdayPicker(d.days, viewModel::updateDays)

            Spacer(Modifier.height(22.dp))
            SectionHeader("REMINDERS")
            Spacer(Modifier.height(10.dp))
            ReminderEditor(d.reminder, viewModel::updateReminder)

            if (s.prerequisiteOptions.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("DO AFTER")
                MonoLabel("WON'T BE SUGGESTED UNTIL THESE ARE DONE", Modifier.padding(top = 4.dp), fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.prerequisiteOptions.forEach { t ->
                        TagChip(
                            (if (t.id in d.blockedBy) "[X] " else "[ ] ") + t.titleForList,
                            selected = t.id in d.blockedBy,
                            onClick = { viewModel.togglePrerequisite(t.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            ActionButton(if (s.isNew) "ADD TASK" else "SAVE", Modifier.fillMaxWidth(), danger = true, onClick = { viewModel.saveTask() })
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmLeave) {
        TodoPanel(onDismiss = { confirmLeave = false }) {
            Text("UNSAVED CHANGES", style = TextStyle(fontFamily = Fonts.Oppressive, fontSize = 22.sp), color = p.text)
            Spacer(Modifier.height(8.dp))
            Text("Save this task before leaving?", color = p.textDim)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("DISCARD", Modifier.weight(1f), onClick = { confirmLeave = false; onBack() })
                ActionButton("SAVE", Modifier.weight(1f), danger = true, onClick = {
                    confirmLeave = false
                    viewModel.saveTask() // navigates away once saved; stays (showing why) if it can't be
                })
            }
            Spacer(Modifier.height(8.dp))
            ActionButton("KEEP EDITING", Modifier.fillMaxWidth(), onClick = { confirmLeave = false })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(tags: List<String>, known: List<String>, onAdd: (String) -> Unit, onToggle: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    val all = (known + tags).distinct().sorted()
    if (all.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { tag -> TagChip("#$tag", selected = tag in tags, onClick = { onToggle(tag) }) }
        }
        Spacer(Modifier.height(10.dp))
    }
    val commit = {
        input.split(',').forEach(onAdd)
        input = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            // Spaces and hyphens become underscores as you type; a comma adds the tag.
            onValueChange = { v ->
                input = Tags.typing(v)
                if (v.endsWith(",")) commit()
            },
            singleLine = true,
            label = { Text("New tag") },
            modifier = Modifier.weight(1f),
            colors = todoFieldColors(),
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
        )
        Spacer(Modifier.width(8.dp))
        ActionButton("ADD", enabled = input.isNotBlank(), onClick = commit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditor(r: Reminder, onChange: (Reminder) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    fun choose(mode: ReminderMode) {
        if (mode != ReminderMode.OFF && Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onChange(r.copy(mode = mode))
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TagChip("OFF", selected = r.mode == ReminderMode.OFF, onClick = { choose(ReminderMode.OFF) })
        TagChip("AT A TIME", selected = r.mode == ReminderMode.AT_TIME, onClick = { choose(ReminderMode.AT_TIME) })
        TagChip("RANDOM IN A WINDOW", selected = r.mode == ReminderMode.RANDOM_WINDOW, onClick = { choose(ReminderMode.RANDOM_WINDOW) })
    }
    if (r.mode == ReminderMode.OFF) return
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (r.mode == ReminderMode.AT_TIME) {
            MonoLabel("AT")
            ActionButton(ReminderPlanner.formatMinute(r.startMinute), onClick = { pickStart = true })
        } else {
            MonoLabel("BETWEEN")
            ActionButton(ReminderPlanner.formatMinute(r.startMinute), onClick = { pickStart = true })
            MonoLabel("AND")
            ActionButton(ReminderPlanner.formatMinute(r.endMinute), onClick = { pickEnd = true })
        }
    }
    if (r.mode == ReminderMode.RANDOM_WINDOW && r.endMinute <= r.startMinute) {
        MonoLabel("THE WINDOW MUST END AFTER IT STARTS", Modifier.padding(top = 6.dp), color = p.accent, fontSize = 10.sp)
    }
    Spacer(Modifier.height(12.dp))
    MonoLabel("ON // NONE TICKED = EVERY DAY", fontSize = 10.sp)
    Spacer(Modifier.height(6.dp))
    WeekdayPicker(r.days) { onChange(r.copy(days = it)) }
    MonoLabel(
        if (r.mode == ReminderMode.AT_TIME) "ONE NOTIFICATION EACH OF THOSE DAYS, UNTIL THE TASK IS DONE"
        else "ONE NOTIFICATION AT A RANDOM MOMENT IN THE WINDOW, EACH OF THOSE DAYS",
        Modifier.padding(top = 6.dp), fontSize = 10.sp,
    )

    if (pickStart) TimeDialog(r.startMinute, onDismiss = { pickStart = false }) { onChange(r.copy(startMinute = it)); pickStart = false }
    if (pickEnd) TimeDialog(r.endMinute, onDismiss = { pickEnd = false }) { onChange(r.copy(endMinute = it)); pickEnd = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    val p = LocalPalette.current
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
    TodoPanel(onDismiss = onDismiss) {
        MonoLabel("TIME", color = p.accent)
        Spacer(Modifier.height(12.dp))
        TimePicker(state = state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("CANCEL", Modifier.weight(1f), onClick = onDismiss)
            ActionButton("SET", Modifier.weight(1f), danger = true, onClick = { onSet(state.hour * 60 + state.minute) })
        }
    }
}

/** Shows a date (and time); tapping opens a date picker, then a time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(value: Long?, defaultTime: LocalTime, onChange: (Long?) -> Unit) {
    val p = LocalPalette.current
    val zone = ZoneId.systemDefault()
    var pickDate by remember { mutableStateOf(false) }
    var pickTimeForDate by remember { mutableStateOf<LocalDate?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        ActionButton(value?.let { formatWhen(it) } ?: "SET DATE", onClick = { pickDate = true })
        if (value != null) {
            ActionButton("TIME", onClick = { pickTimeForDate = Instant.ofEpochMilli(value).atZone(zone).toLocalDate() })
            ActionButton("CLEAR", onClick = { onChange(null) })
        }
    }

    if (pickDate) {
        // DatePicker works in UTC midnights.
        val initial = value?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickDate = false
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = value?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: defaultTime
                        onChange(date.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                }) { Text("OK", color = p.accent) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("CANCEL", color = p.textDim) } },
            shape = RectangleShape,
            colors = DatePickerDefaults.colors(containerColor = p.surface),
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = p.surface))
        }
    }

    pickTimeForDate?.let { date ->
        val current = value?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: defaultTime
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        TodoPanel(onDismiss = { pickTimeForDate = null }) {
            MonoLabel("TIME", color = p.accent)
            Spacer(Modifier.height(12.dp))
            TimePicker(state = state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("END OF DAY", Modifier.weight(1f), onClick = {
                    onChange(date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
                    pickTimeForDate = null
                })
                ActionButton("SET", Modifier.weight(1f), danger = true, onClick = {
                    onChange(date.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli())
                    pickTimeForDate = null
                })
            }
        }
    }
}
