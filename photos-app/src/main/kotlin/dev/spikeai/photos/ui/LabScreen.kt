package dev.spikeai.photos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import dev.spikeai.photos.data.Knobs
import dev.spikeai.photos.data.SearchEngine
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.spikeai.embed.Embedder
import dev.spikeai.embed.VectorMath
import dev.spikeai.photos.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The learning corner: see the numbers, turn the knobs, watch what changes. */
@Composable
fun LabScreen(state: AppState, ready: AppState.Ready) {
    var searchVersion by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Lab", style = MaterialTheme.typography.headlineSmall)
        Text("Every number the app uses is a knob here. Turn one, see the effect. Tap “How did this search work?” on the Search tab to watch a search step by step.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IndexCard(state, ready)
        KnobGroupCard(state.knobs, Knobs.Group.SEARCH, onChange = { searchVersion++ }) { LivePreview(ready, searchVersion) }
        KnobGroupCard(state.knobs, Knobs.Group.INDEX, onChange = {}) { IndexActions(ready) }
        KnobGroupCard(state.knobs, Knobs.Group.TRAINING, onChange = {}) {
            Text("Open a label on the Labels tab and press ▶ Watch it train to see the effect.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EmbeddingCard(state, ready)
    }
}

@Composable
private fun IndexCard(state: AppState, ready: AppState.Ready) {
    val p by ready.indexer.progress.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your index", style = MaterialTheme.typography.titleMedium)
            Mono("photos (CLIP vectors)  ${ready.engine.photoCount}")
            Mono("text chunks (MiniLM)    ${ready.engine.chunkCount}")
            Mono("index                   ${ready.engine.indexStats()}")
            Mono("status                  ${p.phase} ${p.done}/${p.total}" + if (p.msPerPhoto > 0) "  (%.0f ms each)".format(p.msPerPhoto) else "")
            Mono("models loaded in        ${ready.loadMs} ms  (CLIP text: ${state.models.clipTextName})")
        }
    }
}

/** One card per knob group: a control per knob, its explanation, and [extra] (preview or actions) underneath. */
@Composable
private fun KnobGroupCard(knobs: Knobs, group: Knobs.Group, onChange: () -> Unit, extra: @Composable () -> Unit) {
    var resetCount by remember { mutableIntStateOf(0) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton({ knobs.reset(group); resetCount++; onChange() }) { Text("Reset") }
            }
            Text(group.help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            key(resetCount) { Knobs.ALL.filter { it.group == group }.forEach { KnobControl(knobs, it, onChange) } }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            extra()
        }
    }
}

@Composable
private fun KnobControl(knobs: Knobs, k: Knobs.Knob, onChange: () -> Unit) {
    var v by remember { mutableFloatStateOf(knobs[k]) }
    fun show(x: Float) = when (k.kind) { Knobs.Kind.INT -> "${x.toInt()}"; Knobs.Kind.DECIMAL -> if (k.max <= 0.1f) "%.4f".format(x) else "%.2f".format(x); Knobs.Kind.SWITCH -> "" }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (v != k.default) FontWeight.SemiBold else FontWeight.Normal)
            if (k.kind == Knobs.Kind.SWITCH) Switch(v > 0.5f, { on -> v = if (on) 1f else 0f; knobs[k] = v; onChange() })
            else Text(show(v) + if (v != k.default) "  (default ${show(k.default)})" else "", style = MaterialTheme.typography.labelLarge)
        }
        if (k.kind != Knobs.Kind.SWITCH) Slider(
            v, { x -> v = if (k.kind == Knobs.Kind.INT) Math.round(x).toFloat() else x }, valueRange = k.min..k.max,
            onValueChangeFinished = { knobs[k] = v; onChange() },
        )
        Text(k.help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Re-runs a sample search after every knob change and says what moved. */
@Composable
private fun LivePreview(ready: AppState.Ready, version: Int) {
    var query by rememberSaveable { mutableStateOf("wifi password") }
    var lastQuery by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf<SearchEngine.Answer?>(null) }
    var diff by remember { mutableStateOf("") }
    LaunchedEffect(query, version) {
        delay(250)
        val a = withContext(Dispatchers.Default) { ready.engine.search(query.trim()) }
        val ids = a.results.map { it.photoId }
        val old = now?.results?.map { it.photoId }
        diff = if (old == null || lastQuery != query) "" else {
            val moved = ids.withIndex().count { (i, id) -> id in old && old.indexOf(id) != i }
            val new = ids.count { it !in old }; val gone = old.count { it !in ids }
            val flip = when { now?.weak == a.weak -> ""; a.weak -> "now “no strong match”"; else -> "now real matches (was “no strong match”)" }
            listOfNotNull(flip.ifEmpty { null }, if (moved + new + gone == 0) null else "$moved moved · $new new · $gone dropped")
                .joinToString(" · ").ifEmpty { "No change for this search." }
        }
        now = a; lastQuery = query
    }
    Text("Live preview", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Sample search") })
    now?.let { a ->
        Text((if (a.weak) "No strong match · closest: " else "${a.results.size} results · top 6:") + if (diff.isNotEmpty()) "   $diff" else "",
            style = MaterialTheme.typography.labelMedium, color = if (a.weak) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            a.results.take(6).forEach { r -> Box(Modifier.weight(1f)) { Thumb(r.photoId) } }
            repeat((6 - a.results.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
        }
    }
}

/** HNSW on/off, rebuild with the index knobs, and measure accuracy on your photos. */
@Composable
private fun IndexActions(ready: AppState.Ready) {
    val scope = rememberCoroutineScope()
    var hnsw by remember { mutableStateOf(ready.engine.useHnsw) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Use HNSW (off = brute force)", Modifier.weight(1f))
        Switch(hnsw, { on -> hnsw = on; busy = "Rebuilding…"; scope.launch { withContext(Dispatchers.Default) { ready.engine.reload(hnsw = on) }; busy = null } })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = busy == null, onClick = {
            scope.launch {
                busy = "Re-chunking…"
                val t0 = System.nanoTime()
                val chunks = withContext(Dispatchers.Default) { ready.engine.rebuild { d, n -> busy = "Re-chunking $d/$n…" } }
                result = "Rebuilt in %.1f s: %d text chunks, %s".format((System.nanoTime() - t0) / 1e9, chunks, ready.engine.indexStats())
                busy = null
            }
        }) { Text("Rebuild index") }
        OutlinedButton(enabled = busy == null, onClick = {
            busy = "Measuring…"
            scope.launch {
                val (recall, flatMs, idxMs) = withContext(Dispatchers.Default) {
                    ready.engine.measureIndex(listOf("a dog", "a beach", "food", "a car", "a document", "a screenshot", "people smiling", "a tree", "a building at night", "a cat", "a phone", "a birthday cake"))
                }
                result = "finds %.0f%% of the true top 10\nbrute force %.2f ms · ${if (hnsw) "HNSW" else "brute force"} %.2f ms per search".format(recall, flatMs, idxMs)
                busy = null
            }
        }) { Text("Measure") }
    }
    busy?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    result?.let { Mono(it) }
}

/** Lesson 1, live: two phrases → 384 numbers each → one similarity. Switch between ONNX Runtime and our hand-written MiniLM. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmbeddingCard(state: AppState, ready: AppState.Ready) {
    val handWritten by produceState<Embedder?>(null) { value = withContext(Dispatchers.IO) { runCatching { state.models.loadHandWrittenMiniLm() }.getOrNull() } }
    var useMine by rememberSaveable { mutableStateOf(false) }
    var a by rememberSaveable { mutableStateOf("red cap") } // not one of the homework pairs
    var b by rememberSaveable { mutableStateOf("red hat") }
    var out by remember { mutableStateOf<Triple<Embedder.Embedding, Embedder.Embedding, Float>?>(null) }
    val engine = if (useMine) handWritten ?: ready.minilm else ready.minilm
    LaunchedEffect(a, b, engine) {
        delay(200)
        out = withContext(Dispatchers.Default) { val x = engine.embed(a); val y = engine.embed(b); Triple(x, y, VectorMath.dot(x.vector, y.vector)) }
    }
    val pairs = listOf("blue round glasses" to "navy circular frames", "blue round glasses" to "blue round pizza", "I love these frames" to "I hate these frames", "chashma" to "glasses")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Embeddings (lesson 1)", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!useMine, { useMine = false }, label = { Text("ONNX Runtime") })
                FilterChip(useMine, { useMine = true }, enabled = handWritten != null, label = { Text(if (handWritten == null) "Hand-written (weights missing)" else "Hand-written Kotlin") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pairs.forEachIndexed { i, (x, y) -> AssistChip(onClick = { a = x; b = y }, label = { Text("Pair ${i + 1}") }) }
            }
            OutlinedTextField(a, { a = it }, Modifier.fillMaxWidth(), label = { Text("Phrase A") }, singleLine = true)
            OutlinedTextField(b, { b = it }, Modifier.fillMaxWidth(), label = { Text("Phrase B") }, singleLine = true)
            out?.let { (x, y, s) ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.2f".format(s), fontSize = 36.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Text("%.0f ms + %.0f ms".format(x.millis, y.millis), Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { s.coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Mono("A: " + x.tokens.joinToString(" "))
                Mono("B: " + y.tokens.joinToString(" "))
                Mono("A numbers: " + x.vector.take(5).joinToString(", ") { "%.3f".format(it) } + " … 384")
            }
        }
    }
}

