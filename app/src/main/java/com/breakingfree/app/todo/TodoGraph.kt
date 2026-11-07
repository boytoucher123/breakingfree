package com.breakingfree.app.todo

import android.content.Context
import androidx.room.Room
import com.breakingfree.app.todo.data.TaskRepository
import com.breakingfree.app.todo.data.WeightsStore
import com.breakingfree.app.todo.data.local.TodoDatabase
import com.breakingfree.app.todo.reminders.Reminders
import com.breakingfree.app.todo.widget.NextTaskWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// The file name, preference names and the `todo` package predate the rename to "Tasks". They stay
// as they are so existing tasks, settings and placed widgets survive upgrades.

/**
 * The Tasks feature's singletons (database, repository, settings), created on first use.
 * Plays the part Hilt played in the old standalone TODO app.
 */
class TodoGraph private constructor(context: Context) {
    private val app = context.applicationContext

    /** For work that should outlive a screen: widget redraws, reminder scheduling. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = Room.databaseBuilder(app, TodoDatabase::class.java, "todo.db")
        .addMigrations(TodoDatabase.MIGRATION_1_2, TodoDatabase.MIGRATION_2_3)
        .build()

    val weights = WeightsStore(app)

    val repository = TaskRepository(db.taskDao()) { afterChange() }

    private suspend fun afterChange() {
        scope.launch { runCatching { NextTaskWidget.refreshAll(app) } }
        runCatching { Reminders.rescheduleAll(app, repository.getTasks()) }
    }

    /** Re-draw the widget, e.g. after the weightings change. */
    fun refreshWidget() {
        scope.launch { runCatching { NextTaskWidget.refreshAll(app) } }
    }

    companion object {
        @Volatile private var instance: TodoGraph? = null

        fun get(context: Context): TodoGraph =
            instance ?: synchronized(this) {
                instance ?: TodoGraph(context).also { g ->
                    instance = g
                    g.scope.launch { g.repository.reconcile() }
                }
            }
    }
}
