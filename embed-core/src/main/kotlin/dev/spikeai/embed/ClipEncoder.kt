package dev.spikeai.embed

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * MobileCLIP: two networks trained together so a photo and a sentence describing it land on the same
 * 512-number direction. Search = embed the query with the text network, compare with stored photo vectors.
 */
class ClipEncoder(
    private val text: OrtSession,
    private val vision: OrtSession,
    val tokenizer: ClipBpeTokenizer,
) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()

    fun embedText(query: String): FloatArray {
        val ids = tokenizer.encode(query).ids
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { t ->
            text.run(mapOf(text.inputNames.first() to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                return VectorMath.normalize((out.get(0).value as Array<FloatArray>)[0])
            }
        }
    }

    /** [chw] from [ImagePrep.toChw]. */
    fun embedImage(chw: FloatArray): FloatArray {
        val s = ImagePrep.SIZE.toLong()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, s, s)).use { t ->
            vision.run(mapOf(vision.inputNames.first() to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                return VectorMath.normalize((out.get(0).value as Array<FloatArray>)[0])
            }
        }
    }

    override fun close() { text.close(); vision.close() }

    companion object {
        fun load(textModel: String, visionModel: String, tokenizerJson: String, threads: Int = 4): ClipEncoder {
            val env = OrtEnvironment.getEnvironment()
            fun open(p: String) = env.createSession(p, OrtSession.SessionOptions().apply { setIntraOpNumThreads(threads) })
            return ClipEncoder(open(textModel), open(visionModel), ClipBpeTokenizer.fromTokenizerJson(tokenizerJson))
        }
    }
}
