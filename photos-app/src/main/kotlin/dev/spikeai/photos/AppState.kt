package dev.spikeai.photos

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.Embedder
import dev.spikeai.photos.data.Indexer
import dev.spikeai.photos.data.Labels
import dev.spikeai.photos.data.Models
import dev.spikeai.photos.data.PhotoStore
import dev.spikeai.photos.data.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the screens need, created once. Networks load off the main thread (a few seconds). */
class AppState(context: Context) {
    val app: Context = context.applicationContext
    val models = Models(app)
    val store = PhotoStore(app)

    class Ready(
        val clip: ClipEncoder,
        val minilm: Embedder,
        val engine: SearchEngine,
        val indexer: Indexer,
        val labels: Labels,
        val loadMs: Long,
    )

    var ready by mutableStateOf<Ready?>(null); private set
    var loadError by mutableStateOf<String?>(null); private set

    suspend fun load() = withContext(Dispatchers.IO) {
        if (ready != null) return@withContext
        runCatching {
            val t0 = System.currentTimeMillis()
            val clip = models.loadClip()
            val minilm = models.loadMiniLm()
            val engine = SearchEngine(store, clip, minilm).apply { reload() }
            Ready(clip, minilm, engine, Indexer(app, store, clip, minilm, engine), Labels(store, engine, clip), System.currentTimeMillis() - t0)
        }.onSuccess { ready = it }.onFailure { loadError = it.toString() }
    }
}
