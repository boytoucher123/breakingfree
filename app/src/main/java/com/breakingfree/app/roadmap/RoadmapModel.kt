package com.breakingfree.app.roadmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class Tier(val rank: Int, val label: String) {
    MINOR(0, "Minor milestone"),
    REGULAR(1, "Milestone"),
    MAJOR(2, "Major milestone"),
}

data class RoadNode(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val tier: Tier,
    val parentId: String? = null,
    val done: Boolean = false,
    /** Only on a major: the point the red→white climb leads to. Anything after it is "beyond". */
    val endGoal: Boolean = false,
)

/** A node placed on the campaign path. index 0 = the very beginning (bottom of the map). */
data class PathNode(val node: RoadNode, val index: Int, val depth: Int)

/**
 * The roadmap is stored as a flat list. Sibling order = order in that list.
 * The campaign path is a post-order walk: a milestone's sub-steps come before it,
 * so you climb through the minors, reach the milestone, and eventually the major.
 */
data class Roadmap(val nodes: List<RoadNode> = emptyList()) {
    private val byId: Map<String, RoadNode> by lazy { nodes.associateBy { it.id } }

    fun get(id: String?): RoadNode? = id?.let { byId[it] }

    /** Parent only counts if it exists and outranks the child. */
    fun effectiveParent(n: RoadNode): RoadNode? = get(n.parentId)?.takeIf { it.tier.rank > n.tier.rank }

    fun children(parentId: String?): List<RoadNode> =
        nodes.filter { (effectiveParent(it)?.id) == parentId }

    fun path(): List<PathNode> {
        val out = ArrayList<PathNode>()
        fun walk(n: RoadNode, depth: Int) {
            children(n.id).forEach { walk(it, depth + 1) }
            out.add(PathNode(n, out.size, depth))
        }
        children(null).forEach { walk(it, 0) }
        return out
    }

    /** Outline order (pre-order), as written: Major / - Milestone / -- Minor. */
    fun outline(): List<PathNode> {
        val out = ArrayList<PathNode>()
        fun walk(n: RoadNode, depth: Int) {
            out.add(PathNode(n, out.size, depth))
            children(n.id).forEach { walk(it, depth + 1) }
        }
        children(null).forEach { walk(it, 0) }
        return out
    }

    fun descendants(id: String): Set<String> {
        val result = HashSet<String>()
        fun walk(pid: String) {
            children(pid).forEach { if (result.add(it.id)) walk(it.id) }
        }
        walk(id)
        return result
    }

    fun eligibleParents(tier: Tier, selfId: String?): List<RoadNode> {
        if (tier == Tier.MAJOR) return emptyList()
        val banned = selfId?.let { descendants(it) + it } ?: emptySet()
        return outline().map { it.node }.filter { it.tier.rank > tier.rank && it.id !in banned }
    }

    /** Path index of the end goal (the marked major), or the final node if none is marked. */
    fun endGoalIndex(path: List<PathNode> = path()): Int {
        val i = path.indexOfFirst { it.node.endGoal && it.node.tier == Tier.MAJOR }
        return if (i == -1) (path.size - 1).coerceAtLeast(0) else i
    }

    /** The major a node ultimately belongs to. */
    fun majorOf(n: RoadNode): RoadNode? {
        var cur: RoadNode? = n
        while (cur != null && cur.tier != Tier.MAJOR) cur = effectiveParent(cur)
        return cur
    }

    fun currentIndex(): Int {
        val p = path()
        val i = p.indexOfFirst { !it.node.done }
        return if (i == -1) (p.size - 1).coerceAtLeast(0) else i
    }
}

/** One named roadmap with its own colours. Swiped between on the Roadmap tab. */
data class RoadmapBook(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val scheme: RoadScheme = RoadScheme.BLOOD,
    val map: Roadmap = Roadmap(),
)

/**
 * All the roadmaps, kept in roadmaps.json. The first run after the update reads the old single
 * roadmap.json into a roadmap called "General" (the old file is left in place).
 */
class RoadmapViewModel(app: Application) : AndroidViewModel(app) {
    private val dir = app.filesDir
    private val file = File(dir, "roadmaps.json")
    private val legacyFile = File(dir, "roadmap.json")
    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<RoadmapBook>> = _books

    /** The roadmap on screen. Kept here so the tab reopens on it and the app can use its colours. */
    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page
    fun setPage(i: Int) { _page.value = i }

    private fun commitBooks(books: List<RoadmapBook>) {
        _books.value = books
        save(books)
    }

    private fun editBook(bookId: String, f: (RoadmapBook) -> RoadmapBook) =
        commitBooks(_books.value.map { if (it.id == bookId) f(it) else it })

    private fun editNodes(bookId: String, f: (Roadmap) -> List<RoadNode>) =
        editBook(bookId) { it.copy(map = Roadmap(normalize(f(it.map)))) }

    // ---- roadmaps ----

    /** @return the new roadmap's position. */
    fun addBook(name: String, scheme: RoadScheme): Int {
        commitBooks(_books.value + RoadmapBook(name = name.trim().ifEmpty { "Untitled" }, scheme = scheme))
        return _books.value.size - 1
    }

    fun renameBook(bookId: String, name: String) = editBook(bookId) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun setScheme(bookId: String, scheme: RoadScheme) = editBook(bookId) { it.copy(scheme = scheme) }

    /** The last roadmap can't be deleted. */
    fun deleteBook(bookId: String) {
        if (_books.value.size > 1) commitBooks(_books.value.filter { it.id != bookId })
    }

    // ---- milestones in one roadmap ----

    fun add(bookId: String, title: String, notes: String, tier: Tier, parentId: String?, endGoal: Boolean = false) = editNodes(bookId) { m ->
        var nodes = m.nodes
        if (endGoal) nodes = nodes.map { it.copy(endGoal = false) }
        nodes + RoadNode(title = title.trim(), notes = notes.trim(), tier = tier,
            parentId = if (tier == Tier.MAJOR) null else parentId, endGoal = endGoal && tier == Tier.MAJOR)
    }

    fun update(bookId: String, node: RoadNode) = editNodes(bookId) { m ->
        val old = m.get(node.id) ?: return@editNodes m.nodes
        val updated = if (node.tier == Tier.MAJOR) node.copy(parentId = null) else node.copy(endGoal = false)
        var nodes = m.nodes.map {
            when {
                it.id == node.id -> updated
                updated.endGoal -> it.copy(endGoal = false)
                else -> it
            }
        }
        // Children that no longer fit under this node climb to its parent (still inside the same major).
        if (old.tier != updated.tier) {
            nodes = nodes.map { c ->
                if (c.parentId == node.id && c.tier.rank >= updated.tier.rank) c.copy(parentId = updated.parentId ?: m.majorOf(old)?.id)
                else c
            }
        }
        if (old.parentId != updated.parentId) {
            // Re-parented: move to the end of its new sibling group.
            nodes = nodes.filter { it.id != node.id } + updated
        }
        nodes
    }

    fun toggleDone(bookId: String, id: String) = editNodes(bookId) { m ->
        m.nodes.map { if (it.id == id) it.copy(done = !it.done) else it }
    }

    /** Deleting a major takes everything inside it. Deleting a milestone hands its steps to the parent. */
    fun delete(bookId: String, id: String) = editNodes(bookId) { m ->
        val victim = m.get(id) ?: return@editNodes m.nodes
        if (victim.tier == Tier.MAJOR) {
            val gone = m.descendants(id) + id
            m.nodes.filter { it.id !in gone }
        } else {
            val newParent = m.effectiveParent(victim)?.id
            m.nodes.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = newParent) else it }
        }
    }

    /** direction +1 = later (further up the map), -1 = earlier. Swaps with the adjacent sibling. */
    fun move(bookId: String, id: String, direction: Int) = editNodes(bookId) { m ->
        val node = m.get(id) ?: return@editNodes m.nodes
        val siblings = m.children(m.effectiveParent(node)?.id)
        val si = siblings.indexOfFirst { it.id == id }
        val other = siblings.getOrNull(si + direction) ?: return@editNodes m.nodes
        val list = m.nodes.toMutableList()
        val a = list.indexOfFirst { it.id == id }
        val b = list.indexOfFirst { it.id == other.id }
        list[a] = other; list[b] = node
        list
    }

    fun clear(bookId: String) = editNodes(bookId) { emptyList() }

    fun restoreDefaults(bookId: String) = editNodes(bookId) { defaultRoadmap() }

    // ---- storage ----

    private fun load(): List<RoadmapBook> {
        if (file.exists()) {
            runCatching {
                val arr = JSONObject(file.readText()).getJSONArray("roadmaps")
                val books = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RoadmapBook(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        scheme = o.optJSONObject("scheme")?.let(::readScheme) ?: RoadScheme.BLOOD,
                        map = Roadmap(normalize(readNodes(o.getJSONArray("nodes")))),
                    )
                }
                if (books.isNotEmpty()) return books
            }
        }
        val nodes = if (legacyFile.exists()) {
            runCatching { normalize(readNodes(JSONArray(legacyFile.readText()))) }.getOrElse { emptyList() }
        } else {
            defaultRoadmap()
        }
        return listOf(RoadmapBook(name = "General", map = Roadmap(nodes))).also(::save)
    }

    private fun readNodes(arr: JSONArray): List<RoadNode> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        RoadNode(
            id = o.getString("id"),
            title = o.getString("title"),
            notes = o.optString("notes"),
            tier = runCatching { Tier.valueOf(o.getString("tier")) }.getOrDefault(Tier.REGULAR),
            parentId = if (o.isNull("parent")) null else o.optString("parent").ifBlank { null },
            done = o.optBoolean("done"),
            endGoal = o.optBoolean("endGoal"),
        )
    }

    private fun writeNodes(nodes: List<RoadNode>) = JSONArray().apply {
        nodes.forEach { n ->
            put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("notes", n.notes); put("tier", n.tier.name)
                put("parent", n.parentId ?: JSONObject.NULL); put("done", n.done)
                put("endGoal", n.endGoal)
            })
        }
    }

    private fun readScheme(o: JSONObject): RoadScheme {
        val d = RoadScheme.BLOOD
        return RoadScheme(
            name = o.optString("name", d.name),
            origin = o.optInt("origin", d.origin),
            midway = o.optInt("midway", d.midway),
            goal = o.optInt("goal", d.goal),
            beyondStart = o.optInt("beyondStart", d.beyondStart),
            beyondMid = o.optInt("beyondMid", d.beyondMid),
            abyss = o.optInt("abyss", d.abyss),
        )
    }

    private fun writeScheme(s: RoadScheme) = JSONObject().apply {
        put("name", s.name); put("origin", s.origin); put("midway", s.midway); put("goal", s.goal)
        put("beyondStart", s.beyondStart); put("beyondMid", s.beyondMid); put("abyss", s.abyss)
    }

    private fun save(books: List<RoadmapBook>) {
        val arr = JSONArray()
        books.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id); put("name", b.name)
                put("scheme", writeScheme(b.scheme))
                put("nodes", writeNodes(b.map.nodes))
            })
        }
        runCatching {
            val tmp = File(dir, "roadmaps.json.tmp")
            tmp.writeText(JSONObject().put("roadmaps", arr).toString())
            tmp.renameTo(file)
        }
    }
}

/**
 * Enforces the rules: majors stand alone; milestones live in a major; minors live in a milestone or a major.
 * Anything stranded is gathered into the nearest major before it (or after it), or a new "Unsorted" major.
 * At most one end goal.
 */
internal fun normalize(input: List<RoadNode>): List<RoadNode> {
    var nodes = input
    val needsHome = { list: List<RoadNode> ->
        val tmp = Roadmap(list)
        list.any { it.tier != Tier.MAJOR && tmp.effectiveParent(it) == null }
    }
    if (needsHome(nodes) && nodes.none { it.tier == Tier.MAJOR }) {
        nodes = listOf(RoadNode(title = "Unsorted", tier = Tier.MAJOR)) + nodes
    }
    if (needsHome(nodes)) {
        val tmp = Roadmap(nodes)
        nodes = nodes.mapIndexed { i, n ->
            if (n.tier == Tier.MAJOR || tmp.effectiveParent(n) != null) n
            else {
                val home = nodes.subList(0, i).lastOrNull { it.tier == Tier.MAJOR }
                    ?: nodes.subList(i, nodes.size).first { it.tier == Tier.MAJOR }
                n.copy(parentId = home.id)
            }
        }
    }
    nodes = nodes.map { if (it.tier == Tier.MAJOR && it.parentId != null) it.copy(parentId = null) else it }
    val goals = nodes.filter { it.endGoal }
    if (goals.size > 1 || goals.any { it.tier != Tier.MAJOR }) {
        val keep = goals.firstOrNull { it.tier == Tier.MAJOR }?.id
        nodes = nodes.map { it.copy(endGoal = it.id == keep) }
    }
    return nodes
}

private fun defaultRoadmap(): List<RoadNode> {
    val list = ArrayList<RoadNode>()
    fun major(title: String, notes: String = "", endGoal: Boolean = false) =
        RoadNode(title = title, tier = Tier.MAJOR, notes = notes, endGoal = endGoal).also { list += it }
    fun goal(parent: RoadNode, title: String, notes: String = "") =
        RoadNode(title = title, tier = Tier.REGULAR, parentId = parent.id, notes = notes).also { list += it }

    // Starting point: no debt, no assets. Pure income for now.
    // ---- the climb (red → white) ----
    val e1 = major("Clear the Ground", "Epoch I. No debt, no assets. Build the income from nothing.")
    goal(e1, "$1k/month")
    goal(e1, "$2k/month")
    goal(e1, "$3k/month")
    goal(e1, "$5k/month")
    goal(e1, "$7.5k/month")

    val e2 = major("Break Free", "The end goal. Nobody owns your time.", endGoal = true)
    goal(e2, "$10k/month")

    // ---- beyond (purple) ----
    val b1 = major("Dominion", "Beyond. Past freedom, into power.")
    goal(b1, "$20k/month")
    goal(b1, "$50k/month")

    val b2 = major("The Black Throne", "Beyond. Few ever see this far.")
    goal(b2, "$100k/month")
    goal(b2, "$1M/month")
    return list
}
