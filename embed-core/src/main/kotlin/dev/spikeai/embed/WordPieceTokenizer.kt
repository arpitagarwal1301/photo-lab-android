package dev.spikeai.embed

import java.text.Normalizer

/**
 * Lesson 1, step 1: the network can't read letters, only ids from a fixed vocabulary (30,522 word-pieces).
 * BERT-style: lowercase → strip accents → split on spaces and punctuation → break each word into the
 * longest pieces the vocabulary knows ("spectacles" → "spectacle", "##s"). Unknown words still get pieces.
 */
class WordPieceTokenizer(private val vocab: Map<String, Int>, private val maxTokens: Int = 128) {

    class Encoding(val tokens: List<String>, val ids: LongArray)

    private val unkId = vocab.getValue(UNK)

    fun encode(text: String): Encoding {
        val pieces = basicSplit(text).flatMap(::wordPieces).take(maxTokens - 2)
        val tokens = listOf(CLS) + pieces + SEP
        return Encoding(tokens, LongArray(tokens.size) { (vocab[tokens[it]] ?: unkId).toLong() })
    }

    private fun basicSplit(text: String): List<String> {
        val cleaned = buildString {
            for (ch in text) when {
                ch.code == 0 || ch.code == 0xFFFD -> Unit
                ch.isWhitespace() -> append(' ')
                Character.getType(ch).let { it == Character.CONTROL.toInt() || it == Character.FORMAT.toInt() } -> Unit
                isCjk(ch.code) -> append(' ').append(ch).append(' ')
                else -> append(ch)
            }
        }
        val out = mutableListOf<String>()
        for (raw in cleaned.split(' ')) {
            if (raw.isEmpty()) continue
            val word = stripAccents(raw.lowercase())
            val cur = StringBuilder()
            for (ch in word) {
                if (isPunctuation(ch)) {
                    if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                    out += ch.toString()
                } else cur.append(ch)
            }
            if (cur.isNotEmpty()) out += cur.toString()
        }
        return out
    }

    /** Greedy longest-match from the left; pieces after the first are prefixed with "##". */
    private fun wordPieces(word: String): List<String> {
        if (word.length > 100) return listOf(UNK)
        val out = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var match: String? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                if (piece in vocab) { match = piece; break }
                end--
            }
            if (match == null) return listOf(UNK)
            out += match
            start = end
        }
        return out
    }

    private fun stripAccents(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

    private fun isPunctuation(ch: Char): Boolean {
        val c = ch.code
        if (c in 33..47 || c in 58..64 || c in 91..96 || c in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
            Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun isCjk(c: Int) = c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0xF900..0xFAFF

    companion object {
        const val CLS = "[CLS]"
        const val SEP = "[SEP]"
        const val UNK = "[UNK]"

        /** vocab.txt: one token per line, line number = id. */
        fun fromVocabLines(lines: List<String>, maxTokens: Int = 128) =
            WordPieceTokenizer(lines.withIndex().associate { (i, t) -> t to i }, maxTokens)
    }
}
