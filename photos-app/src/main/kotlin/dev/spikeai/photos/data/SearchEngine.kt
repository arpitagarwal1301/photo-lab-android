package dev.spikeai.photos.data

import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.Embedder
import dev.spikeai.search.Bm25
import dev.spikeai.search.FlatIndex
import dev.spikeai.search.Fusion
import dev.spikeai.search.Hit
import dev.spikeai.search.Hnsw
import dev.spikeai.search.VectorIndex

/**
 * Hybrid search over three signals, then Reciprocal Rank Fusion:
 *   1. CLIP:   query text ↔ photo pixels      "beach at sunset"   (what the photo LOOKS like)
 *   2. MiniLM: query ↔ chunks of text in photo  "wifi password"    (what the text in it MEANS)
 *   3. BM25:   query words ↔ text in photo      "INV-2291"         (exact words and numbers)
 */
class SearchEngine(private val store: PhotoStore, private val clip: ClipEncoder, private val minilm: Embedder) {

    class Result(val photoId: Long, val score: Float, val why: List<String>)
    /** [weak] = nothing cleared any bar; [results] are then just the closest-looking photos. */
    class Answer(val results: List<Result>, val weak: Boolean)
    class Timing(val clipMs: Double, val miniLmMs: Double, val indexMs: Double, val bm25Ms: Double, val total: Double)

    private val lock = Any()
    private val photoIds = ArrayList<Long>()          // index position → MediaStore id
    private val position = HashMap<Long, Int>()
    private val photoVecs = ArrayList<FloatArray>()
    private lateinit var photoIndex: VectorIndex
    private lateinit var chunkIndex: VectorIndex
    private val chunkPhoto = ArrayList<Int>()        // chunk position → photo position
    private val chunkText = ArrayList<String>()
    private var bm25 = Bm25()

    var useHnsw = true; private set
    var ef = 64
        set(v) { field = v; synchronized(lock) { (photoIndex as? Hnsw)?.ef = v; (chunkIndex as? Hnsw)?.ef = v } }
    var lastTiming: Timing? = null; private set
    val photoCount get() = synchronized(lock) { photoIds.size }
    val chunkCount get() = synchronized(lock) { chunkText.size }

    fun reload(hnsw: Boolean = useHnsw) = synchronized(lock) {
        useHnsw = hnsw
        photoIds.clear(); position.clear(); photoVecs.clear(); chunkPhoto.clear(); chunkText.clear()
        photoIndex = newIndex(); chunkIndex = newIndex(); bm25 = Bm25()
        val photos = store.photos()
        photos.forEach { addPhotoLocked(it.id, it.clip); it.ocrText?.let { t -> if (t.isNotBlank()) bm25.add(position.getValue(it.id), t) } }
        store.chunks().forEach { c -> position[c.photoId]?.let { addChunkLocked(it, c.text, c.vec) } }
    }

    private fun newIndex(): VectorIndex = if (useHnsw) Hnsw(m = 16, efConstruction = 100, ef = ef) else FlatIndex()

    fun addPhoto(id: Long, vec: FloatArray) = synchronized(lock) { if (id !in position) addPhotoLocked(id, vec) }

    fun addOcr(photoId: Long, text: String, chunks: List<Pair<String, FloatArray>>) = synchronized(lock) {
        val p = position[photoId] ?: return
        if (text.isNotBlank()) bm25.add(p, text)
        chunks.forEach { (t, v) -> addChunkLocked(p, t, v) }
    }

    private fun addPhotoLocked(id: Long, vec: FloatArray) {
        position[id] = photoIds.size; photoIndex.add(photoIds.size, vec); photoIds += id; photoVecs += vec
    }

    private fun addChunkLocked(photoPos: Int, text: String, vec: FloatArray) {
        chunkIndex.add(chunkText.size, vec); chunkPhoto += photoPos; chunkText += text
    }

    fun vectorOf(photoId: Long): FloatArray? = synchronized(lock) { position[photoId]?.let { photoVecs[it] } }
    fun allVectors(): List<Pair<Long, FloatArray>> = synchronized(lock) { photoIds.indices.map { photoIds[it] to photoVecs[it] } }

    fun search(query: String, limit: Int = 60): Answer {
        val t0 = System.nanoTime()
        val qClip = clip.embedText(query)
        val t1 = System.nanoTime()
        val qText = minilm.embed(query).vector
        // Skip guesses that mean the same as the query ("an animal" when searching "a dog").
        val others = otherGuesses.filter { (_, v) -> dot(qClip, v) < SAME_AS_QUERY }
        val t2 = System.nanoTime()
        synchronized(lock) {
            // Nearest photos always exist, even for "update" when no photo is about updates. So for each candidate ask
            // "what else could this photo be?": keep it only if the query describes it better than every other guess.
            val closest = photoIndex.search(qClip, 100)
            val rival = closest.associate { it.id to bestOtherGuess(photoVecs[it.id], others) }
            val byLook = closest.filter { it.score > rival.getValue(it.id).second }
            // Nearest chunks always exist, even when unrelated — keep only clearly similar text.
            val byMeaning = chunkIndex.search(qText, 100).filter { it.score > MIN_TEXT_SIMILARITY }
                .map { Hit(chunkPhoto[it.id], it.score) to chunkText[it.id] }
                .distinctBy { it.first.id }
            val t3 = System.nanoTime()
            val byWords = bm25.search(query, 100)
            val t4 = System.nanoTime()
            val weak = byLook.isEmpty() && byMeaning.isEmpty() && byWords.isEmpty()
            val fused = if (weak) closest.take(limit) else Fusion.rrf(listOf(byLook, byMeaning.map { it.first }, byWords), limit = limit)
            val lookRank = byLook.withIndex().associate { (r, h) -> h.id to r + 1 }
            val lookScore = closest.associate { it.id to it.score }
            val meaning = byMeaning.associate { (h, t) -> h.id to t }
            val words = byWords.map { it.id }.toSet()
            lastTiming = Timing((t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6, (t4 - t0) / 1e6)
            android.util.Log.i("PhotoLab", "search '%s': %.1f ms (CLIP text %.1f, MiniLM %.1f, vector index %.2f, BM25 %.2f), %d results".format(query, lastTiming!!.total, lastTiming!!.clipMs, lastTiming!!.miniLmMs, lastTiming!!.indexMs, lastTiming!!.bm25Ms, fused.size))
            android.util.Log.i("PhotoLab", "  look: %d of %d candidates beat the other guesses%s; best %s".format(byLook.size, closest.size, if (weak) " → WEAK, showing closest" else "", closest.firstOrNull()?.let { "%.3f vs '%s' %.3f".format(it.score, rival.getValue(it.id).first, rival.getValue(it.id).second) }))
            android.util.Log.i("PhotoLab", "  look top3=" + byLook.take(3).map { photoIds[it.id] } + " meaning top3=" + byMeaning.take(3).map { photoIds[it.first.id] to "%.2f".format(it.first.score) } + " words top3=" + byWords.take(3).map { photoIds[it.id] to "%.2f".format(it.score) } + " fused top3=" + fused.take(3).map { photoIds[it.id] to "%.4f".format(it.score) })
            return Answer(fused.map { h ->
                Result(photoIds[h.id], h.score, buildList {
                    val look = lookScore[h.id]?.let { s -> rival[h.id]?.let { (name, r) -> "%.2f; next best guess “%s” %.2f".format(s, name, r) } }
                    lookRank[h.id]?.let { add("looks like it (#$it, $look)") }
                    if (weak && look != null) add("closest look, but weak ($look)")
                    meaning[h.id]?.let { add("text means it: “${it.take(60)}”") }
                    if (h.id in words) add("contains the words")
                })
            }, weak)
        }
    }

    /** The generic description that fits this photo best, and its score. */
    private fun bestOtherGuess(photo: FloatArray, others: List<Pair<String, FloatArray>>): Pair<String, Float> =
        others.map { (name, v) -> name to dot(photo, v) }.maxByOrNull { it.second } ?: ("" to -1f)

    private fun dot(a: FloatArray, b: FloatArray): Float { var d = 0f; for (k in a.indices) d += a[k] * b[k]; return d }

    /** The other guesses, encoded once (18 × CLIP text ≈ 0.5 s on the phone). Call at start-up so the first search is fast. */
    private val otherGuesses: List<Pair<String, FloatArray>> by lazy { OTHER_GUESSES.map { it to clip.embedText(it) } }
    fun warmUp() { otherGuesses.size }

    /** Lab: how many of the true top 10 (brute force) does the index return, and how fast. */
    fun measureIndex(queries: List<String>): Triple<Double, Double, Double> {
        val qs = queries.map { clip.embedText(it) }
        synchronized(lock) {
            val flat = FlatIndex(); photoVecs.forEachIndexed { i, v -> flat.add(i, v) }
            var found = 0; var flatNs = 0L; var idxNs = 0L
            for (q in qs) {
                val a = System.nanoTime(); val truth = flat.search(q, 10).map { it.id }.toSet(); flatNs += System.nanoTime() - a
                val b = System.nanoTime(); found += photoIndex.search(q, 10).count { it.id in truth }; idxNs += System.nanoTime() - b
            }
            val n = qs.size.coerceAtLeast(1)
            return Triple(100.0 * found / (n * 10).coerceAtLeast(1), flatNs / 1e6 / n, idxNs / 1e6 / n)
        }
    }

    companion object {
        /** MiniLM similarity below this is "vaguely the same topic" (0.35 let unrelated screenshots in on the phone). */
        const val MIN_TEXT_SIMILARITY = 0.45f
        /**
         * Generic descriptions a photo competes against. A photo counts as "looks like it" only if the query scores
         * higher than all of these. Measured (COCO 1k + 51 phone photos):
         *  - full captions: the right photo is kept 98% of the time
         *  - short queries ("a dog", "pizza"): 3× fewer wrong photos than a "beat the gallery median" rule
         *  - phone: "a dog" and "update" find nothing (correct); "eyeglasses" finds 37 (median rule found 0,
         *    because most photos there are glasses and the median was already high)
         */
        val OTHER_GUESSES = listOf(
            "a screenshot of a phone app", "a screenshot of a web page", "a screenshot of a chat", "a screenshot of a settings menu",
            "a document with text", "a photo of a person", "a selfie", "a group of people", "a photo of food", "a room indoors",
            "a city street", "a landscape", "a building", "a car", "an animal", "a product photo", "a blurry photo", "a photo of an object",
        )
        /** CLIP text similarity above which a guess means the same as the query and is skipped. */
        const val SAME_AS_QUERY = 0.8f
    }

    fun indexStats(): String = synchronized(lock) { (photoIndex as? Hnsw)?.stats() ?: "brute force" }
}
