package dev.spikeai.search

/**
 * Why chunk? An embedding is one point for the whole text. A receipt with 40 lines averaged into one point
 * means nothing in particular. Small pieces each keep a clear meaning, so "GST number" can match one line.
 *
 * Strategy: split into sentences/lines, pack them into chunks of up to [maxWords], and repeat the last
 * [overlapWords] of each chunk at the start of the next so a fact cut at the border still appears whole once.
 */
class Chunker(private val maxWords: Int = 40, private val overlapWords: Int = 10) {
    init { require(overlapWords < maxWords) }

    fun chunk(text: String): List<String> {
        val units = text.split(Regex("""(?<=[.!?])\s+|\n+""")).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = ArrayList<String>()
        var current = ArrayList<String>()
        var fresh = 0 // words added since the last chunk was emitted (overlap words don't count)
        fun emit() {
            chunks += current.joinToString(" ")
            current = ArrayList(current.takeLast(overlapWords)); fresh = 0
        }
        for (unit in units) {
            val words = unit.split(Regex("""\s+"""))
            // Prefer to cut between sentences/lines: if this one won't fit, start a new chunk first.
            if (fresh > 0 && current.size + words.size > maxWords) emit()
            for (w in words) {                        // a very long line still gets cut, mid-line
                if (current.size == maxWords) emit()
                current += w; fresh++
            }
        }
        if (fresh > 0) chunks += current.joinToString(" ")
        return chunks
    }
}
