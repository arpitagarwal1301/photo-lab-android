package dev.spikeai.photos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.spikeai.photos.AppState
import dev.spikeai.photos.data.SearchTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val STEPS = 7
/** How long each step stays on screen while playing, so its own animation can finish. */
private val STEP_MS = listOf(1800L, 2600L, 2200L, 3000L, 4200L, 3000L)

/**
 * "Inside this search": the same search the Search tab ran, re-run with tracing, shown as 7 steps with real numbers.
 * ▶ plays them one by one (each step animates itself in); "Next step" goes by hand. [focus] highlights one photo throughout.
 */
@Composable
fun JourneyScreen(ready: dev.spikeai.photos.AppState.Ready, query: String, focus: Long?, onClose: () -> Unit) {
    val trace by produceState<SearchTrace?>(null, query) { value = withContext(Dispatchers.Default) { ready.engine.trace(query) } }
    var shown by remember { mutableIntStateOf(1) }
    var playing by remember { mutableStateOf(true) }
    var run by remember { mutableIntStateOf(0) } // bumping it replays every step's animation
    val list = rememberLazyListState()
    LaunchedEffect(playing, trace, run) {
        if (!playing || trace == null) return@LaunchedEffect
        while (shown < STEPS) { delay(STEP_MS[shown - 1]); shown++ }
        playing = false
    }
    LaunchedEffect(shown) { if (shown > 1) list.animateScrollToItem(shown - 1) }

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClose) { Text("← Back") }
                    Text("Inside this search", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ if (shown >= STEPS) { shown = 1; run++; playing = true } else playing = !playing }) {
                        Text(if (playing) "⏸ Pause" else if (shown >= STEPS) "↺ Replay" else "▶ Play")
                    }
                    FilledTonalButton({ playing = false; if (shown < STEPS) shown++ }, enabled = shown < STEPS) { Text("Next step ›") }
                    TextButton({ playing = false; shown = STEPS }) { Text("Show all") }
                }
                val t = trace
                if (t == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else key(run) {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shown) { i ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    when (i) {
                                        0 -> StepTyped(t)
                                        1 -> StepTokens(t)
                                        2 -> StepVectors(t)
                                        3 -> StepIndex(t)
                                        4 -> StepSignals(t, focus)
                                        5 -> StepFusion(t, focus)
                                        else -> StepResults(t, focus)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTyped(t: SearchTrace) {
    StepTitle(1, "You typed", "Everything below happened on this phone in %.0f ms, with no internet.".format(t.timing.total))
    val p = rememberReveal(durationMs = 60 * t.query.length + 200)
    val n = (t.query.length * p).toInt()
    Text(t.query.take(n) + if (p < 1f) "▌" else "", fontSize = 28.sp, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTokens(t: SearchTrace) {
    StepTitle(2, "Cut into pieces → numbers", "Networks can't read letters. Each model has a fixed list of word pieces (CLIP 49,408, MiniLM 30,522) and replaces each piece with its number. The first and last pieces mark start and end.")
    val p = rememberReveal(1600)
    Text("CLIP (byte-pair pieces)", style = MaterialTheme.typography.labelLarge)
    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        t.clipTokens.take((t.clipTokens.size * p).toInt().coerceAtLeast(1)).forEachIndexed { i, tok -> TokenChip(tok, t.clipIds[i], highlight = i == 0 || i == t.clipTokens.lastIndex) }
    }
    Text("MiniLM (word-pieces)", style = MaterialTheme.typography.labelLarge)
    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        t.pieces.take((t.pieces.size * p).toInt().coerceAtLeast(1)).forEachIndexed { i, tok -> TokenChip(tok, t.pieceIds[i], highlight = i == 0 || i == t.pieces.lastIndex) }
    }
    Text("CLIP pads to 77 numbers (its fixed input size); MiniLM uses exactly as many as it needs.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepVectors(t: SearchTrace) {
    StepTitle(3, "Numbers → meaning (the networks)", "Each network turns the pieces into one list of numbers, a vector. Similar meanings point the same way. Every photo was turned into 512 numbers the same way when it was indexed.")
    val p = rememberReveal(1500)
    Text("CLIP text network → 512 numbers · %.0f ms".format(t.timing.clipMs), style = MaterialTheme.typography.labelLarge)
    VectorStrip(t.clipVec, p, Modifier.padding(vertical = 4.dp))
    Mono(t.clipVec.take(5).joinToString(", ") { "%.3f".format(it) } + " …")
    Text("MiniLM → 384 numbers · %.0f ms".format(t.timing.miniLmMs), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
    VectorStrip(t.textVec, p, Modifier.padding(vertical = 4.dp))
    Mono(t.textVec.take(5).joinToString(", ") { "%.3f".format(it) } + " …")
    Text("orange = positive, blue = negative, stronger = bigger", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepIndex(t: SearchTrace) {
    val ix = t.index
    StepTitle(4, "Find the nearest photos (vector index)",
        if (ix.kind == "HNSW") "HNSW is a graph of photos linked to their neighbours, in layers like motorways over streets. It starts at the top, hops towards the query, drops a layer, and repeats."
        else "Brute force: compare the query with every photo. Exact, but slow for big galleries. Switch HNSW on in the Lab.")
    val p = rememberReveal(2400)
    if (ix.kind == "HNSW") HnswPicture(ix, p, t.answer.results.take(3).map { it.photoId }.toSet(), Modifier.padding(vertical = 4.dp))
    Text("Measured %d of %d photos · %.1f ms · ef = %d".format(ix.compared, ix.total, t.timing.indexMs, ix.ef), style = MaterialTheme.typography.labelLarge)
    Text(if (ix.kind == "HNSW" && ix.ef >= ix.total) "With only ${ix.total} photos and ef = ${ix.ef}, HNSW ends up measuring them all. It pays off with thousands: try ef = 8 in the Lab and watch this number drop."
         else "Brute force would measure all ${ix.total}. With 10,000 photos the difference is huge.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Focusable(focus: Boolean, content: @Composable () -> Unit) {
    Box(if (focus) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).padding(4.dp) else Modifier.padding(4.dp)) { content() }
}

@Composable
private fun StepSignals(t: SearchTrace, focus: Long?) {
    StepTitle(5, "Three ways to match", "Each signal makes its own ranked list. A photo only gets on a list if it clears that signal's bar.")
    val p = rememberReveal(2000)

    Text("① Looks like it (CLIP: query vs photo pixels)" + if (!t.lookOn) "  — switched off in Lab" else "", style = MaterialTheme.typography.titleSmall)
    Text(if (t.guessesOn) "Kept only if the query fits better than the best generic guess (black line)." else "Other-guesses check is off in the Lab: every nearest photo is kept.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val maxLook = (t.look.maxOfOrNull { maxOf(it.score, it.rivalScore) } ?: 0.3f) * 1.1f
    t.look.take(6).forEach { r ->
        Focusable(r.photoId == focus) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(r.photoId, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    ScoreBar(r.score, maxLook, if (t.guessesOn) r.rivalScore else null, r.kept, p)
                    Text("%.3f  vs best guess %.3f".format(r.score, r.rivalScore), style = MaterialTheme.typography.labelSmall)
                    Text("“${r.rival}”", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (r.kept) "✓" else "✗", color = if (r.kept) Kept else Dropped, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Text("② Text means it (MiniLM: query vs chunks of text read from photos)" + if (!t.meaningOn) "  — off" else "", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
    Text("Kept if similarity is above the bar (black line, %.2f).".format(t.minText), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (t.meaning.isEmpty()) Text("No text read from any photo yet.", style = MaterialTheme.typography.bodySmall)
    t.meaning.take(4).forEach { r ->
        Focusable(r.photoId == focus) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(r.photoId, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text("“${r.chunk}”", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ScoreBar(r.score, 1f, t.minText, r.kept, p)
                    Text("%.3f".format(r.score), style = MaterialTheme.typography.labelSmall)
                }
                Text(if (r.kept) "✓" else "✗", color = if (r.kept) Kept else Dropped, style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    Text("③ Contains the words (BM25)" + if (!t.wordsOn) "  — off" else "", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
    Text("Query words after dropping “a”, “the”, …: " + t.words.joinToString(" · ").ifEmpty { "(none)" } +
        ". Score per word = rarity (idf) × how often it appears, with diminishing returns.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (t.wordRows.isEmpty()) Text("No photo contains these exact words.", style = MaterialTheme.typography.bodySmall)
    t.wordRows.take(4).forEach { r ->
        Focusable(r.photoId == focus) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(r.photoId, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    r.terms.filter { it.tf > 0 }.forEach { w -> Mono("%s: ×%d, idf %.2f → %.2f".format(w.word, w.tf, w.idf, w.part)) }
                }
                Text("%.2f".format(r.total), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun StepFusion(t: SearchTrace, focus: Long?) {
    StepTitle(6, "Merge the three lists (Reciprocal Rank Fusion)",
        "Scores from different signals can't be compared, so only positions count: each list gives 1 / (k + rank), k = ${t.rrfK}. Being on two lists beats being #1 on one.")
    if (t.answer.weak) { Text("Nothing cleared any bar → the app shows the closest-looking photos and says “No strong match”.", style = MaterialTheme.typography.bodyMedium); return }
    val p = rememberReveal(1800)
    val max = t.fusion.maxOfOrNull { it.total } ?: 1f
    val names = listOf("looks", "means", "words")
    t.fusion.take(6).forEachIndexed { i, r ->
        Focusable(r.photoId == focus) {
            Row(Modifier.alpha(((p * 6) - i).coerceIn(0f, 1f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#${i + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.titleSmall)
                Thumb(r.photoId, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Mono(r.ranks.indices.joinToString("  ") { s -> r.ranks[s]?.let { "${names[s]} #$it" } ?: "${names[s]} –" })
                    Mono(r.parts.filter { it > 0 }.joinToString(" + ") { "%.4f".format(it) } + " = %.4f".format(r.total))
                    ScoreBar(r.total, max, progress = p)
                }
            }
        }
    }
}

@Composable
private fun StepResults(t: SearchTrace, focus: Long?) {
    StepTitle(7, "Results", if (t.answer.weak) "No strong match: these are only the closest-looking photos." else "${t.answer.results.size} photos, best first. Tap a photo on the Search tab to see why it matched.")
    val res = t.answer.results.take(9)
    for (row in res.chunked(3)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { r ->
                val i = res.indexOf(r)
                val a = rememberReveal(500, delayMs = 120 * i)
                Box(Modifier.weight(1f).scale(0.6f + 0.4f * a).alpha(a)) {
                    Thumb(r.photoId, if (r.photoId == focus) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
                    Text(" ${i + 1} ", Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(bottomEnd = 8.dp)), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
    (res.firstOrNull { it.photoId == focus } ?: res.firstOrNull())?.let { r ->
        Text("Why #${res.indexOf(r) + 1} matched:", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
        r.why.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
    }
}
