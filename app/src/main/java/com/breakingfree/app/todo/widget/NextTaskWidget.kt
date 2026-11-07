package com.breakingfree.app.todo.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.breakingfree.app.MainActivity
import com.breakingfree.app.R
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.data.Priority
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.NextMode
import com.breakingfree.app.todo.domain.NextSelection
import com.breakingfree.app.todo.domain.NextTask
import com.breakingfree.app.todo.domain.Weights
import com.breakingfree.app.todo.reminders.Reminders
import com.breakingfree.app.todo.ui.displayTitle
import com.breakingfree.app.todo.util.formatDue

// Widget state, stored per widget instance.
private val ModeKey = stringPreferencesKey("mode")
private val RankKey = intPreferencesKey("rank")
private val ShuffledKey = stringPreferencesKey("shuffled")

private fun Preferences.selection() = NextSelection(
    mode = NextMode.of(this[ModeKey]),
    rank = this[RankKey] ?: 0,
    shuffledId = this[ShuffledKey],
)

private suspend fun store(context: Context, id: GlanceId, selection: NextSelection) {
    updateAppWidgetState(context, id) { prefs ->
        prefs[ModeKey] = selection.mode.name
        prefs[RankKey] = selection.rank
        if (selection.shuffledId != null) prefs[ShuffledKey] = selection.shuffledId else prefs.remove(ShuffledKey)
    }
    NextTaskWidget().update(context, id)
}

private suspend fun currentSelection(context: Context, id: GlanceId): NextSelection =
    getAppWidgetState(context, PreferencesGlanceStateDefinition, id).selection()

/** Home-screen widget showing the task to do next. */
class NextTaskWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = TodoGraph.get(context)
        val initial = graph.repository.getTasks()
        provideContent {
            val tasks by remember { graph.repository.getTasksStream() }.collectAsState(initial)
            val weights by graph.weights.weights.collectAsState()
            Content(tasks, currentState<Preferences>().selection(), weights)
        }
    }

    @Composable
    private fun Content(tasks: List<Task>, selection: NextSelection, weights: Weights) {
        val now = System.currentTimeMillis()
        val resolved = NextTask.resolve(tasks.map { it.toCandidate() }, selection, now, weights)
        val task = resolved.task?.let { c -> tasks.first { it.id == c.id } }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_bg))
                .padding(12.dp),
        ) {
            val header = when {
                selection.mode == NextMode.EASY && resolved.total > 0 -> "NEXT // EASY"
                task == null -> "NEXT //"
                selection.mode == NextMode.SHUFFLE -> "NEXT // RANDOM"
                else -> "NEXT // #${resolved.rank + 1} OF ${resolved.total}"
            }
            Text(header, style = label(AccentColor, 10))
            Spacer(GlanceModifier.height(6.dp))
            Box(
                GlanceModifier.fillMaxWidth().defaultWeight().clickable(
                    actionStartActivity<MainActivity>(actionParametersOf(OpenTaskParam to (task?.id ?: ""))),
                ),
            ) {
                Column {
                    Text(
                        task?.let { displayTitle(it, tasks) } ?: if (resolved.total > 0) "NOTHING SMALL TO PICK" else "NOTHING TO DO",
                        style = TextStyle(color = ColorProvider(TextColor), fontSize = 19.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif),
                        maxLines = 2,
                    )
                    val meta = when {
                        task == null && resolved.total > 0 -> "GIVE SOME TASKS A SIZE OF TINY OR SMALL"
                        task == null -> "TAP TO ADD A TASK"
                        else -> listOfNotNull(
                            if (task.isInProgress) "IN PROGRESS" else null,
                            task.dueAt?.let { formatDue(it, now) },
                            task.priority.takeIf { it != Priority.NONE && !task.isSystem }?.let { "${it.label} PRIORITY" },
                            task.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                        ).joinToString("  //  ")
                    }
                    if (meta.isNotEmpty()) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text(meta, style = label(DimColor, 10), maxLines = 1)
                    }
                }
            }
            // Shown whenever there is anything startable, so Easy finding nothing small isn't a dead end.
            if (resolved.total > 0) {
                Row(GlanceModifier.fillMaxWidth()) {
                    // Equal thirds.
                    WidgetButton("IMPORTANT", actionRunCallback<MostImportantAction>(), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(5.dp))
                    WidgetButton("RANDOM", actionRunCallback<ShuffleAction>(), GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(5.dp))
                    WidgetButton("EASY", actionRunCallback<EasyAction>(), GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun WidgetButton(text: String, onClick: Action, modifier: GlanceModifier = GlanceModifier) {
        Box(
            modifier
                .height(36.dp)
                .background(ImageProvider(R.drawable.widget_cell))
                .clickable(onClick)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = label(TextColor, 11).copy(textAlign = TextAlign.Center), maxLines = 1)
        }
    }

    private fun label(color: Color, size: Int) =
        TextStyle(color = ColorProvider(color), fontSize = size.sp, fontFamily = FontFamily.Serif)

    companion object {
        private val AccentColor = Color(0xFF2F7DE0)
        private val TextColor = Color(0xFFC4DAF5)
        private val DimColor = Color(0xFF7891B8)
        /** Read by [MainActivity] to open a task ("" = add a task). */
        val OpenTaskParam = ActionParameters.Key<String>(Reminders.EXTRA_OPEN_TASK)

        suspend fun refreshAll(context: Context) = NextTaskWidget().updateAll(context)
    }
}

private suspend fun candidates(context: Context) =
    TodoGraph.get(context).repository.getTasks().map { it.toCandidate() }

private fun weights(context: Context) = TodoGraph.get(context).weights.weights.value

class MostImportantAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val next = NextTask.pressImportant(candidates(context), currentSelection(context, glanceId), System.currentTimeMillis(), weights(context))
        store(context, glanceId, next)
    }
}

class ShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val next = NextTask.pressShuffle(candidates(context), currentSelection(context, glanceId), System.currentTimeMillis(), w = weights(context))
        store(context, glanceId, next)
    }
}

class EasyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val next = NextTask.pressEasy(candidates(context), currentSelection(context, glanceId), System.currentTimeMillis(), w = weights(context))
        store(context, glanceId, next)
    }
}

class NextTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTaskWidget()
}
