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

enum class QType { TEXT, TABLE }

/** A question. TABLE questions pair each answer in the left column ([prompt]) with one in the right ([right]). */
data class Question(val id: String, val type: QType, val prompt: String, val right: String = "")

data class Answer(
    val question: String, val text: String = "", val rows: List<Pair<String, String>> = emptyList(),
    val left: String = "", val right: String = "",
)

data class Reflection(val id: String, val timestamp: Long, val answers: List<Answer>)

data class LatticeState(
    val questions: List<Question> = emptyList(),
    val singles: Map<String, String> = emptyMap(),
    val tables: Map<String, List<Pair<String, String>>> = emptyMap(),
    val entries: List<Reflection> = emptyList(),
) {
    fun isAnswered(id: String): Boolean =
        singles[id]?.isNotBlank() == true ||
            tables[id].orEmpty().any { it.first.isNotBlank() || it.second.isNotBlank() }

    val answeredCount: Int get() = questions.count { isAnswered(it.id) }
    val hasDraft: Boolean get() = answeredCount > 0
}

/**
 * Everything lives in the app's private files directory, which survives app upgrades:
 *  - lattice_questions.json : the question set (seeded from assets/lattice_questions.json)
 *  - lattice_draft.json     : answers in progress
 *  - lattice_entries.json   : kept reflections
 */
class LatticeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val questionsFile = File(app.filesDir, "lattice_questions.json")
    private val entriesFile = File(app.filesDir, "lattice_entries.json")
    private val draftFile = File(app.filesDir, "lattice_draft.json")

    private val _state = MutableStateFlow(
        withDraft(LatticeState(questions = loadQuestions(), entries = loadEntries()))
    )
    val state: StateFlow<LatticeState> = _state

    /** Question ids in display order; reshuffled each time the Lattice opens. */
    private val _order = MutableStateFlow(_state.value.questions.map { it.id }.shuffled())
    val order: StateFlow<List<String>> = _order

    fun shuffle() { _order.value = _state.value.questions.map { it.id }.shuffled() }

    // ---- answers ----

    fun setSingle(id: String, text: String) {
        _state.update { it.copy(singles = it.singles + (id to text)) }
        saveDraft()
    }

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
        saveDraft()
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
        val answers = s.questions.mapNotNull { q ->
            when (q.type) {
                QType.TEXT -> s.singles[q.id]?.takeIf { it.isNotBlank() }?.let { Answer(q.prompt, it.trim()) }
                QType.TABLE -> s.tables[q.id].orEmpty()
                    .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                    .takeIf { it.isNotEmpty() }
                    ?.let { rows ->
                        Answer(q.prompt, left = q.prompt, right = q.right, rows = rows.map { it.first.trim() to it.second.trim() })
                    }
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

    // ---- question set ----

    fun addQuestion(type: QType, prompt: String, right: String) {
        val q = Question(UUID.randomUUID().toString(), type, prompt.trim(), if (type == QType.TABLE) right.trim() else "")
        _state.update { it.copy(questions = it.questions + q) }
        _order.update { it + q.id }
        saveQuestions()
    }

    fun deleteQuestion(id: String) {
        _state.update { it.copy(questions = it.questions.filter { q -> q.id != id }, singles = it.singles - id, tables = it.tables - id) }
        _order.update { it - id }
        saveQuestions()
        saveDraft()
    }

    fun restoreDefaultQuestions() {
        val defaults = parseQuestions(readAsset())
        _state.update { it.copy(questions = defaults) }
        shuffle()
        saveQuestions()
    }

    // ---- persistence ----

    private fun readAsset(): String = app.assets.open("lattice_questions.json").bufferedReader().use { it.readText() }

    private fun parseQuestions(json: String): List<Question> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val prompt = o.optString("prompt").trim()
            if (prompt.isEmpty()) return@mapNotNull null
            val type = if (o.optString("type").equals("table", ignoreCase = true)) QType.TABLE else QType.TEXT
            Question(o.optString("id").ifBlank { UUID.randomUUID().toString() }, type, prompt, o.optString("right"))
        }
    }

    private fun loadQuestions(): List<Question> {
        val fromFile = runCatching { parseQuestions(questionsFile.readText()) }.getOrNull()
        if (fromFile != null) return fromFile
        return runCatching { parseQuestions(readAsset()) }.getOrDefault(emptyList()).also { writeQuestions(it) }
    }

    private fun saveQuestions() = writeQuestions(_state.value.questions)

    private fun writeQuestions(list: List<Question>) {
        val arr = JSONArray()
        list.forEach { q ->
            arr.put(JSONObject().apply {
                put("id", q.id); put("type", q.type.name.lowercase()); put("prompt", q.prompt)
                if (q.type == QType.TABLE) put("right", q.right)
            })
        }
        atomicWrite(questionsFile, arr.toString(2))
    }

    private fun atomicWrite(file: File, text: String) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
        }
    }

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
        o.put("singles", JSONObject().apply { s.singles.forEach { (k, v) -> put(k, v) } })
        o.put("tables", JSONObject().apply { s.tables.forEach { (k, v) -> put(k, rowsToJson(v)) } })
        atomicWrite(draftFile, o.toString())
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
        atomicWrite(entriesFile, arr.toString())
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
