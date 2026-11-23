package dev.spikeai.photos.data

import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.VectorMath
import dev.spikeai.search.LinearProbe
import kotlin.random.Random

/**
 * Personal labels. Below [Knobs.minTags] tagged photos (default 5): plain CLIP text search for "a photo of <name>".
 * From there up: train a one-layer model on the phone (real backprop, search-core/LinearProbe.kt),
 * starting from that text vector and nudged by your examples. Epochs, learning rate and L2 come from the Lab knobs.
 */
class Labels(private val store: PhotoStore, private val engine: SearchEngine, private val clip: ClipEncoder, private val knobs: Knobs) {

    /** [replay] = the top photos after each pass of training, so the app can show the ranking change as it learns. */
    class Ranking(
        val photos: List<Pair<Long, Float>>, val tagged: Set<Long>, val trained: Boolean, val loss: List<Float>, val trainMs: Double,
        val replay: List<List<Long>> = emptyList(),
    )

    fun rank(labelId: Long, name: String): Ranking {
        val tagged = store.tagged(labelId)
        val all = engine.allVectors()
        val textVec = clip.embedText("a photo of $name")
        if (tagged.size < knobs.minTags) {
            return Ranking(all.map { (id, v) -> id to VectorMath.dot(textVec, v) }.sortedByDescending { it.second }, tagged, false, emptyList(), 0.0)
        }
        val positives = all.filter { it.first in tagged }.map { it.second }
        val negatives = all.filter { it.first !in tagged }.shuffled(Random(labelId)).take(50).map { it.second }
        val t0 = System.nanoTime()
        val probe = LinearProbe(512, init = textVec).apply { train(positives, negatives, knobs.epochs, knobs.learningRate, knobs.l2) }
        val ms = (System.nanoTime() - t0) / 1e6
        // Train again with a per-pass snapshot of the top 8 (kept out of the timing above). Same seed, same result.
        val start = all.map { (id, v) -> id to VectorMath.dot(textVec, v) }.sortedByDescending { it.second }.take(8).map { it.first }
        val replay = arrayListOf(start)
        val again = LinearProbe(512, init = textVec)
        again.train(positives, negatives, knobs.epochs, knobs.learningRate, knobs.l2, onEpoch = { _, _ ->
            replay += all.map { (id, v) -> id to again.probability(v) }.sortedByDescending { it.second }.take(8).map { it.first }
        })
        return Ranking(all.map { (id, v) -> id to probe.probability(v) }.sortedByDescending { it.second }, tagged, true, probe.lossHistory, ms, replay)
    }
}
