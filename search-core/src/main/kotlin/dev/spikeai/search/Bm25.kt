package dev.spikeai.search

import kotlin.math.ln

/**
 * Keyword search, the classic way (BM25, used by Elasticsearch/Lucene).
 * Vectors are great at meaning ("spectacles" ≈ "glasses") but bad at exact strings:
 * an order id, a phone number, "INV-2291". BM25 is the opposite. Hybrid search uses both.
 *
 * score(doc) = Σ over query words: idf(word) × tf·(k1+1) / (tf + k1·(1 − b + b·len/avgLen))
 *   idf: rare words count more ("invoice" > "the")
 *   tf:  repeating a word helps, but with diminishing returns (k1)
 *   b:   long documents don't win just by being long
 */
class Bm25(var k1: Double = 1.2, var b: Double = 0.75) {
    /** One query word's share of one document's score. */
    class Term(val word: String, val docsWithIt: Int, val idf: Double, val tf: Int, val part: Double)

    private val postings = HashMap<String, HashMap<Int, Int>>() // word → (doc → count)
    private val docLength = HashMap<Int, Int>()

    val size get() = docLength.size

    fun add(docId: Int, text: String) {
        val words = tokenize(text)
        docLength[docId] = (docLength[docId] ?: 0) + words.size
        for (w in words) postings.getOrPut(w) { HashMap() }.merge(docId, 1, Int::plus)
    }

    fun search(query: String, k: Int): List<Hit> {
        if (docLength.isEmpty()) return emptyList()
        val scores = HashMap<Int, Double>()
        for (w in tokenize(query).toSet()) {
            val docs = postings[w] ?: continue
            for ((doc, tf) in docs) scores.merge(doc, part(w, docs.size, tf, doc), Double::plus)
        }
        return scores.entries.sortedByDescending { it.value }.take(k).map { Hit(it.key, it.value.toFloat()) }
    }

    /** Why [doc] scored what it did: one row per query word (words not in the doc have part 0). */
    fun explain(query: String, doc: Int): List<Term> = tokenize(query).distinct().map { w ->
        val docs = postings[w]
        val tf = docs?.get(doc) ?: 0
        val n = docs?.size ?: 0
        Term(w, n, idf(n), tf, if (tf == 0 || docs == null) 0.0 else part(w, n, tf, doc))
    }

    private fun idf(docsWithWord: Int): Double { val n = docLength.size.toDouble(); return ln(1 + (n - docsWithWord + 0.5) / (docsWithWord + 0.5)) }

    @Suppress("UNUSED_PARAMETER")
    private fun part(word: String, docsWithWord: Int, tf: Int, doc: Int): Double {
        val avg = docLength.values.average()
        return idf(docsWithWord) * tf * (k1 + 1) / (tf + k1 * (1 - b + b * docLength.getValue(doc) / avg))
    }

    companion object {
        private val WORD = Regex("""[\p{L}\p{N}]+""")
        private val STOP = setOf("a", "an", "the", "of", "and", "or", "to", "in", "on", "for", "is", "it", "at", "with", "by")
        fun tokenize(text: String) = WORD.findAll(text.lowercase()).map { it.value }.filter { it !in STOP }.toList()
    }
}

/**
 * Reciprocal Rank Fusion: merge ranked lists that use different score scales (cosine vs BM25)
 * by looking only at rank. score = Σ 1 / (k + rank). Simple, and hard to beat.
 */
object Fusion {
    fun rrf(lists: List<List<Hit>>, k: Int = 60, limit: Int = 50): List<Hit> {
        val score = HashMap<Int, Float>()
        for (list in lists) list.forEachIndexed { rank, hit -> score.merge(hit.id, 1f / (k + rank + 1), Float::plus) }
        return score.entries.sortedByDescending { it.value }.take(limit).map { Hit(it.key, it.value) }
    }
}
