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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import kotlinx.coroutines.delay
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
            "Make a label like “my dog” or “office whiteboard”. It starts as plain search; tag ${state.knobs.minTags}+ photos and the phone trains a small model on your examples.",
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
                    else "Plain search for “a photo of $name”. Tag ${state.knobs.minTags - r.tagged.size} more to train.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (r.replay.size > 1) WatchItTrain(r)
                else if (r.loss.size > 1) LossChart(r.loss)
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

/**
 * Replays training pass by pass: the loss line draws itself while the top photos re-order as the one-layer model
 * learns from your tags. Tagged photos are outlined.
 */
@Composable
private fun WatchItTrain(r: Labels.Ranking) {
    var playing by remember { mutableStateOf(false) }
    var epoch by remember { mutableIntStateOf(r.replay.lastIndex) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        epoch = 0
        val step = (4000L / r.replay.size).coerceIn(40L, 400L)
        while (epoch < r.replay.lastIndex) { delay(step); epoch++ }
        playing = false
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (epoch == 0) "Before training (plain search)" else "After pass $epoch of ${r.loss.size} · loss %.3f".format(r.loss[epoch - 1]),
            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        FilledTonalButton({ playing = true }, enabled = !playing) { Text("▶ Watch it train") }
    }
    LossChart(r.loss.take(epoch.coerceAtLeast(2)))
    val primary = MaterialTheme.colorScheme.primary
    val row = rememberLazyListState()
    LaunchedEffect(epoch) { row.scrollToItem(0) } // keep #1 in view while photos swap places
    LazyRow(state = row, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(r.replay[epoch], key = { _, id -> id }) { i, id ->
            Box(Modifier.animateItem().size(43.dp)) {
                Thumb(id, if (id in r.tagged) Modifier.border(3.dp, primary) else Modifier)
                Text(" ${i + 1} ", Modifier.background(primary), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
