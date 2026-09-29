package com.treader

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Book(
    val id: String, val title: String, val author: String, val tag: String,
    val file: String, val type: String, val n: Int,
    val pos: Int = 0, val off: Float = 0f, val last: Long = 0, val sec: Long = 0
) {
    fun j() = JSONObject().put("id", id).put("title", title).put("author", author).put("tag", tag)
        .put("file", file).put("type", type).put("n", n).put("pos", pos).put("off", off.toDouble())
        .put("last", last).put("sec", sec)

    companion object {
        fun of(o: JSONObject) = Book(
            o.getString("id"), o.getString("title"), o.getString("author"), o.getString("tag"),
            o.getString("file"), o.getString("type"), o.getInt("n"), o.getInt("pos"),
            o.getDouble("off").toFloat(), o.getLong("last"), o.getLong("sec")
        )
    }
}

data class Prefs(
    val font: String = "serif", val size: Int = 18, val line: Float = 1.6f,
    val margin: Int = 16, val theme: Int = 0, val goal: Int = 20, val img: Int = 1,
    val remindOn: Boolean = false, val remindHour: Int = 20, val remindMin: Int = 0
)

data class Bookmark(val id: String, val bookId: String, val pos: Int, val off: Float, val label: String, val time: Long) {
    fun j() = JSONObject().put("id", id).put("bookId", bookId).put("pos", pos).put("off", off.toDouble())
        .put("label", label).put("time", time)
    companion object {
        fun of(o: JSONObject) = Bookmark(o.getString("id"), o.getString("bookId"), o.getInt("pos"),
            o.getDouble("off").toFloat(), o.getString("label"), o.getLong("time"))
    }
}

class Store(val ctx: Context) {
    private val f = File(ctx.filesDir, "lib.json")
    val books = mutableStateListOf<Book>()
    var prefs by mutableStateOf(Prefs())
    val daily = mutableStateMapOf<String, Long>()
    val bookmarks = mutableStateListOf<Bookmark>()

    init { runCatching { load(f.readText(), false) } }

    fun today() = LocalDate.now().toString()

    fun json(): String {
        val d = JSONObject(); daily.forEach { (k, v) -> d.put(k, v) }
        val p = prefs
        return JSONObject().put("books", JSONArray(books.map { it.j() })).put("daily", d)
            .put("prefs", JSONObject().put("font", p.font).put("size", p.size).put("line", p.line.toDouble())
                .put("margin", p.margin).put("theme", p.theme).put("goal", p.goal).put("img", p.img)
                .put("remindOn", p.remindOn).put("remindHour", p.remindHour).put("remindMin", p.remindMin))
            .put("bookmarks", JSONArray(bookmarks.map { it.j() })).toString()
    }

    /** merge=true: nhập file sao lưu, chỉ lấy vị trí đọc/thẻ mới hơn cho sách đã có trên máy. */
    fun load(s: String, merge: Boolean) {
        val o = JSONObject(s); val arr = o.getJSONArray("books")
        for (i in 0 until arr.length()) {
            val b = Book.of(arr.getJSONObject(i)); val k = books.indexOfFirst { it.id == b.id }
            if (!merge) books.add(b)
            else if (k >= 0 && b.last > books[k].last)
                books[k] = books[k].copy(pos = b.pos, off = b.off, last = b.last,
                    sec = maxOf(b.sec, books[k].sec), tag = b.tag, author = b.author)
        }
        val d = o.getJSONObject("daily")
        d.keys().forEach { daily[it] = maxOf(d.getLong(it), daily[it] ?: 0) }
        if (o.has("bookmarks")) {
            val ba = o.getJSONArray("bookmarks")
            for (i in 0 until ba.length()) {
                val bm = Bookmark.of(ba.getJSONObject(i))
                if (bookmarks.none { it.id == bm.id }) bookmarks.add(bm)
            }
        }
        if (!merge) {
            val p = o.getJSONObject("prefs")
            prefs = Prefs(p.getString("font"), p.getInt("size"), p.getDouble("line").toFloat(),
                p.getInt("margin"), p.getInt("theme"), p.getInt("goal"), p.optInt("img", 1),
                p.optBoolean("remindOn", false), p.optInt("remindHour", 20), p.optInt("remindMin", 0))
        }
    }

    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var last = ""
    /** Ghi nền, ghi nguyên tử (tmp -> rename), bỏ qua nếu dữ liệu không đổi -> ít hao ổ đĩa. */
    fun save() {
        val j = json()
        io.execute {
            if (j != last) { val t = File(f.path + ".tmp"); t.writeText(j); t.renameTo(f); last = j }
        }
    }
    fun update(b: Book, persist: Boolean = true) {
        val i = books.indexOfFirst { it.id == b.id }; if (i >= 0) books[i] = b; if (persist) save()
    }
}