package com.breakingfree.app.todo.addedittask

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.data.Priority
import com.breakingfree.app.todo.data.Task
import com.breakingfree.app.todo.data.TaskDraft
import com.breakingfree.app.todo.data.toDraft
import com.breakingfree.app.todo.domain.Reminder
import com.breakingfree.app.todo.domain.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditTaskUiState(
    val draft: TaskDraft = TaskDraft(),
    /** What was loaded, to tell whether anything changed. */
    val original: TaskDraft = TaskDraft(),
    /** Tags used on any task, offered as one-tap suggestions. */
    val knownTags: List<String> = emptyList(),
    /** Open tasks this one could be made to wait on (excludes ones that would create a loop). */
    val prerequisiteOptions: List<Task> = emptyList(),
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val isTaskSaved: Boolean = false,
) {
    val isDirty get() = draft != original
    val willBeUnfinished get() = draft.tags.isEmpty() || draft.priority == Priority.NONE
}

class AddEditTaskViewModel(app: Application, private val taskId: String?) : AndroidViewModel(app) {
    private val repo = TodoGraph.get(app).repository

    private val _uiState = MutableStateFlow(AddEditTaskUiState(isNew = taskId == null, isLoading = true))
    val uiState: StateFlow<AddEditTaskUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val all = repo.getTasks()
            val task = taskId?.let { id -> all.firstOrNull { it.id == id } }
            val dependents = taskId?.let { dependentsOf(it, all) }.orEmpty()
            val draft = task?.toDraft() ?: TaskDraft()
            _uiState.update {
                it.copy(
                    draft = draft,
                    original = draft,
                    knownTags = all.flatMap { t -> t.tags }.distinct().sorted(),
                    prerequisiteOptions = all.filter { t ->
                        !t.isSystem && t.id != taskId && t.id !in dependents &&
                            (t.isActive || t.id in draft.blockedBy)
                    }.sortedBy { t -> t.createdAt },
                    isLoading = false,
                )
            }
        }
    }

    private fun edit(f: (TaskDraft) -> TaskDraft) = _uiState.update { it.copy(draft = f(it.draft)) }

    fun updateTitle(v: String) = edit { it.copy(title = v) }
    fun updateDescription(v: String) = edit { it.copy(description = v) }
    fun updatePriority(v: Priority) = edit { it.copy(priority = v) }
    fun updateDueAt(v: Long?) = edit { it.copy(dueAt = v) }
    fun updateStartAt(v: Long?) = edit { it.copy(startAt = v) }
    fun updateDays(v: Int) = edit { it.copy(days = v) }
    fun updateReminder(v: Reminder) = edit { it.copy(reminder = v) }

    fun addTag(raw: String) {
        val tag = Tags.normalize(raw)
        if (tag.isEmpty()) return
        edit { if (tag in it.tags) it else it.copy(tags = it.tags + tag) }
    }

    fun toggleTag(tag: String) = edit { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) }

    fun togglePrerequisite(id: String) = edit {
        it.copy(blockedBy = if (id in it.blockedBy) it.blockedBy - id else it.blockedBy + id)
    }

    /** @return false if the task can't be saved yet (no title). */
    fun saveTask(): Boolean {
        val d = uiState.value.draft
        if (d.title.isBlank()) {
            _uiState.update { it.copy(userMessage = "A task needs a title") }
            return false
        }
        val clean = d.copy(title = d.title.trim(), description = d.description.trim())
        viewModelScope.launch {
            if (taskId == null) repo.createTask(clean) else repo.updateTask(taskId, clean)
            _uiState.update { it.copy(isTaskSaved = true) }
        }
        return true
    }

    /** Every task that (directly or indirectly) waits on [id]; picking one as a prerequisite would loop. */
    private fun dependentsOf(id: String, all: List<Task>): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            all.filter { cur in it.blockedBy && it.id !in result }.forEach {
                result += it.id
                queue += it.id
            }
        }
        return result
    }
}
