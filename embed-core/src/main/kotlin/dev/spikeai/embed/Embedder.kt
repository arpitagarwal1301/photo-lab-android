package dev.spikeai.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer

/** Anything that turns token ids into one 384-number row per token. */
interface MiniLmEngine {
    fun hidden(ids: LongArray): Array<FloatArray>
}

/** The fast path: ONNX Runtime runs the graph stored in model.onnx. */
class OrtMiniLm(private val session: OrtSession) : MiniLmEngine, AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()

    override fun hidden(ids: LongArray): Array<FloatArray> {
        val n = ids.size
        val shape = longArrayOf(1, n.toLong())
        val inputs = mapOf(
            "input_ids" to ids,
            "attention_mask" to LongArray(n) { 1 }, // no padding: every token counts
            "token_type_ids" to LongArray(n),        // single sentence: all segment 0
        ).filterKeys { it in session.inputNames }
            .mapValues { (_, v) -> OnnxTensor.createTensor(env, LongBuffer.wrap(v), shape) }
        try {
            session.run(inputs).use { out ->
                @Suppress("UNCHECKED_CAST")
                return (out.get(0).value as Array<Array<FloatArray>>)[0]
            }
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    override fun close() = session.close()

    companion object {
        fun load(modelPath: String, threads: Int = 4): OrtMiniLm {
            val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) }
            return OrtMiniLm(OrtEnvironment.getEnvironment().createSession(modelPath, opts))
        }
    }
}

/** text → tokens → engine (ONNX Runtime or our Kotlin) → mean-pool → normalize → 384 numbers. */
class Embedder(val engine: MiniLmEngine, val tokenizer: WordPieceTokenizer) {

    class Embedding(val text: String, val tokens: List<String>, val vector: FloatArray, val millis: Double)

    fun embed(text: String): Embedding {
        val t0 = System.nanoTime()
        val enc = tokenizer.encode(text)
        val vector = VectorMath.normalize(VectorMath.meanPool(engine.hidden(enc.ids)))
        return Embedding(text, enc.tokens, vector, (System.nanoTime() - t0) / 1e6)
    }

    companion object {
        fun load(modelPath: String, vocabLines: List<String>) =
            Embedder(OrtMiniLm.load(modelPath), WordPieceTokenizer.fromVocabLines(vocabLines))
    }
}
