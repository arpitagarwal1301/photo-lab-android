package dev.spikeai.photos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.spikeai.photos.AppState
import dev.spikeai.photos.data.Labels
import dev.spikeai.search.LinearProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LabelsScreen(state: AppState, ready: AppState.Ready) {
    var labels by remember { mutableStateOf(state.store.labels()) }
    var open by remember { mutableStateOf<Pair<Long, String>?>(null) }
    open?.let { (id, name) -> return LabelDetail(state, ready, id, name) { open = null; labels = state.store.labels() } }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personal labels", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Make a label like “my dog” or “office whiteboard”. It starts as plain search; tag ${LinearProbe.MIN_EXAMPLES}+ photos and the phone trains a small model on your examples.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("New label") }, singleLine = true)
            Button(enabled = name.isNotBlank(), onClick = { state.store.addLabel(name); name = ""; labels = state.store.labels() }) { Text("Add") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels, key = { it.first }) { (id, n) ->
                Card(Modifier.fillMaxWidth().clickable { open = id to n }) {
                    Text("$n  ·  ${state.store.tagged(id).size} tagged", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LabelDetail(state: AppState, ready: AppState.Ready, id: Long, name: String, onBack: () -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var ranking by remember { mutableStateOf<Labels.Ranking?>(null) }
    var detail by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(version) { ranking = withContext(Dispatchers.Default) { ready.labels.rank(id, name) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("← Labels") }
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton({ state.store.deleteLabel(id); onBack() }) { Text("Delete") }
        }
        ranking?.let { r ->
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (r.trained) "Trained on your ${r.tagged.size} tagged photos in %.0f ms (one layer, %d passes of backprop)".format(r.trainMs, r.loss.size)
                    else "Plain search for “a photo of $name”. Tag ${LinearProbe.MIN_EXAMPLES - r.tagged.size} more to train.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (r.loss.size > 1) LossChart(r.loss)
                Text("Tap to tag / untag · long-press to open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val primary = MaterialTheme.colorScheme.primary
            LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(r.photos.take(300), key = { it.first }) { (pid, _) ->
                    val tagged = pid in r.tagged
                    Box(Modifier.combinedClickable(onClick = { state.store.setTag(id, pid, !tagged); version++ }, onLongClick = { detail = pid })) {
                        Thumb(pid, if (tagged) Modifier.border(4.dp, primary) else Modifier)
                        if (tagged) Text(" ✓ ", Modifier.align(Alignment.TopEnd), color = primary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    detail?.let { PhotoDetail(state, it, emptyList()) { detail = null; version++ } }
}

/** Training loss per epoch: watching it fall is watching the model learn. */
@Composable
private fun LossChart(loss: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    Text("Loss per pass over your examples: %.3f → %.3f".format(loss.first(), loss.last()), style = MaterialTheme.typography.labelSmall)
    Canvas(Modifier.fillMaxWidth().height(48.dp).padding(vertical = 4.dp)) {
        val max = loss.max(); val min = loss.min(); val span = (max - min).coerceAtLeast(1e-6f)
        for (i in 1 until loss.size) {
            fun pt(j: Int) = Offset(size.width * j / (loss.size - 1), size.height * (1 - (loss[j] - min) / span))
            drawLine(color, pt(i - 1), pt(i), strokeWidth = 4f)
        }
    }
}
