package dev.spikeai.search

/** One search result: which item, and how similar (dot product of length-1 vectors, ~-1..1). */
data class Hit(val id: Int, val score: Float)

interface VectorIndex {
    val size: Int
    fun add(id: Int, vector: FloatArray)
    fun search(query: FloatArray, k: Int): List<Hit>
}

internal fun dot(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

/** The honest baseline: compare the query with every vector. Exact, but cost grows with every photo. */
class FlatIndex : VectorIndex {
    private val ids = ArrayList<Int>()
    private val vectors = ArrayList<FloatArray>()
    override val size get() = ids.size

    override fun add(id: Int, vector: FloatArray) { ids += id; vectors += vector }

    override fun search(query: FloatArray, k: Int): List<Hit> {
        val top = java.util.PriorityQueue<Hit>(compareBy { it.score }) // smallest score on top, so we can drop it
        for (i in ids.indices) {
            top += Hit(ids[i], dot(query, vectors[i]))
            if (top.size > k) top.poll()
        }
        return top.sortedByDescending { it.score }
    }
}
