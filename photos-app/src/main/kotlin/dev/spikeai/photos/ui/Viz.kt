package dev.spikeai.photos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.spikeai.photos.data.IndexTrace
import kotlin.math.abs
import kotlin.math.max

/** Colours used for numbers everywhere: positive = orange, negative = blue, like a heat map. */
val Positive = Color(0xFFE8710A)
val Negative = Color(0xFF1A73E8)
val Kept = Color(0xFF188038)
val Dropped = Color(0xFFB3261E)

/** 0 → 1 once, when the composable first appears (each step card animates itself in). */
@Composable
fun rememberReveal(durationMs: Int = 900, delayMs: Int = 0): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing)) }
    return a.value
}

/** A vector as a barcode: one thin bar per number, colour = sign, strength = size. [progress] reveals it left to right. */
@Composable
fun VectorStrip(vec: FloatArray, progress: Float = 1f, modifier: Modifier = Modifier) {
    val maxAbs = vec.maxOf { abs(it) }.coerceAtLeast(1e-6f)
    Canvas(modifier.fillMaxWidth().height(28.dp)) {
        val w = size.width / vec.size
        val shown = (vec.size * progress).toInt()
        for (i in 0 until shown) {
            val v = vec[i]
            drawRect((if (v >= 0) Positive else Negative).copy(alpha = 0.15f + 0.85f * abs(v) / maxAbs), Offset(i * w, 0f), androidx.compose.ui.geometry.Size(max(w, 1f), size.height))
        }
    }
}

/** A score as a bar, with an optional bar-to-beat marker (the threshold or the best other guess). */
@Composable
fun ScoreBar(value: Float, max: Float, marker: Float? = null, kept: Boolean = true, progress: Float = 1f, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        drawRect(track)
        val f = (value / max).coerceIn(0f, 1f) * progress
        drawRect(if (kept) Kept else Dropped.copy(alpha = 0.6f), size = androidx.compose.ui.geometry.Size(size.width * f, size.height))
        marker?.let { m -> val x = size.width * (m / max).coerceIn(0f, 1f); drawLine(ink, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 4f) }
    }
}

/** A small rounded chip showing a token piece over its id. */
@Composable
fun TokenChip(piece: String, id: Long, highlight: Boolean = false) {
    Column(
        Modifier.background(if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(piece, style = MaterialTheme.typography.bodyMedium)
        Text("$id", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * HNSW as a picture: one row per layer (top = few photos, bottom = all), the greedy path drawn hop by hop on the
 * upper layers, then the photos examined on layer 0 lighting up best-first. [progress] animates it.
 */
@Composable
fun HnswPicture(trace: IndexTrace, progress: Float, highlight: Set<Long>, modifier: Modifier = Modifier) {
    val layers = trace.nodesPerLayer.size
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height((44 * layers + 16).dp)) {
            Canvas(Modifier.fillMaxWidth().height((44 * layers + 16).dp)) {
                fun y(layer: Int) = 8.dp.toPx() + (layers - 1 - layer) * 44.dp.toPx() + 22.dp.toPx()
                fun x(id: Long) = size.width * (0.06f + 0.88f * ((id * 0.6180339887).mod(1.0)).toFloat())
                // Background dots: how many photos live on each layer.
                for (l in 0 until layers) {
                    val n = trace.nodesPerLayer[l].coerceAtMost(80)
                    for (i in 0 until n) drawCircle(faint, 3.dp.toPx(), Offset(size.width * (0.06f + 0.88f * (i + 0.5f) / n), y(l)))
                }
                val upper = trace.hops.filter { it.layer > 0 }
                val ground = trace.hops.filter { it.layer == 0 }
                val total = upper.size + ground.size
                val shown = (total * progress).toInt()
                // Upper layers: a path with drops between layers.
                var prev: Offset? = null
                upper.take(shown).forEach { h ->
                    val p = Offset(x(h.photoId), y(h.layer))
                    prev?.let { drawLine(primary, it, p, strokeWidth = 3.dp.toPx()) }
                    drawCircle(primary, 5.dp.toPx(), p)
                    prev = p
                }
                // Layer 0: nodes examined best-first light up.
                val g = (shown - upper.size).coerceAtLeast(0)
                if (g > 0) prev?.let { drawLine(primary, it, Offset(it.x, y(0)), strokeWidth = 3.dp.toPx()) }
                ground.take(g).forEach { h -> drawCircle(primary.copy(alpha = 0.45f), 5.dp.toPx(), Offset(x(h.photoId), y(0))) }
                if (progress >= 1f) highlight.forEach { id -> drawCircle(ink, 9.dp.toPx(), Offset(x(id), y(0)), style = Stroke(2.dp.toPx())) }
            }
            for (l in 0 until layers) Text(
                "layer $l · ${trace.nodesPerLayer[l]}", Modifier.padding(start = 2.dp, top = (2 + (layers - 1 - l) * 44).dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Monospace line. */
@Composable
fun Mono(s: String, modifier: Modifier = Modifier) = Text(s, modifier, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))

/** Card header for a numbered step. */
@Composable
fun StepTitle(n: Int, title: String, explain: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
    Text(explain, Modifier.padding(top = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
