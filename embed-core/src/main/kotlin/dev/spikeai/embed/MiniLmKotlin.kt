package dev.spikeai.embed

import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * MiniLM (a 6-layer BERT) with no ML library — every multiply is here.
 * Same maths as model.onnx; EmbedderTest checks both give the same numbers.
 *
 * Shapes: n = number of tokens, H = 384 numbers per token.
 *   ids [n] → lookup → x [n × H] → 6 × (attention, feed-forward) → x [n × H]
 */
class MiniLmKotlin(w: SafeTensors) : MiniLmEngine {
    private val H = 384
    private val heads = 12
    private val dh = H / heads          // 32 numbers per attention head
    private val ff = 1536               // feed-forward expands 384 → 1536 → 384
    private val eps = 1e-12f

    private val word = w.tensor("embeddings.word_embeddings.weight").data      // [30522 × 384]
    private val pos = w.tensor("embeddings.position_embeddings.weight").data    // [512 × 384]
    private val type = w.tensor("embeddings.token_type_embeddings.weight").data // [2 × 384]
    private val embLn = Norm(w, "embeddings.LayerNorm")

    private class Linear(w: SafeTensors, name: String) {
        val weight = w.tensor("$name.weight") // PyTorch layout: [out × in]
        val bias = w.tensor("$name.bias").data
        val outDim = weight.shape[0].toInt()
        val inDim = weight.shape[1].toInt()
    }

    private class Norm(w: SafeTensors, name: String) {
        val gamma = w.tensor("$name.weight").data
        val beta = w.tensor("$name.bias").data
    }

    private inner class Layer(i: Int) {
        val p = "encoder.layer.$i"
        val q = Linear(w, "$p.attention.self.query")
        val k = Linear(w, "$p.attention.self.key")
        val v = Linear(w, "$p.attention.self.value")
        val attnOut = Linear(w, "$p.attention.output.dense")
        val attnNorm = Norm(w, "$p.attention.output.LayerNorm")
        val up = Linear(w, "$p.intermediate.dense")
        val down = Linear(w, "$p.output.dense")
        val outNorm = Norm(w, "$p.output.LayerNorm")
    }

    private val w = w
    private val layers = List(6) { Layer(it) }

    override fun hidden(ids: LongArray): Array<FloatArray> {
        val n = ids.size
        // 1. Lookup: word meaning + "I am in slot t" + "sentence A", then normalise.
        var x = Array(n) { t ->
            val id = ids[t].toInt()
            FloatArray(H) { i -> word[id * H + i] + pos[t * H + i] + type[i] }.also { layerNorm(it, embLn) }
        }
        for (layer in layers) {
            // 2. Attention: each token builds a query ("what am I looking for?"),
            //    a key ("what do I contain?") and a value ("what I pass on").
            val q = linear(x, layer.q)
            val k = linear(x, layer.k)
            val v = linear(x, layer.v)
            val ctx = Array(n) { FloatArray(H) }
            val scale = 1f / sqrt(dh.toFloat())
            for (h in 0 until heads) {          // 12 heads look for different relations in parallel
                val off = h * dh
                for (i in 0 until n) {
                    // how much token i should listen to every token j
                    val score = FloatArray(n) { j -> dotSlice(q[i], k[j], off, dh) * scale }
                    softmax(score)               // → weights that add up to 1
                    for (j in 0 until n) {
                        val wgt = score[j]
                        for (d in 0 until dh) ctx[i][off + d] += wgt * v[j][off + d]
                    }
                }
            }
            // 3. Mix heads, add the original back in (residual), normalise.
            val a = linear(ctx, layer.attnOut)
            x = Array(n) { t -> FloatArray(H) { i -> a[t][i] + x[t][i] }.also { layerNorm(it, layer.attnNorm) } }
            // 4. Feed-forward: per token, expand to 1536, bend with GELU, shrink back. Residual + normalise.
            val up = linear(x, layer.up)
            for (row in up) for (i in row.indices) row[i] = gelu(row[i])
            val down = linear(up, layer.down)
            x = Array(n) { t -> FloatArray(H) { i -> down[t][i] + x[t][i] }.also { layerNorm(it, layer.outNorm) } }
        }
        return x
    }

    /** y = W·x + b for every token. W is [out × in], so row o of W is a contiguous slice. */
    private fun linear(x: Array<FloatArray>, l: Linear): Array<FloatArray> {
        val wd = l.weight.data
        return Array(x.size) { t ->
            val xt = x[t]
            FloatArray(l.outDim) { o ->
                var s = l.bias[o]
                val base = o * l.inDim
                for (i in 0 until l.inDim) s += wd[base + i] * xt[i]
                s
            }
        }
    }

    /** Shift to mean 0, scale to variance 1, then apply the learned per-number scale (gamma) and shift (beta). */
    private fun layerNorm(v: FloatArray, n: Norm) {
        val mean = v.average().toFloat()
        var varSum = 0f
        for (x in v) varSum += (x - mean) * (x - mean)
        val inv = 1f / sqrt(varSum / v.size + eps)
        for (i in v.indices) v[i] = (v[i] - mean) * inv * n.gamma[i] + n.beta[i]
    }

    private fun softmax(s: FloatArray) {
        val max = s.max()
        var sum = 0f
        for (i in s.indices) { s[i] = exp(s[i] - max); sum += s[i] }
        for (i in s.indices) s[i] /= sum
    }

    private fun dotSlice(a: FloatArray, b: FloatArray, off: Int, len: Int): Float {
        var s = 0f
        for (d in off until off + len) s += a[d] * b[d]
        return s
    }

    /** GELU: like ReLU but smooth. x · P(normal ≤ x) = 0.5·x·(1 + erf(x/√2)). */
    private fun gelu(x: Float): Float = 0.5f * x * (1f + erf(x / 1.4142135f))

    /** erf, accurate to ~1e-7 (Numerical Recipes, erfc Chebyshev fit). */
    private fun erf(x: Float): Float {
        val z = abs(x.toDouble())
        val t = 1.0 / (1.0 + 0.5 * z)
        val r = t * exp(
            -z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + t * (-0.18628806 +
                t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))),
        )
        val e = 1.0 - r
        return (if (x >= 0) e else -e).toFloat()
    }

    companion object {
        fun load(dir: File) = MiniLmKotlin(SafeTensors(File(dir, "model.safetensors")))
    }
}
