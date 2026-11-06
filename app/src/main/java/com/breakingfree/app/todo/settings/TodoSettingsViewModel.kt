package com.breakingfree.app.todo.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakingfree.app.todo.TodoGraph
import com.breakingfree.app.todo.domain.Weights
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A tag and how many tasks use it. */
data class TagUse(val tag: String, val count: Int)

class TodoSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = TodoGraph.get(app)

    val weights: StateFlow<Weights> = graph.weights.weights

    val tags: StateFlow<List<TagUse>> = graph.repository.getTasksStream()
        .map { tasks ->
            tasks.flatMap { it.tags }.groupingBy { it }.eachCount()
                .map { (t, n) -> TagUse(t, n) }.sortedBy { it.tag }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setWeights(w: Weights) = graph.weights.set(w)

    /** After a slider is released: redraw the widget with the new weights (not on every drag step). */
    fun weightsSettled() = graph.refreshWidget()

    fun resetWeights() {
        setWeights(Weights())
        weightsSettled()
    }

    fun deleteTag(tag: String) = viewModelScope.launch { graph.repository.deleteTag(tag) }
}
