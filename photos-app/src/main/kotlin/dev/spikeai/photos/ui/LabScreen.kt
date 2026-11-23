package dev.spikeai.photos.ui

import androidx.compose.foundation.layout.Arrangement
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

/** The learning corner: see the numbers, turn the knobs. */
@Composable
fun LabScreen(state: AppState, ready: AppState.Ready) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Lab", style = MaterialTheme.typography.headlineSmall)
        IndexCard(state, ready)
        KnobCard(ready)
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

@Composable
private fun KnobCard(ready: AppState.Ready) {
    val scope = rememberCoroutineScope()
    var hnsw by remember { mutableStateOf(ready.engine.useHnsw) }
    var ef by remember { mutableFloatStateOf(ready.engine.ef.toFloat()) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vector index knobs", style = MaterialTheme.typography.titleMedium)
            Text("HNSW hops through a graph instead of checking every photo. Higher ef = looks around more = more accurate, slower.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use HNSW", Modifier.weight(1f))
                Switch(hnsw, { on -> hnsw = on; busy = true; scope.launch { withContext(Dispatchers.Default) { ready.engine.reload(hnsw = on) }; busy = false } })
            }
            if (hnsw) {
                Text("ef = ${ef.toInt()}")
                Slider(ef, { ef = it }, valueRange = 8f..256f, onValueChangeFinished = { ready.engine.ef = ef.toInt() })
            }
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val (recall, flatMs, idxMs) = withContext(Dispatchers.Default) {
                        ready.engine.measureIndex(listOf("a dog", "a beach", "food", "a car", "a document", "a screenshot", "people smiling", "a tree", "a building at night", "a cat", "a phone", "a birthday cake"))
                    }
                    result = "finds %.0f%% of the true top 10\nbrute force %.2f ms · ${if (hnsw) "HNSW" else "brute force"} %.2f ms per search".format(recall, flatMs, idxMs)
                    busy = false
                }
            }) { Text(if (busy) "Working…" else "Measure on my photos") }
            result?.let { Mono(it) }
        }
    }
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

@Composable
private fun Mono(s: String) = Text(s, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
