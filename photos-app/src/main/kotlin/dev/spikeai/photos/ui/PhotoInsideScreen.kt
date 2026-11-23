package dev.spikeai.photos.ui

import android.graphics.Bitmap
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.spikeai.embed.ImagePrep
import dev.spikeai.photos.AppState
import dev.spikeai.photos.data.PhotoTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Inside this photo": what the app computed and stored for one photo, from pixels to index. */
@Composable
fun PhotoInsideScreen(ready: AppState.Ready, photoId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val trace by produceState<PhotoTrace?>(null, photoId) { value = withContext(Dispatchers.Default) { ready.engine.photoTrace(photoId) } }
    // The exact 256×256 square the network saw: the same thumbnail + resize + centre crop the indexer used.
    val crop by produceState<ImageBitmap?>(null, photoId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bmp = context.contentResolver.loadThumbnail(photoUri(photoId), Size(512, 512), null)
                val b = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
                val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
                Bitmap.createBitmap(ImagePrep.resizeShortSideAndCrop(px, b.width, b.height), ImagePrep.SIZE, ImagePrep.SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClose) { Text("← Back") }
                    Text("Inside this photo", style = MaterialTheme.typography.titleLarge)
                }
                val t = trace
                if (t == null) { CircularProgressIndicator(Modifier.padding(32.dp)); return@Column }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Section {
                        StepTitle(1, "What the network sees", "The photo is shrunk so its short side is 256 pixels, then the centre square is cut out. That 256×256 square, 196,608 numbers (3 colours × 256 × 256), goes into MobileCLIP.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Thumb(photoId, px = 640); Text("your photo", style = MaterialTheme.typography.labelSmall) }
                            Column(Modifier.weight(1f)) {
                                crop?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(1f)) }
                                Text("what CLIP sees (256×256)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Section {
                        StepTitle(2, "Pixels → 512 numbers", "MobileCLIP's image network turns the square into one vector. Your search words are turned into 512 numbers by its text network, so they can be compared directly.")
                        VectorStrip(t.clipVec, rememberReveal(1200))
                        Mono(t.clipVec.take(5).joinToString(", ") { "%.3f".format(it) } + " …")
                    }
                    Section {
                        StepTitle(3, "What CLIP thinks it is", "Compare the photo's vector with some generic descriptions. Share = CLIP's own softmax over these guesses (similarity × 100).")
                        val p = rememberReveal(1200)
                        val max = t.guesses.maxOf { it.second } * 1.1f
                        t.guesses.forEach { (name, sim, share) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium)
                                ScoreBar(sim, max, progress = p, modifier = Modifier.weight(1f))
                                Text("%.2f · %2.0f%%".format(sim, share * 100), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Section {
                        StepTitle(4, "Text read from it (ML Kit OCR)", "Google's on-device text recognizer reads any text in the photo, at 1280 pixels so small print survives.")
                        Text(when { t.ocrText == null -> "Not read yet (OCR is still running)."; t.ocrText.isBlank() -> "No text found."; else -> t.ocrText },
                            style = MaterialTheme.typography.bodySmall, maxLines = 12)
                    }
                    if (t.chunks.isNotEmpty()) Section {
                        StepTitle(5, "Cut into chunks → 384 numbers each", "The text is cut into pieces of up to ${t.chunkWords} words; the last ${t.chunkOverlap} words of one chunk start the next (highlighted). MiniLM turns each chunk into 384 numbers. One vector per chunk keeps each meaning clear.")
                        var prev: List<String> = emptyList()
                        t.chunks.forEachIndexed { i, (text, vec) ->
                            val words = text.split(" ")
                            val shared = (minOf(prev.size, words.size) downTo 0).first { k -> prev.takeLast(k) == words.take(k) }
                            prev = words
                            Text("chunk ${i + 1} · ${words.size} words", style = MaterialTheme.typography.labelLarge)
                            Text(buildAnnotatedString {
                                withStyle(SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer)) { append(words.take(shared).joinToString(" ")) }
                                if (shared > 0) append(" ")
                                append(words.drop(shared).joinToString(" "))
                            }, style = MaterialTheme.typography.bodySmall)
                            VectorStrip(vec, rememberReveal(900, 200 * i), Modifier.padding(bottom = 6.dp))
                        }
                    }
                    Section {
                        StepTitle(6, "Its place in the vector index", if (t.layers > 1) (if (t.level == 0) "In the HNSW graph (${t.layers} layers) this photo lives only on layer 0, like most photos." else "In the HNSW graph this photo lives on layers 0 to ${t.level} of ${t.layers}: it is one of the few “motorway” photos searches start from.") + " Each layer links it to its nearest neighbours." else "Index is brute force (switch HNSW on in the Lab).")
                        Text("Its nearest photos by CLIP vector:", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            t.neighbours.take(4).forEach { (id, sim) -> Column(Modifier.weight(1f)) { Thumb(id); Text("%.2f".format(sim), style = MaterialTheme.typography.labelSmall) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { content() } }
}
