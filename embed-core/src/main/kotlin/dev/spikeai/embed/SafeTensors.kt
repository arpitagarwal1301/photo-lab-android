package dev.spikeai.embed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * The weights file format from Hugging Face, readable in 20 lines:
 * [8 bytes: header length N][N bytes: JSON {name: {dtype, shape, data_offsets}}][raw little-endian numbers]
 */
class SafeTensors(file: File) {
    class Tensor(val shape: List<Long>, val data: FloatArray)

    private val buf: ByteBuffer
    private val header: Map<String, Triple<String, List<Long>, LongRange>>
    private val dataStart: Long

    init {
        RandomAccessFile(file, "r").use { raf ->
            buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length()).order(ByteOrder.LITTLE_ENDIAN)
        }
        val n = buf.getLong(0)
        val json = ByteArray(n.toInt()).also { buf.position(8); buf.get(it) }
        dataStart = 8 + n
        header = Json.parseToJsonElement(String(json)).jsonObject.filterKeys { it != "__metadata__" }.mapValues { (_, v) ->
            val o = v.jsonObject
            val shape = o["shape"]!!.jsonArray.map { it.jsonPrimitive.long }
            val (a, b) = o["data_offsets"]!!.jsonArray.map { it.jsonPrimitive.long }
            Triple(o["dtype"]!!.jsonPrimitive.content, shape, a until b)
        }
    }

    val names: Set<String> get() = header.keys

    fun tensor(name: String): Tensor {
        val (dtype, shape, range) = header[name] ?: error("missing tensor $name")
        require(dtype == "F32") { "$name is $dtype; only float32 supported" }
        val out = FloatArray(((range.last - range.first + 1) / 4).toInt())
        buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).position((dataStart + range.first).toInt()).asFloatBuffer().get(out)
        return Tensor(shape, out)
    }
}
