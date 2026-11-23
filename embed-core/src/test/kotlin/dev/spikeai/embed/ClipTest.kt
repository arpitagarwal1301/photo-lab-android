package dev.spikeai.embed

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Kotlin CLIP vs Python (HF tokenizers + onnxruntime): clip_reference.tsv. */
class ClipTest {
    private val dir = File(System.getProperty("mobileclip.dir") ?: ".work/models/mobileclip")
    private val ref = javaClass.getResource("/clip_reference.tsv")!!.readText().lines().filter { it.isNotBlank() }.map { it.split('\t') }

    @Test fun bpeIdsMatchPython() {
        assumeTrue(File(dir, "tokenizer.json").exists())
        val tok = ClipBpeTokenizer.fromTokenizerJson(File(dir, "tokenizer.json").readText())
        ref.filter { it[0] == "ids" }.forEach { (_, text, ids) ->
            val got = tok.encode(text).ids.takeWhile { it != 0L }.joinToString(" ")
            assertEquals(text, ids, got)
        }
    }

    @Test fun textAndImageVectorsMatchPython() {
        assumeTrue(File(dir, "text_model.onnx").exists())
        ClipEncoder.load(File(dir, "text_model.onnx").path, File(dir, "vision_model.onnx").path, File(dir, "tokenizer.json").readText()).use { clip ->
            ref.filter { it[0] == "tvec" }.forEach { (_, text, nums) -> close(text, nums, clip.embedText(text)) }
            val rgb = javaClass.getResource("/clip_reference_img.rgb")!!.readBytes()
            val argb = IntArray(256 * 256) { i -> (0xFF shl 24) or ((rgb[3 * i].toInt() and 0xFF) shl 16) or ((rgb[3 * i + 1].toInt() and 0xFF) shl 8) or (rgb[3 * i + 2].toInt() and 0xFF) }
            val img = clip.embedImage(ImagePrep.toChw(argb))
            close("image", ref.first { it[0] == "ivec" }[2], img)
            ref.filter { it[0] == "isim" }.forEach { (_, text, s) ->
                val sim = VectorMath.dot(img, clip.embedText(text))
                if (abs(sim - s.toFloat()) > 1e-4f) throw AssertionError("$text: $sim vs $s")
            }
        }
    }

    @Test fun resizeKeepsAFlatImageFlatAndCropsCentre() {
        val w = 640; val h = 480
        val src = IntArray(w * h) { i -> if (i % w in 280 until 360) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }
        val out = ImagePrep.resizeShortSideAndCrop(src, w, h)
        assertEquals(256 * 256, out.size)
        assertEquals(0xFF0000FF.toInt(), out[10])              // left edge stays blue
        assertEquals(0xFFFF0000.toInt(), out[128 * 256 + 128]) // centre stripe stays red
    }

    private fun close(what: String, nums: String, got: FloatArray) =
        nums.split(' ').map(String::toFloat).forEachIndexed { i, x ->
            if (abs(x - got[i]) > 1e-4f) throw AssertionError("$what[$i]: expected $x got ${got[i]}")
        }
}
