package dev.spikeai.photos.data

import dev.spikeai.search.Bm25

/** Everything one search did, step by step, with real numbers — drawn by the "inside this search" screen. */
class SearchTrace(
    val query: String,
    /** Step 2: the two tokenizers. CLIP's pieces (byte-pair encoding) and MiniLM's word-pieces. */
    val clipTokens: List<String>, val clipIds: List<Long>,
    val pieces: List<String>, val pieceIds: List<Long>,
    /** Step 3: the query as numbers. */
    val clipVec: FloatArray, val textVec: FloatArray,
    val timing: SearchEngine.Timing,
    /** Step 4: how the vector index found the nearest photos. */
    val index: IndexTrace,
    /** Step 5: the three signals. */
    val look: List<LookRow>, val lookOn: Boolean, val guessesOn: Boolean,
    val meaning: List<MeaningRow>, val meaningOn: Boolean, val minText: Float,
    val words: List<String>, val wordRows: List<WordRow>, val wordsOn: Boolean,
    /** Step 6: merging the lists. */
    val fusion: List<FusionRow>, val rrfK: Int,
    /** Step 7. */
    val answer: SearchEngine.Answer,
)

class Hop(val layer: Int, val photoId: Long, val sim: Float)
class IndexTrace(val kind: String, val total: Int, val compared: Int, val hops: List<Hop>, val nodesPerLayer: List<Int>, val ef: Int)
class LookRow(val photoId: Long, val score: Float, val rival: String, val rivalScore: Float, val kept: Boolean)
class MeaningRow(val photoId: Long, val chunk: String, val score: Float, val kept: Boolean)
class WordRow(val photoId: Long, val terms: List<Bm25.Term>, val total: Float)
/** [ranks] and [parts] are in signal order: looks, means, words. A null rank = not in that list. */
class FusionRow(val photoId: Long, val ranks: List<Int?>, val parts: List<Float>, val total: Float)

/** Everything the app stored and computed for one photo — drawn by the "inside this photo" screen. */
class PhotoTrace(
    val photoId: Long,
    val clipVec: FloatArray,
    /** What CLIP thinks the photo is: generic guesses with similarity and softmax share (CLIP's ×100 scale). */
    val guesses: List<Triple<String, Float, Float>>,
    val ocrText: String?,
    val chunks: List<Pair<String, FloatArray>>,
    val chunkWords: Int, val chunkOverlap: Int,
    /** Its place in the vector index. */
    val level: Int, val layers: Int,
    val neighbours: List<Pair<Long, Float>>,
)
