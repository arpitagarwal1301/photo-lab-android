package dev.spikeai.embed

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photo → the exact input MobileCLIP was trained on:
 * shortest side to 256 (smooth, anti-aliased), centre crop 256×256, RGB, 0–255 → 0–1, channels first.
 * Pure Kotlin on ARGB ints, so the phone and the laptop test do identical maths.
 */
object ImagePrep {
    const val SIZE = 256

    /** [argb] is w×h pixels, row by row. Returns SIZE×SIZE ARGB. */
    fun resizeShortSideAndCrop(argb: IntArray, w: Int, h: Int, size: Int = SIZE): IntArray {
        val scale = size.toDouble() / min(w, h)
        val nw = if (w <= h) size else (w * scale).toInt()
        val nh = if (h < w) size else (h * scale).toInt()
        val resized = resize(argb, w, h, nw, nh)
        val left = ((nw - size) / 2.0).roundToInt()
        val top = ((nh - size) / 2.0).roundToInt()
        return IntArray(size * size) { i -> resized[(top + i / size) * nw + left + i % size] }
    }

    /** 256×256 ARGB → [3 × 256 × 256] floats in 0..1 (all reds, then all greens, then all blues). */
    fun toChw(argb: IntArray, size: Int = SIZE): FloatArray {
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            out[i] = ((p shr 16) and 0xFF) / 255f
            out[plane + i] = ((p shr 8) and 0xFF) / 255f
            out[2 * plane + i] = (p and 0xFF) / 255f
        }
        return out
    }

    /**
     * Separable triangle-filter resize (what PIL calls BILINEAR). When shrinking, the triangle is widened
     * by the shrink factor, so each output pixel averages all the input pixels it covers — no aliasing.
     */
    fun resize(argb: IntArray, w: Int, h: Int, nw: Int, nh: Int): IntArray {
        val rgb = Array(3) { c -> FloatArray(w * h) { (argb[it] shr (16 - 8 * c) and 0xFF).toFloat() } }
        val tmp = Array(3) { FloatArray(nw * h) }
        val xs = weights(w, nw)
        for (y in 0 until h) for (x in 0 until nw) {
            val (start, wts) = xs[x]
            for (c in 0..2) { var s = 0f; for (k in wts.indices) s += rgb[c][y * w + start + k] * wts[k]; tmp[c][y * nw + x] = s }
        }
        val ys = weights(h, nh)
        val out = IntArray(nw * nh)
        for (y in 0 until nh) {
            val (start, wts) = ys[y]
            for (x in 0 until nw) {
                var p = 0xFF shl 24
                for (c in 0..2) {
                    var s = 0f
                    for (k in wts.indices) s += tmp[c][(start + k) * nw + x] * wts[k]
                    p = p or (s.roundToInt().coerceIn(0, 255) shl (16 - 8 * c))
                }
                out[y * nw + x] = p
            }
        }
        return out
    }

    private fun weights(inSize: Int, outSize: Int): Array<Pair<Int, FloatArray>> {
        val scale = inSize.toDouble() / outSize
        val support = max(scale, 1.0) // triangle half-width in input pixels
        return Array(outSize) { o ->
            val center = (o + 0.5) * scale
            val lo = max(0, floor(center - support).toInt())
            val hi = min(inSize, ceil(center + support).toInt())
            val raw = DoubleArray(hi - lo) { k -> max(0.0, 1.0 - abs((lo + k + 0.5 - center) / support)) }
            val sum = raw.sum()
            lo to FloatArray(raw.size) { (raw[it] / sum).toFloat() }
        }
    }
}
