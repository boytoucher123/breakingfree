package com.breakingfree.app.todo.taskdetail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.domain.Block
import com.breakingfree.app.todo.domain.DisplayStatus
import com.breakingfree.app.todo.domain.Recommender
import com.breakingfree.app.todo.domain.TaskStatus
import com.breakingfree.app.todo.ui.displayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskDetailUiState(
    val task: Task? = null,
    val title: String = "",
    val isLoading: Boolean = true,
    val status: DisplayStatus = DisplayStatus.TODO,
    val block: Block? = null,
    /** Prerequisites of this task. */
    val blockedBy: List<Task> = emptyList(),
    /** Tasks waiting on this one. */
    val blocking: List<Task> = emptyList(),
    /** For the system task: the tasks it's asking you to finish. */
    val unfinished: List<Task> = emptyList(),
    val isDeleted: Boolean = false,
)

class TaskDetailViewModel(app: Application, val taskId: String) : AndroidViewModel(app) {
    private val repo = TodoGraph.get(app).repository
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<TaskDetailUiState> = combine(repo.getTasksStream(), deleted) { all, isDeleted ->
        val task = all.firstOrNull { it.id == taskId }
        val now = System.currentTimeMillis()
        val openIds = all.filter { it.isActive }.map { it.id }.toSet()
        val block = task?.let { Recommender.blockReason(it.toCandidate(), openIds, now) }
        TaskDetailUiState(
            task = task,
            title = task?.let { displayTitle(it, all) }.orEmpty(),
            isLoading = false,
            status = task?.let { DisplayStatus.of(it.status, it.isUnfinished, block) } ?: DisplayStatus.TODO,
            block = block,
            blockedBy = task?.let { t -> all.filter { it.id in t.blockedBy } }.orEmpty(),
            blocking = all.filter { taskId in it.blockedBy },
            unfinished = if (task?.isSystem == true) all.filter { it.isUnfinished }.sortedBy { it.createdAt } else emptyList(),
            isDeleted = isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskDetailUiState())

    fun deleteTask() = viewModelScope.launch {
        repo.deleteTask(taskId)
        deleted.value = true
    }

    fun setStatus(status: TaskStatus) = viewModelScope.launch { repo.setStatus(taskId, status) }
}
