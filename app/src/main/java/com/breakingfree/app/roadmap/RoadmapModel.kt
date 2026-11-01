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
        fun walk(pid: String) = children(pid).forEach { if (result.add(it.id)) walk(it.id) }
        walk(id)
        return result
    }

    fun eligibleParents(tier: Tier, selfId: String?): List<RoadNode> {
        val banned = selfId?.let { descendants(it) + it } ?: emptySet()
        return outline().map { it.node }.filter { it.tier.rank > tier.rank && it.id !in banned }
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
        // Detach children whose parent no longer outranks them.
        val tmp = Roadmap(nodes)
        val fixed = nodes.map { n -> if (n.parentId != null && tmp.effectiveParent(n) == null) n.copy(parentId = null) else n }
        _map.value = Roadmap(fixed)
        save(fixed)
    }

    fun add(title: String, notes: String, tier: Tier, parentId: String?) {
        commit(_map.value.nodes + RoadNode(title = title.trim(), notes = notes.trim(), tier = tier, parentId = parentId))
    }

    fun update(node: RoadNode) {
        val old = _map.value.get(node.id) ?: return
        var nodes = _map.value.nodes.map { if (it.id == node.id) node else it }
        if (old.parentId != node.parentId) {
            // Re-parented: move to the end of its new sibling group.
            nodes = nodes.filter { it.id != node.id } + node
        }
        commit(nodes)
    }

    fun toggleDone(id: String) {
        commit(_map.value.nodes.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun delete(id: String) {
        val m = _map.value
        val victim = m.get(id) ?: return
        val newParent = m.effectiveParent(victim)?.id
        commit(m.nodes.filter { it.id != id }.map { if (it.parentId == id) it.copy(parentId = newParent) else it })
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

    fun loadExample() = commit(exampleRoadmap())

    private fun load(): Roadmap {
        if (!file.exists()) return Roadmap(exampleRoadmap()).also { save(it.nodes) }
        return runCatching {
            val arr = JSONArray(file.readText())
            Roadmap((0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RoadNode(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    notes = o.optString("notes"),
                    tier = runCatching { Tier.valueOf(o.getString("tier")) }.getOrDefault(Tier.REGULAR),
                    parentId = if (o.isNull("parent")) null else o.optString("parent").ifBlank { null },
                    done = o.optBoolean("done"),
                )
            })
        }.getOrElse { Roadmap() }
    }

    private fun save(nodes: List<RoadNode>) {
        val arr = JSONArray()
        nodes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("notes", n.notes); put("tier", n.tier.name)
                put("parent", n.parentId ?: JSONObject.NULL); put("done", n.done)
            })
        }
        runCatching {
            val tmp = File(file.parentFile, "roadmap.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }
}

private fun exampleRoadmap(): List<RoadNode> {
    val list = ArrayList<RoadNode>()
    fun n(title: String, tier: Tier, parent: RoadNode? = null, done: Boolean = false, notes: String = "") =
        RoadNode(title = title, tier = tier, parentId = parent?.id, done = done, notes = notes).also { list += it }

    val m1 = n("Clear the Ground", Tier.MAJOR, notes = "Example roadmap — edit or clear it from the menu.")
    val r1 = n("Audit the chains", Tier.REGULAR, m1)
    n("List every debt", Tier.MINOR, r1, done = true)
    n("List every habit that costs me", Tier.MINOR, r1, done = true)
    val r2 = n("Build the base", Tier.REGULAR, m1)
    n("Three months of savings", Tier.MINOR, r2)
    n("Daily training", Tier.MINOR, r2)
    n("Read one book a month", Tier.MINOR)
    val m2 = n("Forge the Weapon", Tier.MAJOR)
    val r3 = n("Master a craft", Tier.REGULAR, m2)
    n("Finish first serious project", Tier.MINOR, r3)
    n("Get paid for it", Tier.MINOR, r3)
    n("Find my people", Tier.REGULAR, m2)
    val m3 = n("Break Free — 2030", Tier.MAJOR)
    n("Independent income", Tier.REGULAR, m3)
    n("Own my time", Tier.REGULAR, m3)
    return list
}
