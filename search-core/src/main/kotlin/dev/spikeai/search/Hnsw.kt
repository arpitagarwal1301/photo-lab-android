package dev.spikeai.search

import java.util.PriorityQueue
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/**
 * HNSW — Hierarchical Navigable Small World graph (Malkov & Yashunin, 2016). The index inside most vector DBs.
 *
 * Idea: link every vector to its M nearest neighbours, forming a graph. To search, start somewhere and keep
 * hopping to whichever neighbour is closer to the query. To make the hops long at first and short later,
 * build several layers like a skip list: few nodes on top (motorways), every node on layer 0 (local streets).
 *
 *   layer 2:  A ─────────────── F
 *   layer 1:  A ──── C ──── E ── F
 *   layer 0:  A─B─C─D─E─F─G─H─I─J   (every vector)
 *
 * Knobs: m (links per node), efConstruction (care taken when inserting), ef (care taken when searching).
 * Higher = more accurate, slower. Lesson 4 turns these knobs.
 */
/** What one search did, step by step — for the "inside this search" screen. */
class HnswTrace {
    /** One step: on upper layers the greedy path; on layer 0 the nodes expanded, best-first. */
    class Hop(val layer: Int, val id: Int, val sim: Float)
    val hops = ArrayList<Hop>()
    /** Distinct vectors compared with the query (brute force compares all of them). */
    val seen = HashSet<Int>()
    val compared get() = seen.size
}

class Hnsw(
    val m: Int = 16,
    val efConstruction: Int = 100,
    var ef: Int = 64,
    seed: Long = 42,
) : VectorIndex {
    private val m0 = 2 * m                    // layer 0 is denser
    private val levelMult = 1.0 / ln(m.toDouble())
    private val random = Random(seed)

    private val ids = ArrayList<Int>()
    private val vectors = ArrayList<FloatArray>()
    private val links = ArrayList<Array<IntArrayList>>() // links[node][layer] = neighbour nodes
    private var entry = -1
    private var topLayer = -1

    override val size get() = ids.size

    /** Node levels follow a geometric distribution: about 1 in m nodes reaches layer 1, 1 in m² layer 2… */
    private fun randomLevel(): Int = (-ln(1.0 - random.nextDouble()) * levelMult).toInt()

    override fun add(id: Int, vector: FloatArray) {
        val node = ids.size
        val level = randomLevel()
        ids += id; vectors += vector
        links += Array(level + 1) { IntArrayList() }
        if (entry < 0) { entry = node; topLayer = level; return }

        // 1. Fly down the upper layers greedily (one best candidate at a time) to find a good starting point.
        var cur = entry
        for (layer in topLayer downTo level + 1) cur = greedy(vector, cur, layer)

        // 2. On each layer the new node lives on: find efConstruction candidates, link to the best m of them.
        var entries = listOf(cur)
        for (layer in minOf(level, topLayer) downTo 0) {
            val candidates = searchLayer(vector, entries, efConstruction, layer)
            val maxLinks = if (layer == 0) m0 else m
            val chosen = selectNeighbours(vector, candidates, m)
            for (nb in chosen) {
                links[node][layer].add(nb)
                links[nb][layer].add(node)
                if (links[nb][layer].size > maxLinks) prune(nb, layer, maxLinks) // keep neighbour lists bounded
            }
            entries = candidates.map { it.node }
        }
        if (level > topLayer) { topLayer = level; entry = node }
    }

    override fun search(query: FloatArray, k: Int): List<Hit> = search(query, k, null)

    fun search(query: FloatArray, k: Int, trace: HnswTrace?): List<Hit> {
        if (entry < 0) return emptyList()
        var cur = entry
        for (layer in topLayer downTo 1) cur = greedy(query, cur, layer, trace)
        return searchLayer(query, listOf(cur), max(ef, k), 0, trace).take(k).map { Hit(ids[it.node], it.sim) }
    }

    val layerCount get() = topLayer + 1
    fun nodesOnLayer(layer: Int) = links.count { it.size > layer }
    /** Highest layer this id lives on (0 = only the local streets). */
    fun levelOf(id: Int): Int = ids.indexOf(id).let { if (it < 0) -1 else links[it].size - 1 }

    private class Cand(val node: Int, val sim: Float)

    /** Keep hopping to the most similar neighbour until no neighbour is better. */
    private fun greedy(q: FloatArray, start: Int, layer: Int, trace: HnswTrace? = null): Int {
        var best = start
        var bestSim = dot(q, vectors[start])
        trace?.let { it.seen += start; it.hops += HnswTrace.Hop(layer, ids[best], bestSim) }
        var improved = true
        while (improved) {
            improved = false
            links[best][layer].forEach { nb ->
                val s = dot(q, vectors[nb])
                trace?.let { it.seen += nb }
                if (s > bestSim) { bestSim = s; best = nb; improved = true }
            }
            if (improved) trace?.let { it.hops += HnswTrace.Hop(layer, ids[best], bestSim) }
        }
        return best
    }

    /**
     * Best-first search keeping the ef best nodes seen so far.
     * frontier = nodes still to expand (most similar first); found = current best ef (least similar on top).
     * Stop when the most promising unexpanded node is worse than the worst one we're keeping.
     */
    private fun searchLayer(q: FloatArray, entries: List<Int>, ef: Int, layer: Int, trace: HnswTrace? = null): List<Cand> {
        val visited = HashSet<Int>()
        val frontier = PriorityQueue<Cand>(compareByDescending { it.sim })
        val found = PriorityQueue<Cand>(compareBy { it.sim })
        for (e in entries) if (visited.add(e)) Cand(e, dot(q, vectors[e])).let { frontier += it; found += it; trace?.let { t -> t.seen += e } }
        while (found.size > ef) found.poll()
        while (frontier.isNotEmpty()) {
            val c = frontier.poll()
            if (found.size >= ef && c.sim < found.peek().sim) break
            trace?.let { if (it.hops.size < 200) it.hops += HnswTrace.Hop(layer, ids[c.node], c.sim) }
            links[c.node][layer].forEach { nb ->
                if (visited.add(nb)) {
                    val s = dot(q, vectors[nb])
                    trace?.let { it.seen += nb }
                    if (found.size < ef || s > found.peek().sim) {
                        val cand = Cand(nb, s)
                        frontier += cand; found += cand
                        if (found.size > ef) found.poll()
                    }
                }
            }
        }
        return found.sortedByDescending { it.sim }
    }

    /**
     * The HNSW "heuristic": take candidates best-first, but skip one if it's closer to an already-chosen
     * neighbour than to us. That spreads links in different directions instead of a tight clump,
     * which keeps the graph navigable.
     */
    private fun selectNeighbours(q: FloatArray, candidates: List<Cand>, count: Int): List<Int> {
        val chosen = ArrayList<Int>(count)
        for (c in candidates) {
            if (chosen.size >= count) break
            val v = vectors[c.node]
            if (chosen.none { dot(v, vectors[it]) > c.sim }) chosen += c.node
        }
        if (chosen.size < count) for (c in candidates) { if (chosen.size >= count) break; if (c.node !in chosen) chosen += c.node }
        return chosen
    }

    private fun prune(node: Int, layer: Int, maxLinks: Int) {
        val v = vectors[node]
        val cands = links[node][layer].toList().map { Cand(it, dot(v, vectors[it])) }.sortedByDescending { it.sim }
        links[node][layer] = IntArrayList().apply { selectNeighbours(v, cands, maxLinks).forEach(::add) }
    }

    /** Average links per node on layer 0, and number of layers — shown in the Lab. */
    fun stats(): String = "layers ${topLayer + 1}, avg links ${"%.1f".format(links.sumOf { it[0].size }.toDouble() / max(1, size))}"
}

/** Growable int list without boxing (a phone with 10k photos × 32 links shouldn't allocate 320k Integers). */
class IntArrayList {
    private var data = IntArray(8)
    var size = 0; private set
    fun add(v: Int) { if (size == data.size) data = data.copyOf(size * 2); data[size++] = v }
    inline fun forEach(f: (Int) -> Unit) { for (i in 0 until size) f(get(i)) }
    fun get(i: Int) = data[i]
    fun toList() = List(size) { data[it] }
}
