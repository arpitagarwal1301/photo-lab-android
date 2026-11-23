package dev.spikeai.photos.data

import android.content.Context

/**
 * Every value a learner can turn, in one place, saved on the phone. The Lab screen draws one slider or switch
 * per [Knob]; the search engine, indexer and label trainer read the current value every time they run.
 */
class Knobs(context: Context) {
    private val prefs = context.getSharedPreferences("knobs", Context.MODE_PRIVATE)

    enum class Group(val title: String, val help: String) {
        SEARCH("Search knobs", "Change one and the sample search below re-runs right away."),
        INDEX("Index knobs", "These change how photos are stored. Press Rebuild to apply them."),
        TRAINING("Label training knobs", "Used the next time a personal label trains."),
    }

    enum class Kind { SWITCH, INT, DECIMAL }

    class Knob(
        val key: String, val group: Group, val kind: Kind, val label: String, val help: String,
        val default: Float, val min: Float = 0f, val max: Float = 1f,
    )

    operator fun get(k: Knob): Float = prefs.getFloat(k.key, k.default)
    operator fun set(k: Knob, v: Float) = prefs.edit().putFloat(k.key, v).apply()
    fun isDefault(k: Knob) = this[k] == k.default
    fun reset(group: Group) = prefs.edit().apply { ALL.filter { it.group == group }.forEach { remove(it.key) } }.apply()

    private fun on(k: Knob) = this[k] > 0.5f
    private fun int(k: Knob) = this[k].toInt()

    val useLook get() = on(LOOK)
    val useMeaning get() = on(MEANING)
    val useWords get() = on(WORDS)
    val otherGuesses get() = on(GUESSES)
    val sameAsQuery get() = this[SAME_AS_QUERY]
    val minTextSimilarity get() = this[MIN_TEXT]
    val rrfK get() = int(RRF_K)
    val bm25K1 get() = this[BM25_K1].toDouble()
    val bm25B get() = this[BM25_B].toDouble()
    val ef get() = int(EF)
    val hnswM get() = int(HNSW_M)
    val efConstruction get() = int(EF_CONSTRUCTION)
    val chunkWords get() = int(CHUNK_WORDS)
    /** Overlap must stay below the chunk size, or a chunk would never move forward. */
    val chunkOverlap get() = int(CHUNK_OVERLAP).coerceAtMost(chunkWords - 1)
    val epochs get() = int(EPOCHS)
    val learningRate get() = this[LR]
    val l2 get() = this[L2]
    val minTags get() = int(MIN_TAGS)

    companion object {
        private val S = Group.SEARCH; private val I = Group.INDEX; private val T = Group.TRAINING
        val LOOK = Knob("look", S, Kind.SWITCH, "Looks like it (CLIP)", "Compare your words with the photo's pixels.", 1f)
        val MEANING = Knob("meaning", S, Kind.SWITCH, "Text means it (MiniLM)", "Compare your words with the meaning of text read from photos.", 1f)
        val WORDS = Knob("words", S, Kind.SWITCH, "Contains the words (BM25)", "Exact words and numbers in the photo's text.", 1f)
        val GUESSES = Knob("guesses", S, Kind.SWITCH, "Other-guesses check",
            "Keep a photo only if your words fit it better than generic guesses like “a screenshot of a web page”. Turn it off and search “update” to see the old bug.", 1f)
        val SAME_AS_QUERY = Knob("same_as_query", S, Kind.DECIMAL, "Skip guesses this close to your words",
            "“an animal” shouldn't compete with “a dog”. Guesses whose CLIP text similarity to your query is above this are left out.", 0.8f, 0.6f, 0.95f)
        val MIN_TEXT = Knob("min_text", S, Kind.DECIMAL, "Text meaning bar",
            "How similar (MiniLM) a chunk of text must be to count. Lower = more, looser text matches.", 0.45f, 0.2f, 0.8f)
        val RRF_K = Knob("rrf_k", S, Kind.INT, "Fusion k",
            "Merging lists: score = Σ 1 / (k + rank). Small k: each list's #1 dominates. Large k: the lists blend evenly.", 60f, 1f, 100f)
        val BM25_K1 = Knob("bm25_k1", S, Kind.DECIMAL, "BM25 k1", "How much repeating a word helps. 0 = once is as good as ten times.", 1.2f, 0f, 3f)
        val BM25_B = Knob("bm25_b", S, Kind.DECIMAL, "BM25 b", "How much long texts are held back. 0 = length doesn't matter.", 0.75f, 0f, 1f)
        val EF = Knob("ef", S, Kind.INT, "HNSW ef (search effort)", "How many candidates HNSW keeps while hopping. Higher = more accurate, slower.", 64f, 8f, 256f)

        val HNSW_M = Knob("hnsw_m", I, Kind.INT, "HNSW links per photo (m)", "More links = easier to find your way through the graph, more memory.", 16f, 4f, 48f)
        val EF_CONSTRUCTION = Knob("ef_construction", I, Kind.INT, "HNSW build effort", "How hard the index looks for good neighbours when adding a photo.", 100f, 16f, 400f)
        val CHUNK_WORDS = Knob("chunk_words", I, Kind.INT, "Chunk size (words)",
            "Text in a photo is cut into pieces this long before MiniLM. Too long and one vector has to mean too many things.", 40f, 10f, 120f)
        val CHUNK_OVERLAP = Knob("chunk_overlap", I, Kind.INT, "Chunk overlap (words)",
            "Words repeated at the start of the next chunk, so a fact cut at a border still appears whole once.", 10f, 0f, 40f)

        val EPOCHS = Knob("epochs", T, Kind.INT, "Passes over your examples (epochs)", "More passes = fits your tagged photos more closely.", 30f, 1f, 100f)
        val LR = Knob("lr", T, Kind.DECIMAL, "Learning rate", "Size of each step. Too big and the loss jumps around; too small and it barely moves.", 0.003f, 0.0005f, 0.03f)
        val L2 = Knob("l2", T, Kind.DECIMAL, "Pull back to the label's words",
            "Keeps the model close to the plain text search for the label name. 0 = trust only your examples.", 1f, 0f, 5f)
        val MIN_TAGS = Knob("min_tags", T, Kind.INT, "Tagged photos before training", "Below this, the label is plain text search.", 5f, 1f, 10f)

        val ALL = listOf(LOOK, MEANING, WORDS, GUESSES, SAME_AS_QUERY, MIN_TEXT, RRF_K, BM25_K1, BM25_B, EF,
            HNSW_M, EF_CONSTRUCTION, CHUNK_WORDS, CHUNK_OVERLAP, EPOCHS, LR, L2, MIN_TAGS)
    }
}
