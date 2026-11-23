package dev.spikeai.photos.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The "vector database", made of plain SQLite + in-memory indexes:
 * SQLite keeps the vectors safe across restarts (as BLOBs); SearchEngine loads them into HNSW at start-up.
 * That's the same split real vector DBs use: durable storage + an index rebuilt or memory-mapped for speed.
 */
class PhotoStore(context: Context) : SQLiteOpenHelper(context, "photos.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE photos(
            id INTEGER PRIMARY KEY,           -- MediaStore id
            modified INTEGER NOT NULL,        -- re-index if the photo changes
            taken INTEGER NOT NULL,
            clip BLOB NOT NULL,               -- 512 floats: what the photo looks like
            text_score REAL NOT NULL,         -- how 'texty' CLIP thinks it is (OCR order)
            ocr_done INTEGER NOT NULL DEFAULT 0,
            ocr_text TEXT)""")
        db.execSQL("""CREATE TABLE chunks(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            photo_id INTEGER NOT NULL,
            text TEXT NOT NULL,
            vec BLOB NOT NULL)                -- 384 floats (MiniLM): what this piece of text means""")
        db.execSQL("CREATE INDEX chunks_photo ON chunks(photo_id)")
        db.execSQL("CREATE TABLE labels(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE tags(label_id INTEGER NOT NULL, photo_id INTEGER NOT NULL, PRIMARY KEY(label_id, photo_id))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    class Photo(val id: Long, val taken: Long, val clip: FloatArray, val textScore: Float, val ocrDone: Boolean, val ocrText: String?)
    class Chunk(val id: Long, val photoId: Long, val text: String, val vec: FloatArray)

    fun modifiedTimes(): Map<Long, Long> = readableDatabase.rawQuery("SELECT id, modified FROM photos", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getLong(0), c.getLong(1)) }
    }

    fun putPhoto(id: Long, modified: Long, taken: Long, clip: FloatArray, textScore: Float) {
        writableDatabase.transaction {
            delete("chunks", "photo_id=?", arrayOf(id.toString()))
            insertWithOnConflict("photos", null, ContentValues().apply {
                put("id", id); put("modified", modified); put("taken", taken); put("clip", clip.toBlob())
                put("text_score", textScore); put("ocr_done", 0); putNull("ocr_text")
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun putOcr(photoId: Long, text: String, chunks: List<Pair<String, FloatArray>>) {
        writableDatabase.transaction {
            delete("chunks", "photo_id=?", arrayOf(photoId.toString()))
            for ((t, v) in chunks) insert("chunks", null, ContentValues().apply { put("photo_id", photoId); put("text", t); put("vec", v.toBlob()) })
            update("photos", ContentValues().apply { put("ocr_done", 1); put("ocr_text", text) }, "id=?", arrayOf(photoId.toString()))
        }
    }

    fun deletePhotos(ids: Collection<Long>) = writableDatabase.transaction {
        for (id in ids) { delete("photos", "id=?", arrayOf(id.toString())); delete("chunks", "photo_id=?", arrayOf(id.toString())); delete("tags", "photo_id=?", arrayOf(id.toString())) }
    }

    fun photos(): List<Photo> = readableDatabase.rawQuery("SELECT id, taken, clip, text_score, ocr_done, ocr_text FROM photos ORDER BY taken DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(Photo(c.getLong(0), c.getLong(1), c.getBlob(2).toFloats(), c.getFloat(3), c.getInt(4) == 1, c.getString(5))) }
    }

    fun ocrQueue(): List<Long> = readableDatabase.rawQuery("SELECT id FROM photos WHERE ocr_done=0 ORDER BY text_score DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0)) }
    }

    fun chunks(): List<Chunk> = readableDatabase.rawQuery("SELECT id, photo_id, text, vec FROM chunks", null).use { c ->
        buildList { while (c.moveToNext()) add(Chunk(c.getLong(0), c.getLong(1), c.getString(2), c.getBlob(3).toFloats())) }
    }

    // Labels
    fun labels(): List<Pair<Long, String>> = readableDatabase.rawQuery("SELECT id, name FROM labels ORDER BY name", null).use { c ->
        buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
    }
    fun addLabel(name: String): Long = writableDatabase.insertWithOnConflict("labels", null, ContentValues().apply { put("name", name.trim()) }, SQLiteDatabase.CONFLICT_IGNORE)
    fun deleteLabel(id: Long) = writableDatabase.transaction { delete("labels", "id=?", arrayOf(id.toString())); delete("tags", "label_id=?", arrayOf(id.toString())) }
    fun tagged(labelId: Long): Set<Long> = readableDatabase.rawQuery("SELECT photo_id FROM tags WHERE label_id=?", arrayOf(labelId.toString())).use { c ->
        buildSet { while (c.moveToNext()) add(c.getLong(0)) }
    }
    fun setTag(labelId: Long, photoId: Long, on: Boolean) {
        if (on) writableDatabase.insertWithOnConflict("tags", null, ContentValues().apply { put("label_id", labelId); put("photo_id", photoId) }, SQLiteDatabase.CONFLICT_IGNORE)
        else writableDatabase.delete("tags", "label_id=? AND photo_id=?", arrayOf(labelId.toString(), photoId.toString()))
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction(); try { block(); setTransactionSuccessful() } finally { endTransaction() }
    }
}

fun FloatArray.toBlob(): ByteArray = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> forEach { b.putFloat(it) } }.array()
fun ByteArray.toFloats(): FloatArray = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().let { fb -> FloatArray(fb.remaining()).also { fb.get(it) } }
