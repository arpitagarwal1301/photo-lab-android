package dev.spikeai.photos.data

import android.content.Context
import dev.spikeai.embed.ClipEncoder
import dev.spikeai.embed.Embedder
import dev.spikeai.embed.MiniLmKotlin
import dev.spikeai.embed.OrtMiniLm
import dev.spikeai.embed.WordPieceTokenizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The three networks the app uses, stored in app-private storage (files/models):
 *  - MobileCLIP image + text encoders → photo search by meaning
 *  - MiniLM → meaning of the text found inside photos (OCR chunks)
 * Downloaded once from Hugging Face; after that everything runs offline.
 */
class Models(context: Context) {
    val dir = File(context.filesDir, "models")

    class Need(val path: String, val url: String, val approxMb: Int, val optional: Boolean = false)

    private val hf = "https://huggingface.co"
    val needs = listOf(
        Need("mobileclip/vision_model.onnx", "$hf/Xenova/mobileclip_s0/resolve/main/onnx/vision_model.onnx", 46),
        Need("mobileclip/text_model.onnx", "$hf/Xenova/mobileclip_s0/resolve/main/onnx/text_model.onnx", 170),
        Need("mobileclip/tokenizer.json", "$hf/Xenova/mobileclip_s0/resolve/main/tokenizer.json", 2),
        Need("minilm/model.onnx", "$hf/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx", 90),
        Need("minilm/vocab.txt", "$hf/sentence-transformers/all-MiniLM-L6-v2/resolve/main/vocab.txt", 1),
        // Only for the Lab's "hand-written MiniLM" switch.
        Need("minilm/model.safetensors", "$hf/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.safetensors", 91, optional = true),
    )

    /** Our own int8 text model (same accuracy, smaller) if it was copied over USB; else the full one. */
    private val clipText get() = File(dir, "mobileclip/text_model_q8.onnx").takeIf { it.exists() } ?: File(dir, "mobileclip/text_model.onnx")

    fun missing(): List<Need> = needs.filter { n ->
        val present = File(dir, n.path).exists() || (n.path == "mobileclip/text_model.onnx" && File(dir, "mobileclip/text_model_q8.onnx").exists())
        !n.optional && !present
    }

    fun download(onProgress: (file: String, done: Long, total: Long) -> Unit) {
        for (n in missing()) {
            val target = File(dir, n.path).apply { parentFile!!.mkdirs() }
            val part = File(target.path + ".part")
            val conn = URL(n.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16); var done = 0L; var last = 0L
                    while (true) {
                        val r = input.read(buf); if (r < 0) break
                        out.write(buf, 0, r); done += r
                        if (done - last > (1 shl 20)) { onProgress(n.path, done, total); last = done }
                    }
                }
            }
            part.renameTo(target)
        }
    }

    fun loadClip(): ClipEncoder = ClipEncoder.load(
        clipText.path, File(dir, "mobileclip/vision_model.onnx").path, File(dir, "mobileclip/tokenizer.json").readText(),
    )

    fun vocab() = File(dir, "minilm/vocab.txt").readLines()

    fun loadMiniLm(): Embedder = Embedder(OrtMiniLm.load(File(dir, "minilm/model.onnx").path), WordPieceTokenizer.fromVocabLines(vocab()))

    /** null when the optional weights file isn't there. */
    fun loadHandWrittenMiniLm(): Embedder? = File(dir, "minilm/model.safetensors").takeIf { it.exists() }?.let {
        Embedder(MiniLmKotlin.load(File(dir, "minilm")), WordPieceTokenizer.fromVocabLines(vocab()))
    }

    val clipTextName get() = clipText.name
}
