package com.breakingfree.app.todo

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.util.UUID

/** Screens inside the TODO tab. Each route carries a key so every visit gets fresh screen state. */
sealed interface TodoRoute {
    val key: String

    data object Tasks : TodoRoute { override val key = "tasks" }
    data class Detail(val taskId: String, override val key: String = "detail-$taskId") : TodoRoute
    data class Edit(val taskId: String?, override val key: String = "edit-${UUID.randomUUID()}") : TodoRoute
    data object Settings : TodoRoute { override val key = "settings" }
}

/**
 * The TODO tab's own little back stack. It lives above the tab bar so switching tabs and back
 * keeps your place (a NavHost inside the cross-fading tab content would be torn down each time).
 */
class TodoNav(initial: List<TodoRoute> = listOf(TodoRoute.Tasks)) {
    val stack = mutableStateListOf<TodoRoute>().apply { addAll(initial) }

    val current: TodoRoute get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: TodoRoute) {
        if (stack.lastOrNull()?.key != route.key) stack.add(route)
    }

    fun pop() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    fun resetTo(route: TodoRoute) {
        stack.clear()
        stack.add(TodoRoute.Tasks)
        if (route != TodoRoute.Tasks) stack.add(route)
    }

    /** After a delete: back to the list. */
    fun backToTasks() {
        while (canGoBack) pop()
    }

    companion object {
        val saver: Saver<TodoNav, Any> = listSaver(
            save = { nav ->
                nav.stack.map {
                    when (it) {
                        TodoRoute.Tasks -> "t"
                        TodoRoute.Settings -> "s"
                        is TodoRoute.Detail -> "d:${it.taskId}"
                        is TodoRoute.Edit -> "e:${it.taskId.orEmpty()}:${it.key}"
                    }
                }
            },
            restore = { saved ->
                TodoNav(
                    saved.map { s ->
                        when {
                            s == "s" -> TodoRoute.Settings
                            s.startsWith("d:") -> TodoRoute.Detail(s.removePrefix("d:"))
                            s.startsWith("e:") -> {
                                val parts = s.split(":", limit = 3)
                                TodoRoute.Edit(parts[1].ifEmpty { null }, parts[2])
                            }
                            else -> TodoRoute.Tasks
                        }
                    }.ifEmpty { listOf(TodoRoute.Tasks) },
                )
            },
        )
    }
}
