package com.breakingfree.app.lattice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

sealed class Prompt(val id: String) {
    class Single(id: String, val question: String) : Prompt(id)
    /** A two-column table: each row pairs an answer on the left with its answer on the right. */
    class Table(id: String, val left: String, val right: String) : Prompt(id)
}

val PROMPTS: List<Prompt> = listOf(
    Prompt.Single("today", "How was today?"),
    Prompt.Single("holds", "What holds me back?"),
    Prompt.Single("want", "What do I want?"),
    Prompt.Single("hurting", "Am I hurting anyone?"),
    Prompt.Single("misery", "Am I adding misery to the world?"),
    Prompt.Table("want_why", "What do I want?", "Why don't I have it?"),
    Prompt.Single("decade", "If I die in a decade's time, what's the one thing I must complete before then?"),
    Prompt.Table("think_true", "What did I think today?", "Is it true?"),
    Prompt.Single("envy", "The people who you envy, what are they doing that you aren't?"),
    Prompt.Table("self", "What do I like about myself?", "What don't I like about myself?"),
    Prompt.Table("others", "What do I like about others?", "What don't I like about others?"),
)

data class Answer(val question: String, val text: String = "", val rows: List<Pair<String, String>> = emptyList(),
                  val left: String = "", val right: String = "")

data class Reflection(val id: String, val timestamp: Long, val answers: List<Answer>)

data class LatticeState(
    val singles: Map<String, String> = emptyMap(),
    val tables: Map<String, List<Pair<String, String>>> = emptyMap(),
    val entries: List<Reflection> = emptyList(),
) {
    val hasDraft: Boolean
        get() = singles.values.any { it.isNotBlank() } ||
            tables.values.any { rows -> rows.any { it.first.isNotBlank() || it.second.isNotBlank() } }
}

class LatticeViewModel(app: Application) : AndroidViewModel(app) {
    private val entriesFile = File(app.filesDir, "lattice_entries.json")
    private val draftFile = File(app.filesDir, "lattice_draft.json")
    private val _state = MutableStateFlow(LatticeState(entries = loadEntries()).let(::withDraft))
    val state: StateFlow<LatticeState> = _state

    /** Order of questions, reshuffled each time the Lattice is opened (or on request). */
    private val _order = MutableStateFlow(PROMPTS.shuffled())
    val order: StateFlow<List<Prompt>> = _order

    fun shuffle() { _order.value = PROMPTS.shuffled() }

    fun setSingle(id: String, text: String) {
        _state.update { it.copy(singles = it.singles + (id to text)) }
        saveDraft()
    }

    fun rows(id: String): List<Pair<String, String>> = _state.value.tables[id].orEmpty().ifEmpty { listOf("" to "") }

    fun setCell(id: String, row: Int, left: Boolean, text: String) {
        _state.update { s ->
            val rows = s.tables[id].orEmpty().ifEmpty { listOf("" to "") }.toMutableList()
            while (rows.size <= row) rows += "" to ""
            rows[row] = if (left) rows[row].copy(first = text) else rows[row].copy(second = text)
            s.copy(tables = s.tables + (id to rows))
        }
        saveDraft()
    }

    fun addRow(id: String) {
        _state.update { s ->
            val rows = s.tables[id].orEmpty().ifEmpty { listOf("" to "") }
            s.copy(tables = s.tables + (id to rows + ("" to "")))
        }
    }

    fun removeRow(id: String, row: Int) {
        _state.update { s ->
            val rows = s.tables[id].orEmpty().toMutableList()
            if (row in rows.indices) rows.removeAt(row)
            s.copy(tables = s.tables + (id to rows))
        }
        saveDraft()
    }

    /** Commits the current answers as a dated reflection and clears the boxes. */
    fun keep(): Boolean {
        val s = _state.value
        if (!s.hasDraft) return false
        val answers = PROMPTS.mapNotNull { pr ->
            when (pr) {
                is Prompt.Single -> s.singles[pr.id]?.takeIf { it.isNotBlank() }?.let { Answer(pr.question, it.trim()) }
                is Prompt.Table -> s.tables[pr.id].orEmpty()
                    .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { rows -> Answer(pr.left, left = pr.left, right = pr.right, rows = rows.map { it.first.trim() to it.second.trim() }) }
            }
        }
        val entry = Reflection(UUID.randomUUID().toString(), System.currentTimeMillis(), answers)
        _state.update { it.copy(singles = emptyMap(), tables = emptyMap(), entries = listOf(entry) + it.entries) }
        saveEntries()
        saveDraft()
        return true
    }

    fun deleteEntry(id: String) {
        _state.update { it.copy(entries = it.entries.filter { e -> e.id != id }) }
        saveEntries()
    }

    // ---- persistence ----

    private fun rowsToJson(rows: List<Pair<String, String>>) = JSONArray().apply {
        rows.forEach { put(JSONArray().put(it.first).put(it.second)) }
    }

    private fun rowsFromJson(a: JSONArray?): List<Pair<String, String>> =
        if (a == null) emptyList() else (0 until a.length()).map { i ->
            val r = a.getJSONArray(i); r.optString(0) to r.optString(1)
        }

    private fun saveDraft() {
        val s = _state.value
        val o = JSONObject()
        o.put("singles", JSONObject(s.singles as Map<*, *>))
        o.put("tables", JSONObject().apply { s.tables.forEach { (k, v) -> put(k, rowsToJson(v)) } })
        runCatching { draftFile.writeText(o.toString()) }
    }

    private fun withDraft(s: LatticeState): LatticeState = runCatching {
        val o = JSONObject(draftFile.readText())
        val singles = o.optJSONObject("singles")?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } }.orEmpty()
        val tables = o.optJSONObject("tables")?.let { j ->
            j.keys().asSequence().associateWith { rowsFromJson(j.optJSONArray(it)) }
        }.orEmpty()
        s.copy(singles = singles, tables = tables)
    }.getOrDefault(s)

    private fun saveEntries() {
        val arr = JSONArray()
        _state.value.entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id); put("ts", e.timestamp)
                put("answers", JSONArray().apply {
                    e.answers.forEach { a ->
                        put(JSONObject().apply {
                            put("q", a.question); put("text", a.text); put("left", a.left); put("right", a.right)
                            put("rows", rowsToJson(a.rows))
                        })
                    }
                })
            })
        }
        runCatching {
            val tmp = File(entriesFile.parentFile, "lattice_entries.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(entriesFile)
        }
    }

    private fun loadEntries(): List<Reflection> = runCatching {
        val arr = JSONArray(entriesFile.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ans = o.getJSONArray("answers")
            Reflection(o.getString("id"), o.getLong("ts"), (0 until ans.length()).map { j ->
                val a = ans.getJSONObject(j)
                Answer(a.getString("q"), a.optString("text"), rowsFromJson(a.optJSONArray("rows")),
                    a.optString("left"), a.optString("right"))
            })
        }
    }.getOrDefault(emptyList())
}
