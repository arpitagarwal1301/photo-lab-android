package dev.spikeai.search

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Personal labels: learn "is this one of MY receipts / MY dog?" from a handful of tagged photos.
 *
 * We don't retrain CLIP (millions of weights). We train one tiny layer on top of its 512 numbers:
 *   p = sigmoid(w · x + b)        — 513 weights, a few KB
 * Training is real backprop, just one layer deep:
 *   loss  = −[y·ln p + (1−y)·ln(1−p)]   (cross-entropy)
 *   ∂loss/∂w = (p − y) · x,  ∂loss/∂b = (p − y)
 *   w ← w − lr · gradient   (Adam: per-weight step sizes that adapt)
 * Start w from the CLIP text vector of the label name ("my dog"), so with zero examples it already works
 * like normal search, and every tagged photo nudges it towards your version of "dog".
 * Defaults picked on COCO (results/m1-benchmark.md): small steps + a strong pull back to the text vector.
 * Below [MIN_EXAMPLES] tagged photos, plain text search wins, so the app only trains from 5 up.
 */
class LinearProbe(val dim: Int, init: FloatArray? = null) {
    private val anchor = init?.copyOf() ?: FloatArray(dim)
    val w = anchor.copyOf()
    var b = 0f
    val lossHistory = ArrayList<Float>()

    fun probability(x: FloatArray) = sigmoid(dot(w, x) * SCALE + b)

    /** [positives]/[negatives] are length-1 CLIP image vectors. */
    fun train(positives: List<FloatArray>, negatives: List<FloatArray>, epochs: Int = 30, lr: Float = 0.003f, l2: Float = 1f, seed: Long = 1) {
        // l2 pulls w back towards the starting point (the label's text vector), not towards zero:
        // a few examples should adjust the meaning of "dog", not replace it.
        val data = positives.map { it to 1f } + negatives.map { it to 0f }
        if (data.isEmpty()) return
        // Balance classes: 5 positives vs 500 negatives shouldn't teach "always say no".
        val posWeight = if (positives.isEmpty()) 1f else negatives.size.coerceAtLeast(1).toFloat() / positives.size
        val mW = FloatArray(dim); val vW = FloatArray(dim); var mB = 0f; var vB = 0f
        val b1 = 0.9f; val b2 = 0.999f; var step = 0
        val rnd = Random(seed)
        repeat(epochs) {
            var total = 0f
            for ((x, y) in data.shuffled(rnd)) {
                val p = probability(x)
                val weight = if (y == 1f) posWeight else 1f
                total += weight * -(y * ln(p + 1e-7f) + (1 - y) * ln(1 - p + 1e-7f))
                val g = weight * (p - y)                       // ∂loss/∂(logit)
                step++
                val c1 = 1 - Math.pow(b1.toDouble(), step.toDouble()).toFloat()
                val c2 = 1 - Math.pow(b2.toDouble(), step.toDouble()).toFloat()
                for (i in 0 until dim) {
                    val gi = g * x[i] * SCALE + l2 * (w[i] - anchor[i]) // chain rule through ×SCALE, plus the pull back to the anchor
                    mW[i] = b1 * mW[i] + (1 - b1) * gi
                    vW[i] = b2 * vW[i] + (1 - b2) * gi * gi
                    w[i] -= lr * (mW[i] / c1) / (sqrt(vW[i] / c2) + 1e-8f)
                }
                mB = b1 * mB + (1 - b1) * g; vB = b2 * vB + (1 - b2) * g * g
                b -= lr * (mB / c1) / (sqrt(vB / c2) + 1e-8f)
            }
            lossHistory += total / data.size
        }
    }

    companion object {
        /** CLIP similarities live in ~0.1–0.35; scaling (like CLIP's own logit scale) gives the sigmoid room to move. */
        const val SCALE = 20f
        const val MIN_EXAMPLES = 5
        private fun sigmoid(z: Float) = 1f / (1f + exp(-z))
    }
}
