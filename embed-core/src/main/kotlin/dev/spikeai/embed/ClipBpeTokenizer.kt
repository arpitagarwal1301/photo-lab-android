package dev.spikeai.embed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/**
 * CLIP's tokenizer: byte-level BPE (byte-pair encoding).
 *
 * WordPiece (MiniLM) starts from whole words and cuts them; BPE goes the other way:
 * start from single characters and repeatedly glue the pair that was most common in training
 * ("merges" list, rank 0 = most common) until no known pair is left.
 *   "glasses" → g l a s s e s</w> → … → glasses</w>
 * Byte-level: text is first turned into UTF-8 bytes, so emoji, ₹ and any script work — nothing is unknown.
 */
class ClipBpeTokenizer(private val vocab: Map<String, Int>, merges: List<Pair<String, String>>) {

    private val rank: Map<Pair<String, String>, Int> = merges.withIndex().associate { (i, p) -> p to i }
    private val cache = HashMap<String, List<String>>()
    private val split = Regex("""'s|'t|'re|'ve|'m|'ll|'d|[\p{L}]+|[\p{N}]|[^\s\p{L}\p{N}]+""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""[\s\p{Z}]+""") // any Unicode space; works on both JVM and Android regex engines

    class Encoding(val tokens: List<String>, val ids: LongArray)

    /** Returns exactly [length] ids: start, pieces…, end, then zeros (the model needs a fixed 77). */
    fun encode(text: String, length: Int = CONTEXT): Encoding {
        val clean = spaces.replace(Normalizer.normalize(text, Normalizer.Form.NFC), " ").lowercase()
        val pieces = split.findAll(clean).flatMap { m -> bpe(toByteChars(m.value)) }.toList()
        val body = pieces.take(length - 2)
        val tokens = listOf(SOT) + body + EOT
        val ids = LongArray(length)
        tokens.forEachIndexed { i, t -> ids[i] = (vocab[t] ?: error("not in vocab: $t")).toLong() }
        return Encoding(tokens, ids)
    }

    /** UTF-8 bytes → one printable character per byte (GPT-2's trick, so pieces stay normal strings). */
    private fun toByteChars(s: String) = buildString { for (b in s.toByteArray(Charsets.UTF_8)) append(BYTE_CHAR[b.toInt() and 0xFF]) }

    private fun bpe(word: String): List<String> = cache.getOrPut(word) {
        // Start: one symbol per character; the last one carries the end-of-word marker.
        val symbols = word.map { it.toString() }.toMutableList()
        symbols[symbols.lastIndex] = symbols.last() + "</w>"
        while (symbols.size > 1) {
            // Find the adjacent pair with the best (lowest) rank.
            var best = -1
            var bestRank = Int.MAX_VALUE
            for (i in 0 until symbols.size - 1) {
                val r = rank[symbols[i] to symbols[i + 1]] ?: continue
                if (r < bestRank) { bestRank = r; best = i }
            }
            if (best < 0) break
            // Glue every occurrence of that pair, left to right.
            val a = symbols[best]; val b = symbols[best + 1]
            var i = 0
            while (i < symbols.size - 1) {
                if (symbols[i] == a && symbols[i + 1] == b) { symbols[i] = a + b; symbols.removeAt(i + 1) }
                i++
            }
        }
        symbols
    }

    companion object {
        const val CONTEXT = 77
        const val SOT = "<|startoftext|>"
        const val EOT = "<|endoftext|>"

        /** GPT-2 bytes_to_unicode: printable bytes map to themselves, the rest to chars from U+0100 up. */
        private val BYTE_CHAR: CharArray = run {
            val keep = (33..126) + (161..172) + (174..255)
            val out = CharArray(256)
            var next = 256
            for (b in 0..255) out[b] = if (b in keep) b.toChar() else (next++).toChar()
            out
        }

        fun fromTokenizerJson(json: String): ClipBpeTokenizer {
            val model = Json.parseToJsonElement(json).jsonObject["model"]!!.jsonObject
            val vocab = model["vocab"]!!.jsonObject.mapValues { it.value.jsonPrimitive.int }
            val merges = model["merges"]!!.jsonArray.map { m ->
                if (m is JsonArray) m[0].jsonPrimitive.content to m[1].jsonPrimitive.content
                else m.jsonPrimitive.content.split(' ', limit = 2).let { it[0] to it[1] }
            }
            return ClipBpeTokenizer(vocab, merges)
        }
    }
}
