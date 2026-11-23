package dev.spikeai.embed

import kotlin.math.sqrt

object VectorMath {
    /** Lesson 1, step 3: the network gives one 384-number vector per token; average them into one per sentence. */
    fun meanPool(perToken: Array<FloatArray>): FloatArray {
        val out = FloatArray(perToken[0].size)
        for (row in perToken) for (i in out.indices) out[i] += row[i]
        for (i in out.indices) out[i] /= perToken.size
        return out
    }

    /** Scale to length 1, so similarity is just a dot product. */
    fun normalize(v: FloatArray): FloatArray {
        val len = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / len }
    }

    /** Cosine similarity of two length-1 vectors: 1 = same direction, ~0 = unrelated. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
