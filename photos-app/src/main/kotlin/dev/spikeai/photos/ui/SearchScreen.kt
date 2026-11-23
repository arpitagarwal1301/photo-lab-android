package dev.spikeai.photos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.spikeai.photos.AppState
import dev.spikeai.photos.data.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SUGGESTIONS = listOf("beach at sunset", "wifi password", "receipt total", "my handwriting", "a dog", "screenshot of a chat", "food on a plate", "train ticket")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: AppState, ready: AppState.Ready) {
    val progress by ready.indexer.progress.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchEngine.Result>>(emptyList()) }
    var timing by remember { mutableStateOf<SearchEngine.Timing?>(null) }
    var weak by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    // Keyed grids keep the old first photo in view when results re-order; always show the new best match.
    LaunchedEffect(results) { grid.scrollToItem(0) }
    var open by remember { mutableStateOf<SearchEngine.Result?>(null) }
    val focus = LocalFocusManager.current
    // Re-run when the query changes, and refresh every few hundred photos while indexing.
    val bucket = progress.done / 200
    LaunchedEffect(query, bucket, progress.finished) {
        delay(250)
        val answer = withContext(Dispatchers.Default) {
            if (query.isBlank()) SearchEngine.Answer(state.store.photos().take(120).map { SearchEngine.Result(it.id, 0f, emptyList()) }, false)
            else ready.engine.search(query.trim())
        }
        results = answer.results; weak = answer.weak
        timing = if (query.isBlank()) null else ready.engine.lastTiming
    }
    Column(Modifier.fillMaxSize()) {
        ProgressBanner(progress)
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            placeholder = { Text("Describe a photo, or text inside it") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        if (query.isBlank()) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTIONS.forEach { s -> AssistChip(onClick = { query = s }, label = { Text(s) }) }
            }
        } else timing?.let { t ->
            if (weak) Text(
                "No strong match for “${query.trim()}”. These are only the closest-looking photos.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
            Text(
                ((if (weak) "%d closest" else "%d results") + " in %.0f ms  ·  CLIP %.0f  ·  MiniLM %.0f  ·  index %.1f  ·  keywords %.1f").format(results.size, t.total, t.clipMs, t.miniLmMs, t.indexMs, t.bm25Ms),
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(GridCells.Adaptive(110.dp), Modifier.fillMaxSize().padding(top = 8.dp), state = grid, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(results, key = { it.photoId }) { r -> Thumb(r.photoId, Modifier.clickable { open = r }) }
        }
    }
    open?.let { r -> PhotoDetail(state, r.photoId, r.why) { open = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoDetail(state: AppState, photoId: Long, why: List<String>, onClose: () -> Unit) {
    var refresh by remember { mutableStateOf(0) }
    val info by produceState<Pair<String?, List<Triple<Long, String, Boolean>>>?>(null, photoId, refresh) {
        value = withContext(Dispatchers.IO) {
            val text = state.store.photos().firstOrNull { it.id == photoId }?.ocrText
            text to state.store.labels().map { (id, name) -> Triple(id, name, photoId in state.store.tagged(id)) }
        }
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Thumb(photoId, Modifier.fillMaxWidth(), px = 1080)
                if (why.isNotEmpty()) {
                    Text("Why it matched", style = MaterialTheme.typography.titleSmall)
                    why.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                info?.let { (text, labels) ->
                    if (labels.isNotEmpty()) {
                        Text("Labels (tap to tag)", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            labels.forEach { (id, name, on) ->
                                FilterChip(on, onClick = { state.store.setTag(id, photoId, !on); refresh++ }, label = { Text(name) })
                            }
                        }
                    }
                    Text("Text found in the photo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when { text == null -> "Not read yet (OCR is still running)"; text.isBlank() -> "No text"; else -> text },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClose) { Text("Close") }
            }
        }
    }
}
