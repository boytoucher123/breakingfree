package com.breakingfree.app.todo.data

import android.content.Context
import com.breakingfree.app.todo.domain.Weights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The user's weighting settings, kept in SharedPreferences. */
class WeightsStore(context: Context) {
    private val prefs = context.getSharedPreferences("todo_weights", Context.MODE_PRIVATE)
    private val _weights = MutableStateFlow(load())
    val weights: StateFlow<Weights> = _weights.asStateFlow()

    fun set(w: Weights) {
        prefs.edit()
            .putFloat(K_HIGH, w.priorityHigh.toFloat())
            .putFloat(K_MED, w.priorityMedium.toFloat())
            .putFloat(K_LOW, w.priorityLow.toFloat())
            .putFloat(K_NONE, w.priorityNone.toFloat())
            .putFloat(K_OVERDUE, w.overdue.toFloat())
            .putFloat(K_SOON, w.dueSoon.toFloat())
            .putFloat(K_SOON_H, w.dueSoonHours.toFloat())
            .putFloat(K_BASE, w.shuffleBase.toFloat())
            .putFloat(K_PROGRESS, w.inProgressBoost.toFloat())
            .apply()
        _weights.value = w
    }

    private fun load(): Weights {
        val d = Weights()
        fun f(k: String, def: Double) = prefs.getFloat(k, def.toFloat()).toDouble()
        return Weights(
            priorityHigh = f(K_HIGH, d.priorityHigh),
            priorityMedium = f(K_MED, d.priorityMedium),
            priorityLow = f(K_LOW, d.priorityLow),
            priorityNone = f(K_NONE, d.priorityNone),
            overdue = f(K_OVERDUE, d.overdue),
            dueSoon = f(K_SOON, d.dueSoon),
            dueSoonHours = f(K_SOON_H, d.dueSoonHours),
            shuffleBase = f(K_BASE, d.shuffleBase),
            inProgressBoost = f(K_PROGRESS, d.inProgressBoost),
        )
    }

    private companion object {
        const val K_HIGH = "high"
        const val K_MED = "medium"
        const val K_LOW = "low"
        const val K_NONE = "none"
        const val K_OVERDUE = "overdue"
        const val K_SOON = "due_soon"
        const val K_SOON_H = "due_soon_hours"
        const val K_BASE = "shuffle_base"
        const val K_PROGRESS = "in_progress_boost"
    }
}
