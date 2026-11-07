package com.breakingfree.app.todo.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.DisplayStatus
import com.breakingfree.app.todo.domain.NextSelection
import com.breakingfree.app.todo.domain.NextTask
import com.breakingfree.app.todo.domain.Recommender
import com.breakingfree.app.todo.ui.displayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TasksFilterType(val label: String) {
    ACTIVE("ACTIVE"),
    UNFINISHED("UNFINISHED"),
    ALL("ALL"),
    COMPLETED("DONE"),
}

data class TaskRow(val task: Task, val title: String, val status: DisplayStatus)

data class NextCard(val task: Task?, val title: String, val rank: Int, val total: Int, val selection: NextSelection)

data class TasksUiState(
    val items: List<TaskRow> = emptyList(),
    val isLoading: Boolean = true,
    val filter: TasksFilterType = TasksFilterType.ACTIVE,
    val tagFilter: String? = null,
    val allTags: List<String> = emptyList(),
    val next: NextCard = NextCard(null, "", -1, 0, NextSelection()),
    val hasAnyTasks: Boolean = false,
    val openCount: Int = 0,
    val unfinishedCount: Int = 0,
)

class TasksViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = TodoGraph.get(app)
    private val repo = graph.repository

    private val filter = MutableStateFlow(TasksFilterType.ACTIVE)
    private val tagFilter = MutableStateFlow<String?>(null)
    private val selection = MutableStateFlow(NextSelection())

    val uiState: StateFlow<TasksUiState> = combine(
        repo.getTasksStream(), filter, tagFilter, selection, graph.weights.weights,
    ) { all, f, tag, sel, w ->
        val now = System.currentTimeMillis()
        val candidates = all.map { it.toCandidate() }
        val openIds = all.filter { it.isActive }.map { it.id }.toSet()
        fun status(t: Task) = DisplayStatus.of(t.status, t.isUnfinished, Recommender.blockReason(t.toCandidate(), openIds, now))
        val shown = all
            .filter {
                when (f) {
                    TasksFilterType.ACTIVE -> it.isActive && !it.isUnfinished
                    TasksFilterType.UNFINISHED -> it.isUnfinished
                    TasksFilterType.ALL -> true
                    TasksFilterType.COMPLETED -> it.isCompleted
                }
            }
            .filter { tag == null || tag in it.tags }
            .map { TaskRow(it, displayTitle(it, all), status(it)) }
            .sortedWith(
                compareBy<TaskRow> { it.task.isCompleted }
                    .thenBy { it.status == DisplayStatus.WAITING || it.status == DisplayStatus.BLOCKED }
                    .thenByDescending { it.status == DisplayStatus.IN_PROGRESS }
                    .thenByDescending { Recommender.score(it.task.toCandidate(), now, w) }
                    .thenBy { it.task.createdAt },
            )
        val resolved = NextTask.resolve(candidates, sel, now, w)
        val nextTask = resolved.task?.let { c -> all.first { it.id == c.id } }
        TasksUiState(
            items = shown,
            isLoading = false,
            filter = f,
            tagFilter = tag,
            allTags = all.flatMap { it.tags }.distinct().sorted(),
            next = NextCard(nextTask, nextTask?.let { displayTitle(it, all) }.orEmpty(), resolved.rank, resolved.total, sel),
            hasAnyTasks = all.any { !it.isSystem },
            openCount = all.count { it.isActive && !it.isUnfinished },
            unfinishedCount = all.count { it.isUnfinished },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun setFiltering(type: TasksFilterType) {
        filter.value = type
    }

    fun toggleTag(tag: String) {
        tagFilter.value = if (tagFilter.value == tag) null else tag
    }

    fun completeTask(task: Task, completed: Boolean) = viewModelScope.launch {
        repo.setStatus(task.id, if (completed) com.breakingfree.app.todo.domain.TaskStatus.DONE else com.breakingfree.app.todo.domain.TaskStatus.TODO)
    }

    fun clearCompletedTasks() = viewModelScope.launch { repo.clearCompletedTasks() }

    fun pressImportant() = viewModelScope.launch {
        selection.value = NextTask.pressImportant(candidates(), selection.value, System.currentTimeMillis(), graph.weights.weights.value)
    }

    fun pressShuffle() = viewModelScope.launch {
        selection.value = NextTask.pressShuffle(candidates(), selection.value, System.currentTimeMillis(), w = graph.weights.weights.value)
    }

    fun pressEasy() = viewModelScope.launch {
        selection.value = NextTask.pressEasy(candidates(), selection.value, System.currentTimeMillis(), w = graph.weights.weights.value)
    }

    private suspend fun candidates() = repo.getTasks().map { it.toCandidate() }
}
