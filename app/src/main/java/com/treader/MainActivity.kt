@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package com.treader

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.GestureDetector
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import android.view.MotionEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import androidx.compose.runtime.key
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.unit.sp

object Active { @Volatile var on = true }
object Flush { var fn: (() -> Unit)? = null }
enum class LibFilter { None, Fav, RecentAdded, Trash }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        Reminder.createChannel(this)
        val s = Store(this)
        Thread { s.backfillHashes() }.start()   // tính mã băm bù cho sách nhập từ trước, để chống trùng áp dụng luôn cho sách cũ
        setContent { TTheme { Surface(Modifier.fillMaxSize()) { App(s) } } }
    }
    override fun onPause() { Active.on = false; Flush.fn?.invoke(); super.onPause() }
    override fun onResume() { super.onResume(); Active.on = true }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Hệ thống báo sắp thiếu RAM -> nhả bớt cache ảnh bìa, không ảnh hưởng sách đang đọc
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) CoverCache.c.evictAll()
    }
}

@Composable
fun TTheme(c: @Composable () -> Unit) {
    val d = androidx.compose.foundation.isSystemInDarkTheme(); val ctx = LocalContext.current
    val cs = when {
        Build.VERSION.SDK_INT >= 31 -> if (d) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        d -> darkColorScheme(primary = Color(0xFF9FA8FF))
        else -> lightColorScheme(primary = Color(0xFF4A56E2))
    }
    MaterialTheme(cs, shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = c)
}

@Composable
fun App(s: Store) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpNonce by rememberSaveable { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var libFilter by rememberSaveable { mutableStateOf(LibFilter.None) }
    val ctx = LocalContext.current; val sc = rememberCoroutineScope()
    var importing by remember { mutableStateOf<ImportProgress?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) sc.launch {
            var dup = 0
            us.forEachIndexed { i, u ->
                importing = ImportProgress(us.size, i, nameOf(ctx, u), 0f)
                if (importBook(s, ctx, u) { f -> importing = importing?.copy(frac = f) } == ImportOutcome.DUPLICATE) dup++
            }
            importing = null
            if (dup > 0) {
                importMsg = if (dup == 1) "Đã bỏ qua 1 sách vì đã có sẵn trong thư viện" else "Đã bỏ qua $dup sách vì đã có sẵn trong thư viện"
                delay(2500); importMsg = null
            }
        }
    }
    fun doImport() = pick.launch(arrayOf("application/epub+zip", "application/pdf"))
    fun goLib(f: LibFilter) { libFilter = f; tab = 1 }
    BackHandler(openId != null) { openId = null }
    val o = openId?.let { id -> s.books.firstOrNull { it.id == id } }
    if (o != null) key(o.id, jumpNonce) { Reader(s, o, { jumpNonce++ }) { openId = null } }
    else Box(Modifier.fillMaxSize()) { Scaffold(
        bottomBar = { NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0 }, { Text("🏠") }, label = { Text("Trang chủ") })
            NavigationBarItem(tab == 1, { tab = 1; libFilter = LibFilter.None }, { Text("📚") }, label = { Text("Giá sách") })
            NavigationBarItem(tab == 2, { tab = 2 }, { Text("🔖") }, label = { Text("Ghi chú") })
            NavigationBarItem(tab == 3, { tab = 3 }, { Text("⚙️") }, label = { Text("Cài đặt") })
        } },
        floatingActionButton = { if (tab == 0 || tab == 1) ExtendedFloatingActionButton({ doImport() }) { Text("＋  Nhập sách") } }
    ) { pad -> Box(Modifier.padding(pad)) {
        when (tab) {
            0 -> Home(s, { openId = it.id }, { f -> goLib(f) }, { doImport() })
            1 -> Library(s, libFilter) { openId = it.id }
            2 -> Notes(s) { b, bm -> s.update(b.copy(pos = bm.pos, off = bm.off)); openId = b.id }
            else -> Settings(s)
        }
    } }
        importing?.let { ip -> ImportBar(ip, Modifier.align(Alignment.BottomCenter).padding(16.dp, 0.dp, 16.dp, 88.dp)) }
        importMsg?.let { msg -> Card(Modifier.align(Alignment.BottomCenter).padding(16.dp, 0.dp, 16.dp, 88.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(6.dp)) { Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
    }
}

@Composable
fun IconButtonText(icon: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(40.dp)) { Text(icon, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun QuickAction(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(74.dp).clickable(onClick = onClick)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(icon, color = color, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

// ---------- Trang chủ ----------
@Composable
fun Home(s: Store, onOpen: (Book) -> Unit, onFilter: (LibFilter) -> Unit, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val active by remember { derivedStateOf { s.books.filter { !it.trashed } } }
    val recent = active.maxByOrNull { it.last }?.takeIf { it.last > 0 }
    val recentAdded = active.sortedByDescending { it.addedAt }.take(8)
    var menu by remember { mutableStateOf(false) }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(s.json().toByteArray()) } }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { runCatching { s.load(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText(), true); s.save() } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 96.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trang chủ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                IconButtonText("⋮") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Xuất sao lưu") }, onClick = { menu = false; exp.launch("treader-backup.json") })
                    DropdownMenuItem(text = { Text("Nhập sao lưu") }, onClick = { menu = false; imp.launch(arrayOf("application/json", "*/*")) })
                }
            }
            IconButtonText("🔍") { onFilter(LibFilter.None) }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onImport) { Text("＋ Thêm") }
        }
        Spacer(Modifier.height(14.dp))
        if (recent != null) Card(Modifier.fillMaxWidth().clickable { onOpen(recent) }, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(recent, Modifier.width(92.dp).aspectRatio(0.7f))
                Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recent.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recent.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text("Tiến độ đọc: ${(recent.pr() * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator({ recent.pr() }, Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
        } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Text("Chưa đọc cuốn nào. Nhập sách để bắt đầu.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction("♡", "Mục yêu thích\ncủa tôi", Color(0xFFC2536E)) { onFilter(LibFilter.Fav) }
            QuickAction("⤓", "Thêm gần đây", Color(0xFF2F8FB3)) { onFilter(LibFilter.RecentAdded) }
            QuickAction("↗", "Đọc gần đây", Color(0xFF2FA372)) { onFilter(LibFilter.None) }
            QuickAction("🗑", "Thùng rác\ncủa tôi", Color(0xFFB3902F)) { onFilter(LibFilter.Trash) }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(14.dp)) {
                Text("Thêm gần đây", fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
                if (recentAdded.isEmpty()) Text("Chưa có sách nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentAdded.forEach { b -> Column(Modifier.width(96.dp).clickable { onOpen(b) }) {
                        Cover(b, Modifier.fillMaxWidth().aspectRatio(0.7f))
                        Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp))
                    } }
                }
                Text("Tổng cộng ${active.size} cuốn sách", Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------- Ghi chú (đánh dấu trang, gộp toàn bộ thư viện) ----------
@Composable
fun Notes(s: Store, onOpen: (Book, Bookmark) -> Unit) {
    val marks by remember { derivedStateOf { s.bookmarks.sortedByDescending { it.time } } }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item { Text("Ghi chú", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
        item { Text("Các trang đã đánh dấu trong toàn bộ sách", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)) }
        if (marks.isEmpty()) item { Text("Chưa có trang đánh dấu nào. Khi đọc sách, bấm 🔖 để lưu lại.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp)) }
        items(marks, key = { it.id }) { bm ->
            val book = s.books.firstOrNull { it.id == bm.bookId && !it.trashed }
            if (book != null) Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOpen(book, bm) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(book, Modifier.width(44.dp).aspectRatio(0.7f))
                    Column(Modifier.weight(1f)) {
                        Text(bm.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(book.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(bm.time)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ s.bookmarks.remove(bm); s.save() }) { Text("✕") }
                }
            }
        }
    }
}

data class ImportProgress(val total: Int, val done: Int, val name: String, val frac: Float)

@Composable
fun ImportBar(ip: ImportProgress, m: Modifier) {
    // Chép file rất nhanh với sách nhỏ -> làm mượt để mắt luôn thấy thanh chạy, không bị nhảy thẳng
    val anim by animateFloatAsState(ip.frac, tween(280), label = "import")
    Card(m.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Đang nhập sách ${ip.done + 1}/${ip.total}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
            Text(ip.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ip.frac > 0f) LinearProgressIndicator({ anim }, Modifier.fillMaxWidth().clip(CircleShape))
            else LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        }
    }
}

// ---------- Thư viện ----------
fun nameOf(c: Context, u: Uri): String = c.contentResolver.query(u, null, null, null, null)?.use {
    if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
} ?: "book"

enum class ImportOutcome { ADDED, DUPLICATE, FAILED }

fun md5(f: File): String {
    val md = java.security.MessageDigest.getInstance("MD5")
    f.inputStream().use { ins -> val buf = ByteArray(64 * 1024); var n: Int
        while (ins.read(buf).also { n = it } >= 0) md.update(buf, 0, n) }
    return md.digest().joinToString("") { "%02x".format(it) }
}

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
            // Cursor không cho dung lượng thật (hay gặp với 1 số trình quản lý file) -> lấy thẳng kích thước file qua hệ thống
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
                            if (f2 - last >= 0.02f) { last = f2; onProgress(f2) }   // báo thưa ra để khỏi vẽ lại UI liên tục
                        }
                    }
                }
            }
            onProgress(1f)
            // So khớp nội dung file (không phải tên file) với sách đã có -> phát hiện đúng cả khi đổi tên file
            val hash = runCatching { md5(f) }.getOrDefault("")
            if (hash.isNotEmpty() && s.books.any { it.hash == hash }) {
                f.delete(); dup = true; null
            } else {
                val now = System.currentTimeMillis()
                if (pdf) {
                    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = PdfRenderer(pfd); val n = r.pageCount; r.close(); pfd.close()
                    Book(id, name.removeSuffix(".pdf"), "Không rõ", "", f.path, "pdf", n, addedAt = now, hash = hash)
                } else {
                    val e = Epub(f)
                    try { Book(id, e.meta("title") ?: name, e.meta("creator") ?: "Không rõ", "", f.path, "epub", e.spine.size, addedAt = now, hash = hash) }
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

object CoverCache {   // ~6MB, bitmap RGB_565 ~ 0.4MB/ảnh
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

/** Trích bìa 1 lần -> lưu thumbnail JPEG nhỏ (~20-40KB) vào filesDir/covers. Không có bìa thì đánh dấu .none để khỏi thử lại. */
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
    Box(m.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(
        listOf(Color.hsv(h, .55f, .78f), Color.hsv((h + 40) % 360, .7f, .5f)))).padding(12.dp)) {
        Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 4,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Text(b.type.uppercase(), Modifier.align(Alignment.BottomStart), color = Color.White.copy(.75f),
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun Library(s: Store, filter: LibFilter, onOpen: (Book) -> Unit) {
    var q by remember { mutableStateOf("") }; var tag by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Book?>(null) }
    var trashItem by remember { mutableStateOf<Book?>(null) }   // sách trong thùng rác đang bấm giữ
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
    val title = when (filter) { LibFilter.Fav -> "Yêu thích"; LibFilter.RecentAdded -> "Thêm gần đây"; LibFilter.Trash -> "Thùng rác"; else -> "Giá sách" }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) { Column {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("${list.size} cuốn sách", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Tìm tên sách, tác giả…") },
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
                        Text("ĐANG ĐỌC", style = MaterialTheme.typography.labelSmall)
                        Text(recent.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(recent.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        LinearProgressIndicator({ recent.pr() }, Modifier.fillMaxWidth().clip(CircleShape))
                        Text("${(recent.pr() * 100).toInt()}% · ${recent.sec / 60} phút đã đọc", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (filter == LibFilter.Trash) "Thùng rác trống." else "Chưa có sách nào.\nBấm “Nhập sách” để thêm EPUB / PDF.",
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
                OutlinedTextField(a, { a = it }, label = { Text("Tác giả / bộ sách") })
                OutlinedTextField(t, { t = it }, label = { Text("Thẻ (thể loại)") })
            } },
            confirmButton = { TextButton({ s.update(b.copy(author = a, tag = t.trim())); edit = null }) { Text("Lưu") } },
            dismissButton = { TextButton({
                s.update(b.copy(trashed = true, trashedAt = System.currentTimeMillis())); edit = null
            }) { Text("Chuyển vào thùng rác", color = MaterialTheme.colorScheme.error) } })
    }
    trashItem?.let { b ->
        var confirmWipe by remember { mutableStateOf(false) }
        if (!confirmWipe) AlertDialog(onDismissRequest = { trashItem = null }, title = { Text(b.title, maxLines = 2) },
            text = { Text("Sách này đang ở trong thùng rác.") },
            confirmButton = { TextButton({ s.update(b.copy(trashed = false)); trashItem = null }) { Text("Khôi phục") } },
            dismissButton = { TextButton({ confirmWipe = true }) { Text("Xoá vĩnh viễn", color = MaterialTheme.colorScheme.error) } })
        else AlertDialog(onDismissRequest = { trashItem = null }, title = { Text("Xoá \"${b.title}\"?") },
            text = { Text("Sách, tiến độ đọc và trang đánh dấu của cuốn này sẽ bị xoá vĩnh viễn khỏi máy, không thể khôi phục.") },
            confirmButton = { TextButton({
                File(b.file).delete()
                File(s.ctx.filesDir, "covers/${b.id}.jpg").delete(); File(s.ctx.filesDir, "covers/${b.id}.none").delete()
                s.bookmarks.removeAll { it.bookId == b.id }
                s.books.remove(b); s.save(); trashItem = null
            }) { Text("Xoá vĩnh viễn", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ trashItem = null }) { Text("Huỷ") } })
    }
}

// ---------- Trình đọc ----------
fun themeColors(t: Int) = when (t) {
    0 -> Color.White to Color(0xFF111111)
    1 -> Color(0xFF121212) to Color(0xFFDDDDDD)
    else -> Color(0xFFF4ECD8) to Color(0xFF3B3226)
}
fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
fun css(p: Prefs): String {
    val (bg, fg) = themeColors(p.theme)
    val img = when (p.img) {
        0 -> "img,svg,image{display:none!important}"
        1 -> "img{display:block!important;max-width:100%!important;max-height:38vh!important;width:auto!important;height:auto!important;margin:.8em auto!important;border-radius:10px}"
        else -> "img{display:block!important;max-width:100%!important;height:auto!important;margin:.8em auto!important}"
    }
    val dim = if (p.theme == 1) "img{filter:brightness(.85)}" else ""
    return "html,body{background:${hex(bg)}!important;color:${hex(fg)}!important;font-family:${p.font}!important;" +
            "font-size:${p.size}px!important;line-height:${p.line}!important;padding:12px ${p.margin}px 56px!important;margin:0!important;" +
            "word-wrap:break-word;-webkit-text-size-adjust:100%}" +
            "p,div,span,li{font-family:inherit!important}p{margin:0 0 .9em!important;text-indent:0!important}" +
            "h1,h2,h3{text-align:center!important;font-weight:600!important;line-height:1.3!important;margin:1.1em 0 .9em!important}" +
            "h1{font-size:1.4em!important}h2{font-size:1.2em!important}h3{font-size:1.1em!important}" +
            "a{color:inherit!important;text-decoration:none!important}" + img + dim
}

class ReadState(var pos: Int, var off: Float) { var sec = 0L; var saved = 0L }

@Composable
fun Reader(s: Store, b0: Book, remount: () -> Unit, close: () -> Unit) {
    PdfPath.cur = b0.file
    val epub = remember { if (b0.type == "epub") Epub(File(b0.file)) else null }
    val st = remember { ReadState(b0.pos, b0.off) }
    var ch by remember { mutableIntStateOf(b0.pos) }
    var dlg by remember { mutableIntStateOf(0) }
    var bars by remember { mutableStateOf(true) }   // false = chế độ đọc toàn màn hình
    val view = LocalView.current
    BackHandler(!bars) { bars = true }
    DisposableEffect(bars) {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        view.keepScreenOn = true
        if (bars) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()); view.keepScreenOn = false }
    }
    // Vị trí/thời gian giữ ngoài state -> cuộn không làm recompose; chỉ ghi đĩa mỗi 60s và khi thoát/tạm dừng.
    val persist = remember { {
        val add = st.sec - st.saved; st.saved = st.sec
        val cur = s.books.firstOrNull { it.id == b0.id } ?: b0
        if (add > 0) s.daily[s.today()] = (s.daily[s.today()] ?: 0) + add
        s.update(cur.copy(pos = st.pos, off = st.off, last = System.currentTimeMillis(), sec = cur.sec + add))
    } }
    var jump by remember { mutableFloatStateOf(0f) }   // 1f = vào chương ở cuối (khi lùi chương)
    fun go(i: Int, o: Float = 0f) { jump = o; ch = i; st.pos = i; st.off = o }
    val marks by remember { derivedStateOf { s.bookmarks.filter { it.bookId == b0.id }.sortedBy { it.pos + it.off } } }
    fun addBookmark() {
        val label = if (epub != null) (epub.toc.lastOrNull { it.second <= ch }?.first ?: "Chương ${ch + 1}") else "Trang ${st.pos + 1}"
        s.bookmarks.add(Bookmark(UUID.randomUUID().toString(), b0.id, if (epub != null) ch else st.pos,
            if (epub != null) st.off else 0f, label, System.currentTimeMillis()))
        s.save()
    }
    fun gotoBookmark(bm: Bookmark) {
        val cur = s.books.firstOrNull { it.id == b0.id } ?: b0
        s.update(cur.copy(pos = bm.pos, off = bm.off)); dlg = 0; remount()
    }
    LaunchedEffect(Unit) { var t = 0; while (true) { delay(1000); if (Active.on) { st.sec++; if (++t % 60 == 0) persist() } } }
    DisposableEffect(Unit) { Flush.fn = persist; onDispose { persist(); Flush.fn = null; epub?.zip?.close() } }
    val (bg, fg) = themeColors(s.prefs.theme)
    Column(Modifier.fillMaxSize().background(bg).systemBarsPadding()) {
        if (bars) Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(close) { Text("←", color = fg) }
            Text(b0.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg, fontWeight = FontWeight.Medium)
            if (epub != null) { TextButton({ dlg = 1 }) { Text("☰", color = fg) }; TextButton({ dlg = 2 }) { Text("🔍") } }
            TextButton({ dlg = 3 }) { Text("Aa", color = fg) }
            TextButton({ dlg = 4 }) { Text("🔖", color = fg) }
            TextButton({ bars = false }) { Text("⛶", color = fg) }
        }
        if (bars && epub != null) LinearProgressIndicator({ (ch + 1f) / epub.spine.size }, Modifier.fillMaxWidth().height(2.dp))
        Box(Modifier.weight(1f)) {
            if (epub == null) PdfView(b0.pos, s.prefs.theme == 1, !bars, { bars = !bars }) { st.pos = it }
            else { val fadeA = remember { Animatable(0f) }
                LaunchedEffect(ch) { fadeA.snapTo(0f); fadeA.animateTo(1f, tween(220)) }
                Box(Modifier.graphicsLayer { alpha = fadeA.value }.fillMaxSize()) {
                    EpubView(s, epub, ch, b0.off, !bars, jump, { bars = !bars }, { d ->
                        val n = ch + d; if (n in 0 until epub.spine.size) go(n, if (d < 0) 1f else 0f) },
                        { i -> if (bars && i != ch) go(i) }) { st.off = it }
                } }
            if (!bars && epub != null) Text("${ch + 1}/${epub.spine.size}", Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                color = fg.copy(.45f), style = MaterialTheme.typography.labelSmall)
        }
        if (bars && epub != null) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton({ if (ch > 0) go(ch - 1) }) { Text("‹") }
            Text("Chương ${ch + 1} / ${epub.spine.size}", color = fg, style = MaterialTheme.typography.labelLarge)
            FilledTonalButton({ if (ch < epub.spine.size - 1) go(ch + 1) }) { Text("›") }
        }
    }
    if (dlg == 1 && epub != null) ModalBottomSheet({ dlg = 0 }) {
        Text("Mục lục", Modifier.padding(16.dp, 0.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn { items(epub.toc) { (t, i) ->
            Text(t, Modifier.fillMaxWidth().clickable { go(i); dlg = 0 }.padding(16.dp, 12.dp),
                color = if (i == ch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        } }
    }
    if (dlg == 2 && epub != null) {
        var q by remember { mutableStateOf("") }
        var res by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
        val sc = rememberCoroutineScope()
        ModalBottomSheet({ dlg = 0 }) {
            Row(Modifier.padding(16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Tìm trong sách") }, shape = RoundedCornerShape(28.dp))
                Button({ sc.launch { res = withContext(Dispatchers.IO) { epub.search(q) } } }) { Text("Tìm") }
            }
            LazyColumn { items(res) { (c, t) ->
                Text(t, Modifier.fillMaxWidth().clickable { go(c); dlg = 0 }.padding(16.dp, 10.dp), style = MaterialTheme.typography.bodyMedium)
            } }
        }
    }
    if (dlg == 3) ModalBottomSheet({ s.save(); dlg = 0 }) {
        val p = s.prefs
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                (0..2).forEach { i ->
                    val (b, f) = themeColors(i)
                    Box(Modifier.size(48.dp).clip(CircleShape).background(b)
                        .border(if (p.theme == i) 3.dp else 1.dp, if (p.theme == i) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { s.prefs = p.copy(theme = i) }, contentAlignment = Alignment.Center) { Text("A", color = f, fontWeight = FontWeight.Bold) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("serif", "sans-serif", "monospace").forEach { f ->
                FilterChip(p.font == f, { s.prefs = p.copy(font = f) }, { Text(f) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ảnh"); listOf("Ẩn", "Gọn", "Đầy đủ").forEachIndexed { i, n -> FilterChip(p.img == i, { s.prefs = p.copy(img = i) }, { Text(n) }) } }
            Text("Cỡ chữ ${p.size}"); Slider(p.size.toFloat(), { s.prefs = p.copy(size = it.toInt()) }, valueRange = 12f..32f)
            Text("Giãn dòng %.1f".format(p.line)); Slider(p.line, { s.prefs = p.copy(line = it) }, valueRange = 1.2f..2.2f)
            Text("Lề ${p.margin}"); Slider(p.margin.toFloat(), { s.prefs = p.copy(margin = it.toInt()) }, valueRange = 0f..40f)
        }
    }
    if (dlg == 4) ModalBottomSheet({ dlg = 0 }) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Đánh dấu trang", style = MaterialTheme.typography.titleLarge)
            FilledTonalButton({ addBookmark() }) { Text("+ Đánh dấu") }
        }
        if (marks.isEmpty()) Text("Chưa có trang đánh dấu nào ở sách này.", Modifier.padding(16.dp, 4.dp, 16.dp, 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.padding(bottom = 12.dp)) { items(marks, key = { it.id }) { bm ->
            Row(Modifier.fillMaxWidth().clickable { gotoBookmark(bm) }.padding(16.dp, 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bm.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(bm.time)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton({ s.bookmarks.remove(bm); s.save() }) { Text("✕") }
            }
        } }
    }
}

@Composable
fun EpubView(s: Store, epub: Epub, ch: Int, off0: Float, full: Boolean, jumpOff: Float,
             onTap: () -> Unit, onNav: (Int) -> Unit, onLink: (Int) -> Unit, onOff: (Float) -> Unit) {
    val tapCb = rememberUpdatedState(onTap)
    val fullS = rememberUpdatedState(full)
    val navCb = rememberUpdatedState(onNav)
    val linkCb = rememberUpdatedState(onLink)
    val loaded = remember { intArrayOf(-1) }
    val restore = remember { floatArrayOf(off0) }
    val lastCss = remember { arrayOf("") }
    val cb = rememberUpdatedState(onOff)
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        WebView(c).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.blockNetworkLoads = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            val dp = resources.displayMetrics.density
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            scrollBarDefaultDelayBeforeFade = 700
            scrollBarFadeDuration = 350
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
            scrollBarSize = (5 * dp).toInt()
            if (Build.VERSION.SDK_INT >= 29) verticalScrollbarThumbDrawable =
                android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8f * dp; setColor(0xB0808080.toInt()) }
            setBackgroundColor(0)
            overScrollMode = View.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
                // Không bao giờ để WebView tự chuyển trang theo link (link mục lục cuối chương rất dễ bị chạm nhầm khi vuốt/lật trang).
                // Link nội bộ chỉ được theo khi KHÔNG ở chế độ toàn màn hình, và đi qua app để số chương/vị trí luôn đúng.
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (r.url.host == "epub.local") {
                        val i = epub.spine.indexOf(Uri.decode(r.url.path.orEmpty().removePrefix("/")))
                        if (i >= 0) linkCb.value(i)
                    }
                    return true
                }
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                    val path = Uri.decode(r.url.path.orEmpty().removePrefix("/"))
                    val html = path.endsWith("html", true) || path.endsWith("htm", true)
                    if (!html) {   // ảnh/css: stream thẳng từ zip, không nạp cả file vào RAM
                        val ins = epub.stream(path) ?: return WebResourceResponse("text/plain", "UTF-8", 404, "NF", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        return WebResourceResponse(URLConnection.guessContentTypeFromName(path) ?: "application/octet-stream", null, ins)
                    }
                    val txt = epub.read(path)?.let { String(it) } ?: return null
                    val inj = "<meta name='viewport' content='width=device-width,initial-scale=1'><style id='tr'>${css(s.prefs)}</style>"
                    val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(txt)
                    val out = if (m != null) txt.substring(0, m.range.last + 1) + inj + txt.substring(m.range.last + 1) else inj + txt
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(out.toByteArray()))
                }
                override fun onPageFinished(v: WebView, url: String) {
                    // chế độ "Gọn": ẩn logo/icon nhỏ (<=120px) chen giữa chữ
                    if (s.prefs.img == 1) v.evaluateJavascript("document.querySelectorAll('img').forEach(function(i){function f(){if(i.naturalWidth<=120&&i.naturalHeight<=120)i.style.display='none'}i.complete?f():i.addEventListener('load',f)})", null)
                    val f = restore[0]; restore[0] = 0f
                    if (f > 0f) v.postDelayed({
                        val h = (v.contentHeight * v.resources.displayMetrics.density - v.height).coerceAtLeast(1f)
                        v.scrollTo(0, (f * h).toInt())
                    }, 150)
                }
            }
            setOnScrollChangeListener { _, _, y, _, _ ->
                val h = (contentHeight * resources.displayMetrics.density - height).coerceAtLeast(1f)
                if (loaded[0] >= 0) cb.value((y / h).coerceIn(0f, 1f))
            }
            // chạm giữa màn hình (30-70% chiều ngang) để ẩn/hiện thanh; không chặn cuộn/link
            val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val f = e.x / width
                    if (f in 0.3f..0.7f) tapCb.value()
                    else if (fullS.value) {   // toàn màn hình: chạm mép phải = xuống 1 trang, mép trái = lên 1 trang; hết chương thì sang chương
                        if (f > 0.7f) {
                            if (canScrollVertically(1)) evaluateJavascript("window.scrollBy({top: innerHeight*0.92, behavior:'smooth'})", null)
                            else navCb.value(1)
                        } else {
                            if (canScrollVertically(-1)) evaluateJavascript("window.scrollBy({top: -innerHeight*0.92, behavior:'smooth'})", null)
                            else navCb.value(-1)
                        }
                    }
                    return false
                }
            })
            // Toàn màn hình: đang ở cuối chương mà vuốt lên thêm (>80dp) -> chương sau; ở đầu chương vuốt xuống -> chương trước
            val dn = FloatArray(1); val edge = BooleanArray(2)
            setOnTouchListener { v, e ->
                gd.onTouchEvent(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dn[0] = e.y; edge[0] = !canScrollVertically(1); edge[1] = !canScrollVertically(-1) }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()   // báo cho công cụ hỗ trợ tiếp cận biết đây là 1 cú chạm hợp lệ
                        if (fullS.value) {
                            val dy = e.y - dn[0]; val th = 80 * resources.displayMetrics.density
                            if (edge[0] && dy < -th) navCb.value(1) else if (edge[1] && dy > th) navCb.value(-1)
                        }
                    }
                }
                false
            }
        }
    }, update = { w ->
        val p = css(s.prefs)
        if (p != lastCss[0]) {
            lastCss[0] = p
            w.evaluateJavascript("(function(){var s=document.getElementById('tr');if(s)s.textContent=${JSONObject.quote(p)}})()", null)
        }
        if (loaded[0] != ch) { if (loaded[0] >= 0) restore[0] = jumpOff; loaded[0] = ch; w.loadUrl("https://epub.local/" + Uri.encode(epub.spine[ch], "/")) }
    }, onRelease = { it.stopLoading(); it.destroy() })
}

@Composable
fun PdfView(startPage: Int, dark: Boolean, full: Boolean, onTap: () -> Unit, onPos: (Int) -> Unit) {
    val sc = rememberCoroutineScope()
    val ctx = LocalContext.current
    val path = PdfPath.cur
    val pfd = remember { ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) }
    val r = remember { PdfRenderer(pfd) }
    DisposableEffect(Unit) { onDispose { r.close(); pfd.close() } }
    val w = ctx.resources.displayMetrics.widthPixels
    // Cache RGB_565 (2 byte/px), giới hạn ~1/16 RAM heap.
    val cache = remember { object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(k: Int, v: Bitmap) = v.byteCount / 1024 } }
    DisposableEffect(Unit) { onDispose { cache.evictAll() } }
    val ls = rememberLazyListState(startPage.coerceIn(0, maxOf(0, r.pageCount - 1)))
    LaunchedEffect(ls) { snapshotFlow { ls.firstVisibleItemIndex }.collect { onPos(it) } }
    val inv = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)))
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.pointerInput(full) { detectTapGestures(onTap = { o ->
            val f = o.x / size.width
            if (f in 0.3f..0.7f || !full) onTap() else sc.launch {
                val i = ls.firstVisibleItemIndex
                if (f > 0.7f) ls.animateScrollToItem((i + 1).coerceAtMost(r.pageCount - 1))
                else ls.animateScrollToItem(if (ls.firstVisibleItemScrollOffset > 0) i else maxOf(0, i - 1))
            } }) }, state = ls, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(r.pageCount) { i ->
                val bmp by produceState<Bitmap?>(cache.get(i), i) {
                    if (value == null) value = withContext(Dispatchers.IO) {
                        synchronized(r) {
                            val pg = r.openPage(i)
                            try {
                                val tmp = Bitmap.createBitmap(w, w * pg.height / pg.width, Bitmap.Config.ARGB_8888)
                                tmp.eraseColor(android.graphics.Color.WHITE)
                                pg.render(tmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val out = tmp.copy(Bitmap.Config.RGB_565, false); tmp.recycle()
                                cache.put(i, out); out
                            } finally { pg.close() }
                        }
                    }
                }
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), colorFilter = if (dark) inv else null) }
                    ?: Box(Modifier.fillMaxWidth().aspectRatio(0.7f))
            }
        }
        // Thanh cuộn: chỉ hiện khi đang kéo, ngừng ~0.8s thì mờ đi
        val moving = ls.isScrollInProgress
        var show by remember { mutableStateOf(false) }
        LaunchedEffect(moving) { if (moving) show = true else { delay(800); show = false } }
        val a by animateFloatAsState(if (show) 1f else 0f, tween(300), label = "sb")
        val col = MaterialTheme.colorScheme.primary
        Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp).graphicsLayer { alpha = a }) {
            val n = maxOf(r.pageCount, 2)
            val first = ls.layoutInfo.visibleItemsInfo.firstOrNull()
            val frac = if (first == null) 0f else ((first.index + (-first.offset).toFloat() / maxOf(first.size, 1)) / (n - 1)).coerceIn(0f, 1f)
            val th = maxOf(size.height / n, 48.dp.toPx()).coerceAtMost(size.height)
            drawRoundRect(col.copy(.75f), Offset(size.width - 6.dp.toPx(), frac * (size.height - th)), Size(4.dp.toPx(), th), CornerRadius(2.dp.toPx()))
        }
    }
}
object PdfPath { var cur = "" }

// ---------- Thói quen / sao lưu ----------
@Composable
fun SettingsRow(icon: String, color: Color, title: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(icon, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(content = content)
    }
}

@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp, 4.dp, 16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonText("←", onBack)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

enum class SettingsScreen { Main, General, Appearance, Reading, Stats, About, Feedback }

@Composable
fun Settings(s: Store) {
    var screen by rememberSaveable { mutableStateOf(SettingsScreen.Main) }
    when (screen) {
        SettingsScreen.General -> GeneralSettings(s) { screen = SettingsScreen.Main }
        SettingsScreen.Appearance -> AppearanceSettings(s) { screen = SettingsScreen.Main }
        SettingsScreen.Reading -> ReadingSettings(s) { screen = SettingsScreen.Main }
        SettingsScreen.Stats -> StatsSettings(s) { screen = SettingsScreen.Main }
        SettingsScreen.About -> AboutSettings { screen = SettingsScreen.Main }
        SettingsScreen.Feedback -> FeedbackSettings { screen = SettingsScreen.Main }
        SettingsScreen.Main -> SettingsMain(s) { screen = it }
    }
}

@Composable
fun SettingsMain(s: Store, onGo: (SettingsScreen) -> Unit) {
    val ctx = LocalContext.current
    var showBackup by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    var confirmWipe1 by remember { mutableStateOf(false) }
    var confirmWipe2 by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 8.dp, 16.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        SettingsGroup {
            SettingsRow("🔔", Color(0xFF4A90D9), "Cài đặt chung", "Nhắc đọc sách hằng ngày") { onGo(SettingsScreen.General) }
            HorizontalDivider()
            SettingsRow("📖", Color(0xFF3FA37A), "Tùy chọn đọc", "Font, cỡ chữ, giãn dòng, ảnh") { onGo(SettingsScreen.Reading) }
            HorizontalDivider()
            SettingsRow("🎨", Color(0xFF3FA37A), "Giao diện", "Nền sáng, tối, màu giấy") { onGo(SettingsScreen.Appearance) }
            HorizontalDivider()
            SettingsRow("📊", Color(0xFF3FA37A), "Thống kê đọc sách") { onGo(SettingsScreen.Stats) }
        }
        SettingsGroup {
            SettingsRow("✉️", Color(0xFFD9A23F), "Gửi phản hồi") { onGo(SettingsScreen.Feedback) }
            HorizontalDivider()
            SettingsRow("ℹ️", Color(0xFFD9A23F), "Về TReader") { onGo(SettingsScreen.About) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Sao lưu và phục hồi", Modifier.clickable { showBackup = true }.padding(8.dp), color = MaterialTheme.colorScheme.primary)
            Text("Xoá bộ nhớ đệm tạm thời", Modifier.clickable { confirmClearCache = true }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Xoá toàn bộ dữ liệu", Modifier.clickable { confirmWipe1 = true }.padding(8.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showBackup) {
        val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
            u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(s.json().toByteArray()) } }; showBackup = false
        }
        val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
            u?.let { runCatching { s.load(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText(), true); s.save() } }; showBackup = false
        }
        AlertDialog(onDismissRequest = { showBackup = false }, title = { Text("Sao lưu và phục hồi") },
            text = { Text("Xuất ra file để chuyển sang máy khác, hoặc nhập lại một file đã xuất trước đó để khôi phục.") },
            confirmButton = { TextButton({ exp.launch("treader-backup.json") }) { Text("Xuất sao lưu") } },
            dismissButton = { TextButton({ imp.launch(arrayOf("application/json", "*/*")) }) { Text("Nhập sao lưu") } })
    }
    if (confirmClearCache) AlertDialog(onDismissRequest = { confirmClearCache = false }, title = { Text("Xoá bộ nhớ đệm tạm thời?") },
        text = { Text("Xoá ảnh bìa sách đã lưu tạm để tiết kiệm dung lượng. Sách, tiến độ đọc và ghi chú không bị ảnh hưởng. Ảnh bìa sẽ tự tạo lại khi mở sách lần sau.") },
        confirmButton = { TextButton({
            runCatching { File(s.ctx.filesDir, "covers").deleteRecursively() }; CoverCache.c.evictAll()
            confirmClearCache = false; toast = "Đã xoá bộ nhớ đệm"
        }) { Text("Xoá") } },
        dismissButton = { TextButton({ confirmClearCache = false }) { Text("Huỷ") } })
    if (confirmWipe1) AlertDialog(onDismissRequest = { confirmWipe1 = false }, title = { Text("Xoá toàn bộ dữ liệu?") },
        text = { Text("Toàn bộ sách, tiến độ đọc, đánh dấu trang và thống kê sẽ bị xoá vĩnh viễn khỏi máy này. Hãy xuất sao lưu trước nếu mày chưa chắc chắn.") },
        confirmButton = { TextButton({ confirmWipe1 = false; confirmWipe2 = true }) { Text("Tiếp tục", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmWipe1 = false }) { Text("Huỷ") } })
    if (confirmWipe2) AlertDialog(onDismissRequest = { confirmWipe2 = false }, title = { Text("Chắc chắn xoá hết?") },
        text = { Text("Đây là bước xác nhận cuối cùng. Không thể hoàn tác sau khi bấm Xoá hết.") },
        confirmButton = { TextButton({ s.wipeAll(); Reminder.cancel(ctx); confirmWipe2 = false }) { Text("Xoá hết", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmWipe2 = false }) { Text("Huỷ") } })
    toast?.let { msg -> LaunchedEffect(msg) { delay(2000); toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Text(msg, Modifier.padding(16.dp, 10.dp))
            }
        }
    }
}

@Composable
fun GeneralSettings(s: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    SettingsPage("Cài đặt chung", onBack) {
        val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { s.prefs = s.prefs.copy(remindOn = true); s.save(); Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin) }
        }
        SettingsGroup { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Nhắc đọc sách hằng ngày", fontWeight = FontWeight.Medium)
                    Text("Vào lúc %02d:%02d".format(s.prefs.remindHour, s.prefs.remindMin), style = MaterialTheme.typography.bodySmall)
                }
                Switch(s.prefs.remindOn, { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { s.prefs = s.prefs.copy(remindOn = true); s.save(); Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin) }
                    } else { s.prefs = s.prefs.copy(remindOn = false); s.save(); Reminder.cancel(ctx) }
                })
            }
            if (s.prefs.remindOn) OutlinedButton({
                TimePickerDialog(ctx, { _, h, m ->
                    s.prefs = s.prefs.copy(remindHour = h, remindMin = m); s.save(); Reminder.schedule(ctx, h, m)
                }, s.prefs.remindHour, s.prefs.remindMin, true).show()
            }) { Text("Đổi giờ nhắc") }
        } }
    }
}

@Composable
fun AppearanceSettings(s: Store, onBack: () -> Unit) {
    SettingsPage("Giao diện", onBack) {
        Text("Màu nền khi đọc sách", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (0..2).forEach { i ->
                val (bg, fg) = themeColors(i)
                val name = listOf("Sáng", "Tối", "Giấy")[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(bg)
                        .border(if (s.prefs.theme == i) 3.dp else 1.dp, if (s.prefs.theme == i) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                        .clickable { s.prefs = s.prefs.copy(theme = i); s.save() }, contentAlignment = Alignment.Center) { Text("A", color = fg, fontWeight = FontWeight.Bold) }
                    Text(name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ReadingSettings(s: Store, onBack: () -> Unit) {
    SettingsPage("Tùy chọn đọc", onBack) {
        val p = s.prefs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("serif", "sans-serif", "monospace").forEach { f ->
            FilterChip(p.font == f, { s.prefs = p.copy(font = f); s.save() }, { Text(f) }) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ảnh"); listOf("Ẩn", "Gọn", "Đầy đủ").forEachIndexed { i, n -> FilterChip(p.img == i, { s.prefs = p.copy(img = i); s.save() }, { Text(n) }) } }
        Text("Cỡ chữ ${p.size}"); Slider(p.size.toFloat(), { s.prefs = p.copy(size = it.toInt()) }, valueRange = 12f..32f, onValueChangeFinished = { s.save() })
        Text("Giãn dòng %.1f".format(p.line)); Slider(p.line, { s.prefs = p.copy(line = it) }, valueRange = 1.2f..2.2f, onValueChangeFinished = { s.save() })
        Text("Lề ${p.margin}"); Slider(p.margin.toFloat(), { s.prefs = p.copy(margin = it.toInt()) }, valueRange = 0f..40f, onValueChangeFinished = { s.save() })
        Text("Áp dụng cho mọi sách đang đọc. Có thể chỉnh nhanh hơn ngay trong lúc đọc bằng nút Aa.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatsSettings(s: Store, onBack: () -> Unit) {
    val goal = s.prefs.goal
    val min = ((s.daily[s.today()] ?: 0) / 60).toInt()
    var d = LocalDate.now(); var streak = 0
    while ((s.daily[d.toString()] ?: 0) >= goal * 60L) { streak++; d = d.minusDays(1) }
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val vals = days.map { ((s.daily[it.toString()] ?: 0) / 60).toInt() }
    val mx = maxOf(vals.max(), goal, 1)
    val track = MaterialTheme.colorScheme.surfaceVariant; val prim = MaterialTheme.colorScheme.primary
    val card = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    SettingsPage("Thống kê đọc sách", onBack) {
        Card(Modifier.fillMaxWidth(), colors = card) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val st = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(track, 0f, 360f, false, style = st, size = Size(size.width, size.height))
                    drawArc(prim, -90f, 360f * (min.toFloat() / goal).coerceIn(0f, 1f), false, style = st, size = Size(size.width, size.height))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$min", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("/ $goal phút", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ s.prefs = s.prefs.copy(goal = maxOf(5, goal - 5)); s.save() }) { Text("−5") }
                Text("Mục tiêu/ngày")
                OutlinedButton({ s.prefs = s.prefs.copy(goal = goal + 5); s.save() }) { Text("+5") }
            }
        } }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.weight(1f), colors = card) { Column(Modifier.padding(16.dp)) { Text("🔥 $streak", style = MaterialTheme.typography.headlineMedium); Text("ngày liên tiếp", style = MaterialTheme.typography.bodySmall) } }
            Card(Modifier.weight(1f), colors = card) { Column(Modifier.padding(16.dp)) { Text("⏱ ${s.books.sumOf { it.sec } / 3600}h", style = MaterialTheme.typography.headlineMedium); Text("tổng thời gian", style = MaterialTheme.typography.bodySmall) } }
        }
        Card(Modifier.fillMaxWidth(), colors = card) { Row(Modifier.fillMaxWidth().padding(16.dp).height(110.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            days.forEachIndexed { i, dt -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.width(24.dp).height((80f * vals[i] / mx).coerceAtLeast(3f).dp).clip(RoundedCornerShape(6.dp))
                    .background(if (vals[i] >= goal) prim else prim.copy(.4f)))
                Text(if (dt.dayOfWeek.value == 7) "CN" else "T${dt.dayOfWeek.value + 1}", style = MaterialTheme.typography.labelSmall)
            } }
        } }
        Text("Theo sách", style = MaterialTheme.typography.titleMedium)
        s.books.sortedByDescending { it.sec }.take(8).forEach { Text("${it.title} — ${it.sec / 60} phút", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun AboutSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    SettingsPage("Về TReader", onBack) {
        Text("TReader", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Phiên bản $ver", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ứng dụng đọc sách EPUB và PDF, chạy hoàn toàn trên máy — không quảng cáo, không máy chủ, mọi dữ liệu chỉ lưu trên thiết bị của bạn.",
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FeedbackSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    SettingsPage("Gửi phản hồi", onBack) {
        Text("Mở ứng dụng email trên máy để viết góp ý, báo lỗi hoặc yêu cầu tính năng cho TReader.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({
            val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Phản hồi về TReader")
            runCatching { ctx.startActivity(i) }
        }) { Text("Mở email") }
    }
}