package com.treader.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.treader.Book
import com.treader.Epub
import com.treader.LibFilter
import com.treader.R
import com.treader.Store
import com.treader.md5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ImportProgress(val total: Int, val done: Int, val name: String, val frac: Float)

enum class ImportOutcome { ADDED, DUPLICATE, FAILED }

@Composable
fun ImportBar(ip: ImportProgress, m: Modifier) {
    val anim by animateFloatAsState(ip.frac, tween(280), label = "import")
    Card(m.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.importing_books_progress, ip.done + 1, ip.total), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(ip.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ip.frac > 0f) LinearProgressIndicator({ anim }, Modifier.fillMaxWidth().clip(CircleShape))
            else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        }
    }
}

fun nameOf(c: Context, u: Uri): String = c.contentResolver.query(u, null, null, null, null)?.use {
    if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
} ?: "book"

suspend fun importBook(s: Store, c: Context, u: Uri, onProgress: (Float) -> Unit = {}): ImportOutcome {
    var dup = false
    val b = withContext(Dispatchers.IO) {
        runCatching {
            val cur = c.contentResolver.query(u, null, null, null, null)
            var name = "book"; var size = -1L
            cur?.use {
                if (it.moveToFirst()) {
                    name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    val si = it.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !it.isNull(si)) size = it.getLong(si)
                }
            }
            if (size <= 0) size = runCatching { c.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)
            if (size <= 0) size = runCatching { c.contentResolver.openAssetFileDescriptor(u, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
            val pdf = name.endsWith(".pdf", true) || c.contentResolver.getType(u) == "application/pdf"
            val id = UUID.randomUUID().toString()
            val f = File(c.filesDir, "$id." + if (pdf) "pdf" else "epub")
            c.contentResolver.openInputStream(u)!!.use { i ->
                f.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int; var last = 0f
                    while (i.read(buf).also { n = it } >= 0) {
                        o.write(buf, 0, n); done += n
                        if (size > 0) {
                            val f2 = (done.toFloat() / size).coerceIn(0f, 0.98f)
                            if (f2 - last >= 0.02f) { last = f2; onProgress(f2) }
                        }
                    }
                }
            }
            onProgress(1f)
            val hash = runCatching { md5(f) }.getOrDefault("")
            val unknownAuthor = c.getString(R.string.author_unknown)
            if (hash.isNotEmpty() && s.books.any { it.hash == hash }) {
                f.delete(); dup = true; null
            } else {
                val now = System.currentTimeMillis()
                if (pdf) {
                    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd); val n = r.pageCount; r.close(); pfd.close()
                    Book(id, name.removeSuffix(".pdf"), unknownAuthor, "", f.path, "pdf", n, addedAt = now, hash = hash)
                } else {
                    val e = Epub(f)
                    try { Book(id, e.meta("title") ?: name, e.meta("creator") ?: unknownAuthor, "", f.path, "epub", e.spine.size, addedAt = now, hash = hash) }
                    finally { e.zip.close() }
                }
            }
        }.getOrNull()
    }
    return when {
        b != null -> { s.books.add(b); s.save(); ImportOutcome.ADDED }
        dup -> ImportOutcome.DUPLICATE
        else -> ImportOutcome.FAILED
    }
}

fun Book.pr() = ((pos + off) / maxOf(n, 1)).coerceIn(0f, 1f)

object CoverCache {
    val c = object : LruCache<String, Bitmap>(6 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount / 1024 }
}

fun makeThumb(bytes: ByteArray, out: File): Boolean {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0) return false
    val o2 = BitmapFactory.Options().apply { inSampleSize = maxOf(1, o.outWidth / 480); inPreferredConfig = Bitmap.Config.RGB_565 }
    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2) ?: return false
    val sc = minOf(1f, 360f / bm.width)
    val t = if (sc < 1f) Bitmap.createScaledBitmap(bm, (bm.width * sc).toInt(), (bm.height * sc).toInt(), true) else bm
    out.outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    if (t !== bm) t.recycle(); bm.recycle()
    return true
}

fun loadCover(ctx: Context, b: Book): Bitmap? {
    if (b.type != "epub") return null
    val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
    val jpg = File(dir, b.id + ".jpg"); val none = File(dir, b.id + ".none")
    if (none.exists()) return null
    if (!jpg.exists()) {
        val ok = runCatching {
            val e = Epub(File(b.file))
            try { e.coverPath()?.let { p -> e.read(p)?.let { makeThumb(it, jpg) } } ?: false } finally { e.zip.close() }
        }.getOrDefault(false)
        if (!ok) { none.createNewFile(); return null }
    }
    return BitmapFactory.decodeFile(jpg.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
}

@Composable
fun Cover(b: Book, m: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(CoverCache.c.get(b.id), b.id) {
        if (value == null) value = withContext(Dispatchers.IO) { loadCover(ctx, b) }?.also { CoverCache.c.put(b.id, it) }
    }
    val cv = bmp
    if (cv != null) { Image(cv.asImageBitmap(), null, m.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); return }
    val h = ((b.title.hashCode() and 0x7fffffff) % 360).toFloat()
    Box(m.clip(RoundedCornerShape(14.dp)).background(
        Brush.linearGradient(listOf(Color.hsv(h, .55f, .78f), Color.hsv((h + 40) % 360, .7f, .5f)))
    ).padding(12.dp)) {
        Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 4,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(b.type.uppercase(), Modifier.align(Alignment.BottomStart), color = Color.White.copy(.75f),
            style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Library(s: Store, filter: LibFilter, onOpen: (Book) -> Unit) {
    var q by remember { mutableStateOf("") }; var tag by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Book?>(null) }
    var trashItem by remember { mutableStateOf<Book?>(null) }
    val list by remember(filter) { derivedStateOf {
        val pool = s.books.filter { book ->
            if (filter == LibFilter.Trash) book.trashed else !book.trashed && (filter != LibFilter.Fav || book.fav)
        }
        val f = pool.filter { (q.isBlank() || (it.title + it.author).contains(q, true)) && (tag.isBlank() || it.tag == tag) }
        when (filter) {
            LibFilter.RecentAdded -> f.sortedByDescending { it.addedAt }
            LibFilter.Trash -> f.sortedByDescending { it.trashedAt }
            else -> f.sortedByDescending { it.last }
        }
    } }
    val recent = if (filter == LibFilter.None) s.books.filter { !it.trashed }.maxByOrNull { it.last }
        ?.takeIf { it.last > 0 && q.isBlank() && tag.isBlank() } else null
    val tags = s.books.filter { !it.trashed }.map { it.tag }.filter { it.isNotBlank() }.distinct()
    val title = when (filter) {
        LibFilter.Fav -> stringResource(R.string.title_favorites)
        LibFilter.RecentAdded -> stringResource(R.string.title_recent_added)
        LibFilter.Trash -> stringResource(R.string.title_trash)
        else -> stringResource(R.string.title_library)
    }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.books_count, list.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search_placeholder)) },
                singleLine = true, shape = RoundedCornerShape(28.dp))
        }
        if (tags.isNotEmpty() && filter != LibFilter.Trash) item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t -> FilterChip(tag == t, { tag = if (tag == t) "" else t }, { Text(t) }, shape = RoundedCornerShape(20.dp)) }
            }
        }
        if (recent != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Card(Modifier.fillMaxWidth().clickable { onOpen(recent) }, colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cover(recent, Modifier.width(92.dp).aspectRatio(0.7f))
                    Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.reading_now), style = MaterialTheme.typography.labelSmall)
                        Text(recent.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(recent.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        LinearProgressIndicator({ recent.pr() }, Modifier.fillMaxWidth().clip(CircleShape))
                        Text(stringResource(R.string.reading_stats_summary, (recent.pr() * 100).toInt(), (recent.sec / 60).toInt()), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (filter == LibFilter.Trash) stringResource(R.string.trash_empty) else stringResource(R.string.library_empty),
                Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { it.id }) { b ->
            if (filter == LibFilter.Trash) Column(Modifier.clickable { trashItem = b }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Cover(b, Modifier.fillMaxWidth().aspectRatio(0.7f).graphicsLayer { alpha = 0.55f })
                Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            } else Column(Modifier.combinedClickable(onClick = { onOpen(b) }, onLongClick = { edit = b }), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    Cover(b, Modifier.fillMaxWidth().aspectRatio(0.7f))
                    IconButton({ s.update(b.copy(fav = !b.fav)) }, Modifier.align(Alignment.TopEnd).size(32.dp)) {
                        Text(if (b.fav) "♥" else "♡", color = if (b.fav) Color(0xFFE0546A) else Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Text(b.author + if (b.tag.isNotBlank()) " · #${b.tag}" else "", style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator({ b.pr() }, Modifier.fillMaxWidth().clip(CircleShape))
            }
        }
    }
    edit?.let { b ->
        var a by remember { mutableStateOf(b.author) }; var t by remember { mutableStateOf(b.tag) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(b.title, maxLines = 2) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it }, label = { Text(stringResource(R.string.edit_author_label)) })
                OutlinedTextField(t, { t = it }, label = { Text(stringResource(R.string.edit_tag_label)) })
            } },
            confirmButton = { TextButton({ s.update(b.copy(author = a, tag = t.trim())); edit = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({
                s.update(b.copy(trashed = true, trashedAt = System.currentTimeMillis())); edit = null
            }) { Text(stringResource(R.string.move_to_trash), color = MaterialTheme.colorScheme.error) } })
    }
    trashItem?.let { b ->
        var confirmWipe by remember { mutableStateOf(false) }
        if (!confirmWipe) AlertDialog(onDismissRequest = { trashItem = null }, title = { Text(b.title, maxLines = 2) },
            text = { Text(stringResource(R.string.in_trash_message)) },
            confirmButton = { TextButton({ s.update(b.copy(trashed = false)); trashItem = null }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton({ confirmWipe = true }) { Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error) } })
        else AlertDialog(onDismissRequest = { trashItem = null }, title = { Text(stringResource(R.string.delete_book_title, b.title)) },
            text = { Text(stringResource(R.string.delete_book_confirm)) },
            confirmButton = { TextButton({
                File(b.file).delete()
                File(s.ctx.filesDir, "covers/${b.id}.jpg").delete(); File(s.ctx.filesDir, "covers/${b.id}.none").delete()
                s.bookmarks.removeAll { it.bookId == b.id }
                s.books.remove(b); s.save(); trashItem = null
            }) { Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ trashItem = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
