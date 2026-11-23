package dev.spikeai.photos.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.Embedder
import dev.spikeai.embed.ImagePrep
import dev.spikeai.embed.VectorMath
import dev.spikeai.search.Chunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two passes over the gallery, newest photos first, resumable (only new/changed photos are processed):
 *   pass 1 — every photo → CLIP vector (search works as soon as this starts)
 *   pass 2 — read text with ML Kit OCR, most "texty" photos first → chunks → MiniLM vectors
 */
class Indexer(
    private val context: Context,
    private val store: PhotoStore,
    private val clip: ClipEncoder,
    private val minilm: Embedder,
    private val engine: SearchEngine,
) {
    data class Progress(val phase: String, val done: Int, val total: Int, val msPerPhoto: Double, val finished: Boolean = false)

    private val _progress = MutableStateFlow(Progress("Waiting", 0, 0, 0.0))
    val progress: StateFlow<Progress> = _progress

    /** CLIP decides which photos probably contain text, so OCR starts with screenshots, receipts, documents. */
    private val textPrompts by lazy { listOf("a screenshot of a phone", "a photo of a document", "a receipt", "a page of text", "a sign with writing").map(clip::embedText) }
    private val photoPrompts by lazy { listOf("a photo of people", "a photo of nature", "a photo of food", "a photo of an animal", "a photo of a street").map(clip::embedText) }

    suspend fun run() = withContext(Dispatchers.Default) { runOnWorker() } // never on the UI thread

    private suspend fun runOnWorker() {
        val resolver = context.contentResolver
        val uriBase = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        data class Media(val id: Long, val modified: Long, val taken: Long)
        val media = resolver.query(
            uriBase,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN),
            null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { c -> buildList { while (c.moveToNext()) add(Media(c.getLong(0), c.getLong(1), c.getLong(2))) } } ?: emptyList()

        val known = store.modifiedTimes()
        val gone = known.keys - media.map { it.id }.toSet()
        if (gone.isNotEmpty()) { store.deletePhotos(gone); engine.reload() }
        val todo = media.filter { known[it.id] != it.modified }
        val changed = todo.any { it.id in known }

        val t0 = System.nanoTime()
        todo.forEachIndexed { i, m ->
            coroutineContext.ensureActive()
            val uri = ContentUris.withAppendedId(uriBase, m.id)
            runCatching {
                val bmp = resolver.loadThumbnail(uri, Size(512, 512), null)
                val vec = clip.embedImage(ImagePrep.toChw(ImagePrep.resizeShortSideAndCrop(bmp.pixels(), bmp.width, bmp.height)))
                bmp.recycle()
                val textScore = textPrompts.maxOf { VectorMath.dot(vec, it) } - photoPrompts.maxOf { VectorMath.dot(vec, it) }
                store.putPhoto(m.id, m.modified, m.taken, vec, textScore)
                if (!changed) engine.addPhoto(m.id, vec)
            }
            _progress.value = Progress("Understanding photos", i + 1, todo.size, (System.nanoTime() - t0) / 1e6 / (i + 1))
        }
        if (changed) engine.reload()
        if (todo.isNotEmpty()) Log.i(TAG, "CLIP pass: %d photos, %.0f ms each (thumbnail + resize + network)".format(todo.size, (System.nanoTime() - t0) / 1e6 / todo.size))

        val queue = store.ocrQueue()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val chunker = Chunker(engine.knobs.chunkWords, engine.knobs.chunkOverlap) // Lab knobs
        val t1 = System.nanoTime()
        queue.forEachIndexed { i, id ->
            coroutineContext.ensureActive()
            val uri = ContentUris.withAppendedId(uriBase, id)
            runCatching {
                val bmp = resolver.loadThumbnail(uri, Size(1280, 1280), null)
                val text = suspendCancellableCoroutine { cont ->
                    recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { cont.resume(it.text) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                bmp.recycle()
                val chunks = chunker.chunk(text).map { it to minilm.embed(it).vector }
                store.putOcr(id, text, chunks)
                engine.addOcr(id, text, chunks)
            }.onFailure { store.putOcr(id, "", emptyList()) }
            _progress.value = Progress("Reading text in photos", i + 1, queue.size, (System.nanoTime() - t1) / 1e6 / (i + 1))
        }
        recognizer.close()
        if (queue.isNotEmpty()) Log.i(TAG, "OCR pass: %d photos, %.0f ms each (OCR + chunk + MiniLM), %d chunks total".format(queue.size, (System.nanoTime() - t1) / 1e6 / queue.size, engine.chunkCount))
        _progress.value = Progress("Up to date", media.size, media.size, 0.0, finished = true)
    }

    private companion object { const val TAG = "PhotoLab" }

    private fun Bitmap.pixels(): IntArray {
        val b = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)
        return IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    }
}
