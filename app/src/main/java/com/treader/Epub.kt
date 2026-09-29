package com.treader

import android.net.Uri
import java.io.File
import java.util.zip.ZipFile

fun norm(p: String): String {
    val st = ArrayList<String>()
    p.split("/").forEach {
        when (it) { "", "." -> {}; ".." -> if (st.isNotEmpty()) st.removeAt(st.size - 1); else -> st.add(it) }
    }
    return st.joinToString("/")
}

class Epub(file: File) {
    val zip = ZipFile(file)
    fun read(p: String) = zip.getEntry(p)?.let { zip.getInputStream(it).readBytes() }
    fun stream(p: String) = zip.getEntry(p)?.let { zip.getInputStream(it) }
    private fun txt(p: String) = read(p)?.let { String(it) }
    private fun attr(t: String, n: String) =
        Regex("\\b$n\\s*=\\s*\"([^\"]*)\"").find(t)?.groupValues?.get(1)

    private val opf = Regex("full-path=\"([^\"]+)\"").find(txt("META-INF/container.xml")!!)!!.groupValues[1]
    private val dir = opf.substringBeforeLast('/', "")
    private val x = txt(opf)!!
    private fun rel(base: String, h: String) = norm(base + "/" + Uri.decode(h))
    private val items = Regex("<item\\b[^>]*>").findAll(x).map { it.value }.toList()
    private val byId = items.associate { (attr(it, "id") ?: "") to rel(dir, attr(it, "href") ?: "") }
    val spine = Regex("<itemref\\b[^>]*>").findAll(x).mapNotNull { byId[attr(it.value, "idref")] }.toList()

    /** Ảnh bìa: EPUB3 properties="cover-image" -> EPUB2 <meta name="cover"> -> item có chữ "cover". */
    fun coverPath(): String? {
        val id = Regex("<meta[^>]*name=\"cover\"[^>]*>").find(x)?.value?.let { attr(it, "content") }
        val img = items.filter { attr(it, "media-type")?.startsWith("image/") == true }
        val c = img.firstOrNull { attr(it, "properties")?.contains("cover-image") == true }
            ?: img.firstOrNull { attr(it, "id") == id }
            ?: img.firstOrNull { ((attr(it, "id") ?: "") + (attr(it, "href") ?: "")).contains("cover", true) }
        return c?.let { rel(dir, attr(it, "href")!!) }
    }

    fun meta(t: String) = Regex("<dc:$t[^>]*>([\\s\\S]*?)</dc:$t>").find(x)?.groupValues?.get(1)?.trim()

    /** Mục lục lấy từ NCX; không có thì chia theo spine. */
    val toc: List<Pair<String, Int>> = run {
        val np = items.firstOrNull { attr(it, "media-type") == "application/x-dtbncx+xml" }
            ?.let { rel(dir, attr(it, "href")!!) }
        val nd = np?.substringBeforeLast('/', "") ?: ""
        val t = np?.let { txt(it) }
        val l = if (t == null) emptyList() else
            Regex("<navPoint[\\s\\S]*?<text>([\\s\\S]*?)</text>[\\s\\S]*?<content[^>]*src=\"([^\"#]*)")
                .findAll(t).mapNotNull {
                    val i = spine.indexOf(rel(nd, it.groupValues[2]))
                    if (i < 0) null else it.groupValues[1].trim() to i
                }.toList()
        l.ifEmpty { spine.indices.map { "Phần ${it + 1}" to it } }
    }

    /** Tìm chữ trong toàn sách -> (chương, đoạn trích). */
    fun search(q: String): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        if (q.length < 2) return out
        spine.forEachIndexed { ci, p ->
            val t = (txt(p) ?: "").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
            var i = t.indexOf(q, 0, true); var k = 0
            while (i >= 0 && k < 5 && out.size < 100) {
                out.add(ci to "…" + t.substring(maxOf(0, i - 30), minOf(t.length, i + q.length + 50)) + "…")
                k++; i = t.indexOf(q, i + q.length, true)
            }
        }
        return out
    }
}
