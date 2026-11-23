package dev.spikeai.photos.data

import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.Embedder
import dev.spikeai.search.Bm25
import dev.spikeai.search.Chunker
import dev.spikeai.search.FlatIndex
import dev.spikeai.search.Fusion
import dev.spikeai.search.Hit
import dev.spikeai.search.Hnsw
import dev.spikeai.search.HnswTrace
import dev.spikeai.search.VectorIndex
import kotlin.math.exp

/**
 * Hybrid search over three signals, then Reciprocal Rank Fusion:
 *   1. CLIP:   query text ↔ photo pixels      "beach at sunset"   (what the photo LOOKS like)
 *   2. MiniLM: query ↔ chunks of text in photo  "wifi password"    (what the text in it MEANS)
 *   3. BM25:   query words ↔ text in photo      "INV-2291"         (exact words and numbers)
 * Every tunable number comes from [knobs], read on each search, so the Lab can change them live.
 */
class SearchEngine(private val store: PhotoStore, private val clip: ClipEncoder, private val minilm: Embedder, val knobs: Knobs) {

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

    private fun newIndex(): VectorIndex = if (useHnsw) Hnsw(m = knobs.hnswM, efConstruction = knobs.efConstruction, ef = knobs.ef) else FlatIndex()

    /** Apply the index knobs: re-cut stored OCR text into chunks (if the chunk knobs changed), re-embed, rebuild both indexes. */
    fun rebuild(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val chunker = Chunker(knobs.chunkWords, knobs.chunkOverlap)
        val photos = store.photos().filter { !it.ocrText.isNullOrBlank() }
        photos.forEachIndexed { i, p ->
            store.putOcr(p.id, p.ocrText!!, chunker.chunk(p.ocrText).map { it to minilm.embed(it).vector })
            onProgress(i + 1, photos.size)
        }
        reload()
        return chunkCount
    }

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

    fun search(query: String, limit: Int = 60): Answer = run(query, limit, traced = false).first

    /** The same search, plus every intermediate step for the "inside this search" screen. */
    fun trace(query: String, limit: Int = 60): SearchTrace = run(query, limit, traced = true).second!!

    private fun run(query: String, limit: Int, traced: Boolean): Pair<Answer, SearchTrace?> {
        val k = knobs
        val t0 = System.nanoTime()
        val clipEnc = clip.tokenizer.encode(query)
        val qClip = clip.embedText(query)
        val t1 = System.nanoTime()
        val qText = minilm.embed(query).vector
        val t2 = System.nanoTime()
        // Skip guesses that mean the same as the query ("an animal" when searching "a dog").
        val others = otherGuesses.filter { (_, v) -> dot(qClip, v) < k.sameAsQuery }
        synchronized(lock) {
            (photoIndex as? Hnsw)?.ef = k.ef; (chunkIndex as? Hnsw)?.ef = k.ef
            val hnswTrace = if (traced) HnswTrace() else null
            // Nearest photos always exist, even for "update" when no photo is about updates. So for each candidate ask
            // "what else could this photo be?": keep it only if the query describes it better than every other guess.
            val closest = (photoIndex as? Hnsw)?.search(qClip, 100, hnswTrace) ?: photoIndex.search(qClip, 100)
            val rival = closest.associate { it.id to bestOtherGuess(photoVecs[it.id], others) }
            val lookKept = if (k.otherGuesses) closest.filter { it.score > rival.getValue(it.id).second } else closest
            // Nearest chunks always exist too — keep only clearly similar text.
            val allChunks = chunkIndex.search(qText, 100)
            val meaningKept = allChunks.filter { it.score > k.minTextSimilarity }
                .map { Hit(chunkPhoto[it.id], it.score) to chunkText[it.id] }
                .distinctBy { it.first.id }
            val t3 = System.nanoTime()
            bm25.k1 = k.bm25K1; bm25.b = k.bm25B
            val byWords = bm25.search(query, 100)
            val t4 = System.nanoTime()
            val byLook = if (k.useLook) lookKept else emptyList()
            val byMeaning = if (k.useMeaning) meaningKept else emptyList()
            val words = if (k.useWords) byWords else emptyList()
            val weak = byLook.isEmpty() && byMeaning.isEmpty() && words.isEmpty()
            val lists = listOf(byLook, byMeaning.map { it.first }, words)
            val fused = if (weak) closest.take(limit) else Fusion.rrf(lists, k = k.rrfK, limit = limit)
            val lookRank = byLook.withIndex().associate { (r, h) -> h.id to r + 1 }
            val lookScore = closest.associate { it.id to it.score }
            val meaning = byMeaning.associate { (h, t) -> h.id to t }
            val wordIds = words.map { it.id }.toSet()
            val timing = Timing((t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6, (t4 - t0) / 1e6)
            lastTiming = timing
            android.util.Log.i("PhotoLab", "search '%s': %.1f ms (CLIP text %.1f, MiniLM %.1f, vector index %.2f, BM25 %.2f), %d results".format(query, timing.total, timing.clipMs, timing.miniLmMs, timing.indexMs, timing.bm25Ms, fused.size))
            android.util.Log.i("PhotoLab", "  look: %d of %d candidates beat the other guesses%s; best %s".format(lookKept.size, closest.size, if (weak) " → WEAK, showing closest" else "", closest.firstOrNull()?.let { "%.3f vs '%s' %.3f".format(it.score, rival.getValue(it.id).first, rival.getValue(it.id).second) }))
            android.util.Log.i("PhotoLab", "  look top3=" + byLook.take(3).map { photoIds[it.id] } + " meaning top3=" + byMeaning.take(3).map { photoIds[it.first.id] to "%.2f".format(it.first.score) } + " words top3=" + words.take(3).map { photoIds[it.id] to "%.2f".format(it.score) } + " fused top3=" + fused.take(3).map { photoIds[it.id] to "%.4f".format(it.score) })
            val answer = Answer(fused.map { h ->
                Result(photoIds[h.id], h.score, buildList {
                    val look = lookScore[h.id]?.let { s -> rival[h.id]?.let { (name, r) -> "%.2f; next best guess “%s” %.2f".format(s, name, r) } }
                    lookRank[h.id]?.let { add("looks like it (#$it, $look)") }
                    if (weak && look != null) add("closest look, but weak ($look)")
                    meaning[h.id]?.let { add("text means it: “${it.take(60)}”") }
                    if (h.id in wordIds) add("contains the words")
                })
            }, weak)
            if (!traced) return answer to null

            val ranks = lists.map { l -> l.withIndex().associate { (r, h) -> h.id to r + 1 } }
            val pieceEnc = minilm.tokenizer.encode(query)
            val trace = SearchTrace(
                query = query,
                clipTokens = clipEnc.tokens.map { it.removeSuffix("</w>") }, clipIds = clipEnc.ids.take(clipEnc.tokens.size),
                pieces = pieceEnc.tokens, pieceIds = pieceEnc.ids.toList(),
                clipVec = qClip, textVec = qText, timing = timing,
                index = IndexTrace(
                    kind = if (photoIndex is Hnsw) "HNSW" else "brute force", total = photoIds.size,
                    compared = hnswTrace?.compared ?: photoIds.size,
                    hops = hnswTrace?.hops?.map { Hop(it.layer, photoIds[it.id], it.sim) } ?: emptyList(),
                    nodesPerLayer = (photoIndex as? Hnsw)?.let { h -> (0 until h.layerCount).map(h::nodesOnLayer) } ?: listOf(photoIds.size),
                    ef = k.ef,
                ),
                look = closest.take(12).map { h -> rival.getValue(h.id).let { (n, s) -> LookRow(photoIds[h.id], h.score, n, s, !k.otherGuesses || h.score > s) } },
                lookOn = k.useLook, guessesOn = k.otherGuesses,
                meaning = allChunks.take(8).map { MeaningRow(photoIds[chunkPhoto[it.id]], chunkText[it.id], it.score, it.score > k.minTextSimilarity) },
                meaningOn = k.useMeaning, minText = k.minTextSimilarity,
                words = Bm25.tokenize(query).distinct(),
                wordRows = byWords.take(5).map { WordRow(photoIds[it.id], bm25.explain(query, it.id), it.score) },
                wordsOn = k.useWords,
                fusion = if (weak) emptyList() else fused.take(10).map { h ->
                    val r = ranks.map { it[h.id] }
                    FusionRow(photoIds[h.id], r, r.map { x -> if (x == null) 0f else 1f / (k.rrfK + x) }, h.score)
                },
                rrfK = k.rrfK,
                answer = answer,
            )
            return answer to trace
        }
    }

    /** Everything stored and computed for one photo. */
    fun photoTrace(photoId: Long): PhotoTrace? {
        val photo = store.photos().firstOrNull { it.id == photoId } ?: return null
        val sims = otherGuesses.map { (n, v) -> n to dot(photo.clip, v) }
        val max = sims.maxOf { it.second }
        val expSum = sims.sumOf { exp(100.0 * (it.second - max)) }
        val guesses = sims.map { (n, s) -> Triple(n, s, (exp(100.0 * (s - max)) / expSum).toFloat()) }.sortedByDescending { it.second }.take(5)
        val chunks = store.chunks().filter { it.photoId == photoId }.map { it.text to it.vec }
        synchronized(lock) {
            val pos = position[photoId] ?: return null
            val neighbours = photoVecs.indices.filter { it != pos }.map { photoIds[it] to dot(photo.clip, photoVecs[it]) }.sortedByDescending { it.second }.take(6)
            val h = photoIndex as? Hnsw
            return PhotoTrace(photoId, photo.clip, guesses, photo.ocrText, chunks, knobs.chunkWords, knobs.chunkOverlap,
                h?.levelOf(pos) ?: 0, h?.layerCount ?: 1, neighbours)
        }
    }

    /** Lab: how many of the true top 10 (brute force) does the index return, and how fast. */
    fun measureIndex(queries: List<String>): Triple<Double, Double, Double> {
        val qs = queries.map { clip.embedText(it) }
        synchronized(lock) {
            (photoIndex as? Hnsw)?.ef = knobs.ef
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

    /** The generic description that fits this photo best, and its score. */
    private fun bestOtherGuess(photo: FloatArray, others: List<Pair<String, FloatArray>>): Pair<String, Float> =
        others.map { (name, v) -> name to dot(photo, v) }.maxByOrNull { it.second } ?: ("" to -1f)

    private fun dot(a: FloatArray, b: FloatArray): Float { var d = 0f; for (i in a.indices) d += a[i] * b[i]; return d }

    /** The other guesses, encoded once (18 × CLIP text ≈ 0.5 s on the phone). Call at start-up so the first search is fast. */
    private val otherGuesses: List<Pair<String, FloatArray>> by lazy { OTHER_GUESSES.map { it to clip.embedText(it) } }
    fun warmUp() { otherGuesses.size }

    companion object {
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
    }

    fun indexStats(): String = synchronized(lock) { (photoIndex as? Hnsw)?.stats() ?: "brute force" }
}
