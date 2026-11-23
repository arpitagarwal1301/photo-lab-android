package dev.spikeai.photos.data

import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.VectorMath
import dev.spikeai.search.LinearProbe
import kotlin.random.Random

/**
 * Personal labels. Below 5 tagged photos: plain CLIP text search for "a photo of <name>".
 * From 5 up: train a one-layer model on the phone (real backprop, search-core/LinearProbe.kt),
 * starting from that text vector and nudged by your examples.
 */
class Labels(private val store: PhotoStore, private val engine: SearchEngine, private val clip: ClipEncoder) {

    class Ranking(val photos: List<Pair<Long, Float>>, val tagged: Set<Long>, val trained: Boolean, val loss: List<Float>, val trainMs: Double)

    fun rank(labelId: Long, name: String): Ranking {
        val tagged = store.tagged(labelId)
        val all = engine.allVectors()
        val textVec = clip.embedText("a photo of $name")
        if (tagged.size < LinearProbe.MIN_EXAMPLES) {
            return Ranking(all.map { (id, v) -> id to VectorMath.dot(textVec, v) }.sortedByDescending { it.second }, tagged, false, emptyList(), 0.0)
        }
        val positives = all.filter { it.first in tagged }.map { it.second }
        val negatives = all.filter { it.first !in tagged }.shuffled(Random(labelId)).take(50).map { it.second }
        val t0 = System.nanoTime()
        val probe = LinearProbe(512, init = textVec).apply { train(positives, negatives) }
        val ms = (System.nanoTime() - t0) / 1e6
        return Ranking(all.map { (id, v) -> id to probe.probability(v) }.sortedByDescending { it.second }, tagged, true, probe.lossHistory, ms)
    }
}
