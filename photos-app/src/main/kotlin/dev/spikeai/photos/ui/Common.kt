package dev.spikeai.photos.ui

import android.content.ContentUris
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.spikeai.photos.data.Indexer
import dev.spikeai.photos.data.Models
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val thumbCache = LruCache<Long, ImageBitmap>(400)

fun photoUri(id: Long) = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

@Composable
fun Thumb(id: Long, modifier: Modifier = Modifier, px: Int = 320) {
    val context = LocalContext.current
    val key = id * 10 + px / 320
    val bmp by produceState(thumbCache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(photoUri(id), Size(px, px), null).asImageBitmap() }.getOrNull()
        }?.also { thumbCache.put(key, it) }
    }
    Box(modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
fun ProgressBanner(p: Indexer.Progress) {
    if (p.finished || p.total == 0) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            "${p.phase}: ${p.done} / ${p.total}" + if (p.msPerPhoto > 0) "  ·  %.0f ms each".format(p.msPerPhoto) else "",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { p.done.toFloat() / p.total }, Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

@Composable
fun CenterMessage(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(20.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun PermissionScreen(onAsk: () -> Unit) = CenterMessage(
    "Let Photo Lab see your photos",
    "Everything runs on this phone: photos are turned into numbers here and never uploaded. You can pick only some photos if you prefer.",
    "Allow photo access", onAsk,
)

@Composable
fun LoadingScreen(error: String?) {
    if (error != null) CenterMessage("Couldn't load the models", error) else
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Loading three neural networks…")
            }
        }
}

@Composable
fun SetupScreen(models: Models, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val mb = models.missing().sumOf { it.approxMb }
    CenterMessage(
        "One-time download",
        "Photo Lab needs three small neural networks (about $mb MB) from Hugging Face. After that it works fully offline.\n\n" + (status ?: ""),
        if (busy) null else "Download now",
    ) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { models.download { f, done, total -> status = "$f  ${done / 1_000_000} / ${total / 1_000_000} MB" } }
            }
            busy = false
            ok.onSuccess { onDone() }.onFailure { status = "Failed: ${it.message}. Check the connection and try again." }
        }
    }
}
