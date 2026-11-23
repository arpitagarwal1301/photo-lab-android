package dev.spikeai.embed

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Checks the Kotlin pipeline against Python (HF tokenizers + onnxruntime) — reference.tsv. */
class EmbedderTest {
    private val dir = File(System.getProperty("minilm.dir") ?: ".work/models/minilm")
    private val ref = javaClass.getResource("/reference.tsv")!!.readText().lines().filter { it.isNotBlank() }.map { it.split('\t') }

    @Test fun tokensMatchPython() {
        assumeTrue("model not downloaded", File(dir, "vocab.txt").exists())
        val tok = WordPieceTokenizer.fromVocabLines(File(dir, "vocab.txt").readLines())
        ref.filter { it[0] == "tokens" }.forEach { (_, text, expected) ->
            assertEquals(text, expected, tok.encode(text).tokens.joinToString(" "))
        }
    }

    @Test fun vectorsAndSimilaritiesMatchPython() {
        assumeTrue("model not downloaded", File(dir, "model.onnx").exists())
        val e = Embedder.load(File(dir, "model.onnx").path, File(dir, "vocab.txt").readLines())
        run {
            ref.filter { it[0] == "vec" }.forEach { (_, text, nums) ->
                val got = e.embed(text).vector
                nums.split(' ').map(String::toFloat).forEachIndexed { i, x -> assertClose("$text[$i]", x, got[i]) }
            }
            ref.filter { it[0] == "pair" }.forEach { (_, a, b, s) ->
                assertClose("$a | $b", s.toFloat(), VectorMath.dot(e.embed(a).vector, e.embed(b).vector))
            }
        }
    }

    @Test fun handWrittenKotlinMatchesOnnxRuntime() {
        assumeTrue("weights not downloaded", File(dir, "model.safetensors").exists())
        val tok = WordPieceTokenizer.fromVocabLines(File(dir, "vocab.txt").readLines())
        val ort = Embedder(OrtMiniLm.load(File(dir, "model.onnx").path), tok)
        val mine = Embedder(MiniLmKotlin.load(dir), tok)
        ref.filter { it[0] == "vec" }.forEach { (_, text, nums) ->
            val a = ort.embed(text); val b = mine.embed(text)
            nums.split(' ').map(String::toFloat).forEachIndexed { i, x -> assertClose("kotlin $text[$i]", x, b.vector[i]) }
            val sim = VectorMath.dot(a.vector, b.vector)
            println("%-40s ort %6.1f ms   kotlin %6.1f ms   same-direction %.6f".format(text, a.millis, b.millis, sim))
            if (sim < 0.99999f) throw AssertionError("$text: kotlin vs ort cosine $sim")
        }
    }

    private fun assertClose(what: String, expected: Float, actual: Float) {
        if (abs(expected - actual) > 1e-4f) throw AssertionError("$what: expected $expected, got $actual")
    }
}
