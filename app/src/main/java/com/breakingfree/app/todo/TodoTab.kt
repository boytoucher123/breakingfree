package com.breakingfree.app.todo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.breakingfree.app.todo.addedittask.AddEditTaskScreen
import com.breakingfree.app.todo.settings.TodoSettingsScreen
import com.breakingfree.app.todo.taskdetail.TaskDetailScreen
import com.breakingfree.app.todo.tasks.TasksScreen

/** The TODO tab: task list, detail, add/edit and settings screens on a small back stack. */
@Composable
fun TodoTab(nav: TodoNav) {
    BackHandler(enabled = nav.canGoBack) { nav.pop() }
    when (val r = nav.current) {
        TodoRoute.Tasks -> TasksScreen(
            onAddTask = { nav.push(TodoRoute.Edit(null)) },
            onTaskClick = { nav.push(TodoRoute.Detail(it)) },
            onSettings = { nav.push(TodoRoute.Settings) },
        )
        is TodoRoute.Detail -> TaskDetailScreen(
            taskId = r.taskId,
            vmKey = r.key,
            onEditTask = { nav.push(TodoRoute.Edit(it)) },
            onOpenTask = { nav.push(TodoRoute.Detail(it)) },
            onBack = { nav.pop() },
            onDeleted = { nav.backToTasks() },
        )
        is TodoRoute.Edit -> AddEditTaskScreen(
            taskId = r.taskId,
            vmKey = r.key,
            onTaskSaved = { nav.pop() },
            onBack = { nav.pop() },
        )
        TodoRoute.Settings -> TodoSettingsScreen(onBack = { nav.pop() })
    }
}
