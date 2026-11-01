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

class RoadmapViewModel(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "roadmap.json")
    private val _map = MutableStateFlow(load())
    val map: StateFlow<Roadmap> = _map

    private fun commit(nodes: List<RoadNode>) {
        val fixed = normalize(nodes)
        _map.value = Roadmap(fixed)
        save(fixed)
    }

    fun add(title: String, notes: String, tier: Tier, parentId: String?, endGoal: Boolean = false) {
        var nodes = _map.value.nodes
        if (endGoal) nodes = nodes.map { it.copy(endGoal = false) }
        commit(nodes + RoadNode(title = title.trim(), notes = notes.trim(), tier = tier,
            parentId = if (tier == Tier.MAJOR) null else parentId, endGoal = endGoal && tier == Tier.MAJOR))
    }

    fun update(node: RoadNode) {
        val m = _map.value
        val old = m.get(node.id) ?: return
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
        commit(nodes)
    }

    fun toggleDone(id: String) {
        commit(_map.value.nodes.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    /** Deleting a major takes everything inside it. Deleting a milestone hands its steps to the parent. */
    fun delete(id: String) {
        val m = _map.value
        val victim = m.get(id) ?: return
        if (victim.tier == Tier.MAJOR) {
            val gone = m.descendants(id) + id
            commit(m.nodes.filter { it.id !in gone })
        } else {
            val newParent = m.effectiveParent(victim)?.id
            commit(m.nodes.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = newParent) else it })
        }
    }

    /** direction +1 = later (further up the map), -1 = earlier. Swaps with the adjacent sibling. */
    fun move(id: String, direction: Int) {
        val m = _map.value
        val node = m.get(id) ?: return
        val siblings = m.children(m.effectiveParent(node)?.id)
        val si = siblings.indexOfFirst { it.id == id }
        val other = siblings.getOrNull(si + direction) ?: return
        val list = m.nodes.toMutableList()
        val a = list.indexOfFirst { it.id == id }
        val b = list.indexOfFirst { it.id == other.id }
        list[a] = other; list[b] = node
        commit(list)
    }

    fun clear() = commit(emptyList())

    fun restoreDefaults() = commit(defaultRoadmap())

    private fun load(): Roadmap {
        if (!file.exists()) return Roadmap(defaultRoadmap()).also { save(it.nodes) }
        return runCatching {
            val arr = JSONArray(file.readText())
            Roadmap(normalize((0 until arr.length()).map { i ->
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
            }))
        }.getOrElse { Roadmap() }
    }

    private fun save(nodes: List<RoadNode>) {
        val arr = JSONArray()
        nodes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("notes", n.notes); put("tier", n.tier.name)
                put("parent", n.parentId ?: JSONObject.NULL); put("done", n.done)
                put("endGoal", n.endGoal)
            })
        }
        runCatching {
            val tmp = File(file.parentFile, "roadmap.json.tmp")
            tmp.writeText(arr.toString())
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
    fun task(parent: RoadNode, title: String) =
        RoadNode(title = title, tier = Tier.MINOR, parentId = parent.id).also { list += it }

    // ---- the climb (red → white) ----
    val e1 = major("Clear the Ground", "Epoch I. Stop the bleeding before you build.")
    val debt = goal(e1, "Zero consumer debt")
    task(debt, "List every debt, balance and rate")
    task(debt, "Cancel every subscription I don't use")
    task(debt, "Pay off the highest-rate card")
    val buf = goal(e1, "$5k emergency fund")
    task(buf, "Open a separate high-interest account")
    task(buf, "Automate a weekly transfer")
    task(e1, "Track every dollar for 30 days")

    val e2 = major("First Blood", "Epoch II. Money that didn't come from a boss.")
    val k1 = goal(e2, "$1k/month side income")
    task(k1, "Pick one skill to sell")
    task(k1, "Build a portfolio of 3 pieces")
    task(k1, "Land the first paying client")
    val k2 = goal(e2, "$2k/month side income")
    task(k2, "Raise rates 20%")
    task(k2, "Get 3 repeat clients")
    task(k2, "Post work publicly every week")

    val e3 = major("The Forge", "Epoch III. Build the machine.")
    val k5 = goal(e3, "$5k/month")
    task(k5, "Write down the process for every repeat job")
    task(k5, "Hire the first contractor")
    val p1 = goal(e3, "$1k/month passive")
    task(p1, "Launch the first digital product")
    task(p1, "Build an email list of 500")
    val car = goal(e3, "Own my car outright")
    task(car, "Sell what I don't need")
    task(car, "Pay cash, no finance")

    val e4 = major("Break Free", "The end goal. Nobody owns your time.", endGoal = true)
    val k10 = goal(e4, "$10k/month")
    task(k10, "Six months of expenses in the bank")
    task(k10, "Hand in the resignation")
    val p3 = goal(e4, "$3k/month passive")
    task(p3, "Second product line")
    task(p3, "Put surplus into index funds every month")
    val house = goal(e4, "Own my own house")
    task(house, "Get pre-approved")
    task(house, "Save a 20% deposit")

    // ---- beyond (purple) ----
    val b1 = major("Dominion", "Beyond. Past freedom, into power.")
    val k50 = goal(b1, "$50k/month")
    task(b1, "Build a team of 10")
    task(k50, "Acquire a second business")
    goal(b1, "$10k/month passive")
    goal(b1, "$1M investment portfolio")

    val b2 = major("The Black Throne", "Beyond. Few ever see this far.")
    val m1 = goal(b2, "$1M/month")
    task(m1, "Install a CEO under me")
    task(m1, "Own the building")
    goal(b2, "$10M net worth")
    goal(b2, "$100k/month passive")
    return list
}
