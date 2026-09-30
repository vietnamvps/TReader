package com.treader.ui.reader

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.treader.Active
import com.treader.Book
import com.treader.Bookmark
import com.treader.Epub
import com.treader.Flush
import com.treader.Prefs
import com.treader.R
import com.treader.Store
import com.treader.ui.library.pr
import com.treader.ui.theme.ReadingFonts
import com.treader.ui.theme.ReadingThemes
import com.treader.ui.theme.themeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

fun css(p: Prefs): String {
    val spec = ReadingThemes.get(p.theme)
    val fg = spec.fg
    val img = when (p.img) {
        0 -> "img,svg,image{display:none!important}"
        1 -> "img{display:block!important;max-width:100%!important;max-height:38vh!important;width:auto!important;height:auto!important;margin:.8em auto!important;border-radius:10px}"
        else -> "img{display:block!important;max-width:100%!important;height:auto!important;margin:.8em auto!important}"
    }
    val dim = if (p.theme == 1 || p.theme == 5) "img{filter:brightness(.85)}" else ""
    return "html,body{${spec.cssBg}color:${hex(fg)}!important;font-family:${p.font}!important;" +
            "font-size:${p.size}px!important;line-height:${p.line}!important;padding:12px ${p.margin}px 56px!important;margin:0!important;" +
            "word-wrap:break-word;-webkit-text-size-adjust:100%}" +
            "p,div,span,li{font-family:inherit!important}p{margin:0 0 .9em!important;text-indent:0!important}" +
            "h1,h2,h3{text-align:center!important;font-weight:600!important;line-height:1.3!important;margin:1.1em 0 .9em!important}" +
            "h1{font-size:1.4em!important}h2{font-size:1.2em!important}h3{font-size:1.1em!important}" +
            "a{color:inherit!important;text-decoration:none!important}" + img + dim
}

class ReadState(var pos: Int, var off: Float) { var sec = 0L; var saved = 0L }

fun cleanTextForTts(htmlText: String): String {
    return htmlText
        .replace(Regex("(?s)<style[^*]*?</style>"), " ")
        .replace(Regex("(?s)<script[^*]*?</script>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&rdquo;", "\"")
        .replace("&ldquo;", "\"")
        .replace("&mdash;", " - ")
        .replace("&ndash;", " - ")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}

class TtsPlayer(val ctx: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    var isReady by mutableStateOf(false)
    var isSpeaking by mutableStateOf(false)
    var selectedVoiceType by mutableStateOf("female")
    var speechRate by mutableFloatStateOf(1.0f)

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val viLocale = Locale("vi", "VN")
            val viLocaleShort = Locale("vi")
            var viResult = tts?.setLanguage(viLocale)
            if (viResult == TextToSpeech.LANG_MISSING_DATA || viResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                viResult = tts?.setLanguage(viLocaleShort)
            }
            if (viResult == TextToSpeech.LANG_MISSING_DATA || viResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            isReady = true
        }
    }

    fun applyVoiceAndRate() {
        tts?.setSpeechRate(speechRate)
        val voices = runCatching { tts?.voices }.getOrNull()
        if (voices != null && voices.isNotEmpty()) {
            val viVoices = voices.filter { it.locale.language == "vi" || it.locale == Locale("vi", "VN") }
            val pool = if (viVoices.isNotEmpty()) viVoices else voices
            if (selectedVoiceType == "female") {
                val fVoice = pool.firstOrNull { it.name.contains("vif", true) || it.name.contains("female", true) || it.name.contains("f-", true) }
                if (fVoice != null) tts?.voice = fVoice
            } else if (selectedVoiceType == "male") {
                val mVoice = pool.firstOrNull { it.name.contains("vic", true) || it.name.contains("male", true) || it.name.contains("m-", true) }
                if (mVoice != null) tts?.voice = mVoice
            }
        }
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        if (!isReady || text.isBlank()) return
        tts?.stop()
        applyVoiceAndRate()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                onDone()
            }
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
        val clean = cleanTextForTts(text)
        val params = Bundle()
        tts?.speak(clean.take(4000), TextToSpeech.QUEUE_FLUSH, params, "reader_tts_id")
        isSpeaking = true
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isSpeaking = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Reader(s: Store, b0: Book, remount: () -> Unit, close: () -> Unit) {
    PdfPath.cur = b0.file
    val epub = remember { if (b0.type == "epub") Epub(File(b0.file)) else null }
    val st = remember { ReadState(b0.pos, b0.off) }
    var ch by remember { mutableIntStateOf(b0.pos) }
    var dlg by remember { mutableIntStateOf(0) }
    var bars by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    val view = LocalView.current

    val ttsPlayer = remember { TtsPlayer(view.context) }
    DisposableEffect(Unit) {
        onDispose { ttsPlayer.shutdown() }
    }
    val ttsStartStr = stringResource(R.string.tts_started)
    val ttsStopStr = stringResource(R.string.tts_stopped)
    val ttsNotReadyStr = stringResource(R.string.tts_not_ready)

    BackHandler {
        if (bars) bars = false else close()
    }

    DisposableEffect(bars) {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        view.keepScreenOn = true
        if (bars) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()); view.keepScreenOn = false }
    }

    val persist = remember { {
        val add = st.sec - st.saved; st.saved = st.sec
        val cur = s.books.firstOrNull { it.id == b0.id } ?: b0
        if (add > 0) s.daily[s.today()] = (s.daily[s.today()] ?: 0) + add
        s.update(cur.copy(pos = st.pos, off = st.off, last = System.currentTimeMillis(), sec = cur.sec + add))
    } }

    var jump by remember { mutableFloatStateOf(0f) }
    fun go(i: Int, o: Float = 0f) { jump = o; ch = i; st.pos = i; st.off = o }

    val marks by remember { derivedStateOf { s.bookmarks.filter { it.bookId == b0.id }.sortedBy { it.pos + it.off } } }
    val defaultChLabel = stringResource(R.string.chapter_x, ch + 1)
    val defaultPgLabel = stringResource(R.string.page_x, st.pos + 1)
    var toastMsg by remember { mutableStateOf<String?>(null) }
    val toastStr = stringResource(R.string.bookmark_added_toast)

    val currentChapterTitle = if (epub != null) {
        epub.toc.lastOrNull { it.second <= ch }?.first ?: defaultChLabel
    } else defaultPgLabel

    val isBookmarked = marks.any { it.pos == (if (epub != null) ch else st.pos) }

    fun addBookmark() {
        val pct = if (epub != null) (st.off * 100).toInt() else 0
        val baseLabel = currentChapterTitle
        val label = if (epub != null && pct > 0) "$baseLabel ($pct%)" else baseLabel
        s.bookmarks.add(Bookmark(UUID.randomUUID().toString(), b0.id, if (epub != null) ch else st.pos,
            if (epub != null) st.off else 0f, label, System.currentTimeMillis()))
        s.save()
        toastMsg = toastStr
    }

    fun gotoBookmark(bm: Bookmark) {
        val cur = s.books.firstOrNull { it.id == b0.id } ?: b0
        if (epub != null) {
            ch = bm.pos
            st.pos = bm.pos
            st.off = bm.off
            jump = bm.off
            s.update(cur.copy(pos = bm.pos, off = bm.off))
        } else {
            st.pos = bm.pos
            st.off = 0f
            s.update(cur.copy(pos = bm.pos, off = 0f))
        }
        dlg = 0
        remount()
    }

    LaunchedEffect(Unit) { var t = 0; while (true) { delay(1000); if (Active.on) { st.sec++; if (++t % 60 == 0) persist() } } }
    DisposableEffect(Unit) { Flush.fn = persist; onDispose { persist(); Flush.fn = null; epub?.zip?.close() } }

    val (bg, fg) = themeColors(s.prefs.theme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP HEADER (Always visible in clean reading mode like Screenshot 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentChapterTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. MAIN READING AREA (EPUB / PDF)
            Box(modifier = Modifier.weight(1f)) {
                if (epub == null) {
                    PdfView(b0.pos, s.prefs.theme == 1 || s.prefs.theme == 5, !bars, { bars = !bars }) { st.pos = it }
                } else {
                    val fadeA = remember { Animatable(0f) }
                    LaunchedEffect(ch) { fadeA.snapTo(0f); fadeA.animateTo(1f, tween(220)) }
                    Box(modifier = Modifier.graphicsLayer { alpha = fadeA.value }.fillMaxSize()) {
                        EpubView(s, epub, ch, b0.off, !bars, jump, { bars = !bars }, { d ->
                            val n = ch + d; if (n in 0 until epub.spine.size) go(n, if (d < 0) 1f else 0f)
                        }, { i -> if (bars && i != ch) go(i) }) { st.off = it }
                    }
                }
            }

            // 3. BOTTOM STATUS BAR (Always visible in clean reading mode like Screenshot 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                Text(
                    text = "🔋 $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.5f)
                )

                Text(
                    text = b0.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                val progressPct = "%.2f%%".format(b0.pr() * 100)
                val posStr = if (epub != null) "${ch + 1}/${epub.spine.size}" else "${st.pos + 1}"
                Text(
                    text = "$progressPct  $posStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.5f)
                )
            }
        }

        // ==================== OPTIONS OVERLAY MODE (When bars == true) ====================
        if (bars) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable { bars = false }
            )

            // Top Navigation Bar (Screenshot 2)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = close) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = { dlg = 5 }) {
                        Text("†!† " + stringResource(R.string.reader_options_btn))
                    }

                    if (epub != null) {
                        IconButton(onClick = { dlg = 2 }) {
                            Text("🔍", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    IconButton(onClick = remount) {
                        Text("🔄", style = MaterialTheme.typography.titleMedium)
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmarks_title)) },
                                onClick = { showMoreMenu = false; dlg = 4 }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bg_color_reading)) },
                                onClick = { showMoreMenu = false; dlg = 3 }
                            )
                        }
                    }
                }
            }

            // Right Floating Action Rail (Screenshot 2: Audio, Jump, Bookmark)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingActionButton(
                    onClick = {
                        if (!ttsPlayer.isReady) {
                            toastMsg = ttsNotReadyStr
                        } else if (ttsPlayer.isSpeaking) {
                            ttsPlayer.stop()
                            toastMsg = ttsStopStr
                        } else {
                            dlg = 6
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = if (ttsPlayer.isSpeaking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (ttsPlayer.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ) {
                    Text(if (ttsPlayer.isSpeaking) "⏸️" else "🎧", style = MaterialTheme.typography.titleMedium)
                }

                FloatingActionButton(
                    onClick = {
                        if (epub != null && ch < epub.spine.size - 1) go(ch + 1)
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Text("➔", style = MaterialTheme.typography.titleMedium)
                }

                FloatingActionButton(
                    onClick = { addBookmark() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = if (isBookmarked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ) {
                    Text(if (isBookmarked) "🔖" else "🏷️", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Bottom Navigation & Progress Controls (Screenshot 2)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Row 1: 5 Main Control Icons (Mục lục, Ghi chú, Dấu trang, Giao diện, Cài đặt)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { dlg = 1 }
                        ) {
                            Text("📖", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.reader_option_toc), style = MaterialTheme.typography.labelSmall)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { dlg = 4 }
                        ) {
                            Text("📋", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.reader_option_notes), style = MaterialTheme.typography.labelSmall)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { addBookmark() }
                        ) {
                            Text("🔖", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.reader_option_bookmark), style = MaterialTheme.typography.labelSmall)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { dlg = 3 }
                        ) {
                            Text("🔆", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.reader_option_theme), style = MaterialTheme.typography.labelSmall)
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { dlg = 5 }
                        ) {
                            Text("𝜞", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stringResource(R.string.reader_option_settings), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Row 2: Progress Slider Row (`‹` Sleek Slider `›`)
                    if (epub != null && epub.spine.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (ch > 0) go(ch - 1) },
                                enabled = ch > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("‹", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }

                            Slider(
                                value = ch.toFloat(),
                                onValueChange = { go(it.toInt()) },
                                valueRange = 0f..(epub.spine.size - 1).coerceAtLeast(1).toFloat(),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                thumb = {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                },
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        sliderState = sliderState,
                                        modifier = Modifier.height(4.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            )

                            IconButton(
                                onClick = { if (ch < epub.spine.size - 1) go(ch + 1) },
                                enabled = ch < epub.spine.size - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("›", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==================== BOTTOM SHEETS (DIALOGS) ====================
        if (dlg == 1 && epub != null) ModalBottomSheet({ dlg = 0 }) {
            Text(stringResource(R.string.table_of_contents), Modifier.padding(16.dp, 0.dp), style = MaterialTheme.typography.titleLarge)
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
                    OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text(stringResource(R.string.search_in_book)) }, shape = RoundedCornerShape(28.dp))
                    Button({ sc.launch { res = withContext(Dispatchers.IO) { epub.search(q) } } }) { Text(stringResource(R.string.search_button)) }
                }
                LazyColumn { items(res) { (c, t) ->
                    Text(t, Modifier.fillMaxWidth().clickable { go(c); dlg = 0 }.padding(16.dp, 10.dp), style = MaterialTheme.typography.bodyMedium)
                } }
            }
        }

        if (dlg == 3) ModalBottomSheet({ s.save(); dlg = 0 }) {
            val p = s.prefs
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bg_color_reading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingThemes.list.forEach { theme ->
                        val isSelected = p.theme == theme.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { s.prefs = p.copy(theme = theme.id) }) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(theme.bg)
                                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text("A", color = theme.fg, fontWeight = FontWeight.Bold) }
                            Text(stringResource(theme.nameResId), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        if (dlg == 4) ModalBottomSheet({ dlg = 0 }) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bookmarks_title), style = MaterialTheme.typography.titleLarge)
                FilledTonalButton({ addBookmark() }) { Text(stringResource(R.string.add_bookmark_button)) }
            }
            if (marks.isEmpty()) Text(stringResource(R.string.no_bookmarks_in_book), Modifier.padding(16.dp, 4.dp, 16.dp, 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.padding(bottom = 12.dp)) { items(marks, key = { it.id }) { bm ->
                Row(Modifier.fillMaxWidth().clickable { gotoBookmark(bm) }.padding(16.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bm.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(bm.time)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ s.bookmarks.remove(bm); s.save() }) { Text("✕") }
                }
            } }
        }

        if (dlg == 5) ModalBottomSheet({ s.save(); dlg = 0 }) {
            val p = s.prefs
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 0.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.font_family_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingFonts.list.forEach { fontSpec ->
                        FilterChip(
                            selected = p.font == fontSpec.code,
                            onClick = { s.prefs = p.copy(font = fontSpec.code) },
                            label = { Text(stringResource(fontSpec.nameResId), fontFamily = fontSpec.fontFamily) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.img_option))
                    val imgOpts = listOf(stringResource(R.string.img_hide), stringResource(R.string.img_compact), stringResource(R.string.img_full))
                    imgOpts.forEachIndexed { i, n -> FilterChip(p.img == i, { s.prefs = p.copy(img = i) }, { Text(n) }) }
                }
                Text(stringResource(R.string.font_size_x, p.size)); Slider(p.size.toFloat(), { s.prefs = p.copy(size = it.toInt()) }, valueRange = 12f..32f)
                Text(stringResource(R.string.line_spacing_x, p.line)); Slider(p.line, { s.prefs = p.copy(line = it) }, valueRange = 1.2f..2.2f)
                Text(stringResource(R.string.margin_x, p.margin)); Slider(p.margin.toFloat(), { s.prefs = p.copy(margin = it.toInt()) }, valueRange = 0f..40f)
            }
        }

        if (dlg == 6) ModalBottomSheet({ dlg = 0 }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp, 0.dp, 20.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.tts_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    stringResource(R.string.font_family_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val voiceOptions = listOf(
                    "female" to stringResource(R.string.tts_voice_female),
                    "male" to stringResource(R.string.tts_voice_male),
                    "system" to stringResource(R.string.tts_voice_system)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    voiceOptions.forEach { (type, label) ->
                        val selected = ttsPlayer.selectedVoiceType == type
                        FilterChip(
                            selected = selected,
                            onClick = { ttsPlayer.selectedVoiceType = type },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.tts_speed_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.2fx".format(ttsPlayer.speechRate),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = ttsPlayer.speechRate,
                    onValueChange = { ttsPlayer.speechRate = it },
                    valueRange = 0.5f..2.0f,
                    steps = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        dlg = 0
                        val rawText = if (epub != null) {
                            epub.read(epub.spine[ch])?.let { String(it) } ?: b0.title
                        } else b0.title
                        ttsPlayer.speak(rawText)
                        toastMsg = ttsStartStr
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(stringResource(R.string.tts_start_btn), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        toastMsg?.let { msg ->
            LaunchedEffect(msg) { delay(2000); toastMsg = null }
            Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    Text(msg, Modifier.padding(16.dp, 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
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
                GradientDrawable().apply { cornerRadius = 8f * dp; setColor(0xB0808080.toInt()) }
            setBackgroundColor(0)
            overScrollMode = View.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = object : WebViewClient() {
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
                    if (!html) {
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
                    if (s.prefs.img == 1) v.evaluateJavascript("document.querySelectorAll('img').forEach(function(i){function f(){if(i.naturalWidth<=120&&i.naturalHeight<=120)i.style.display='none'}i.complete?f():i.addEventListener('load',f)})", null)
                    val f = restore[0]; restore[0] = 0f
                    if (f > 0f) {
                        v.postDelayed({
                            val h = (v.contentHeight * v.resources.displayMetrics.density - v.height).coerceAtLeast(1f)
                            v.scrollTo(0, (f * h).toInt())
                        }, 100)
                        v.postDelayed({
                            val h = (v.contentHeight * v.resources.displayMetrics.density - v.height).coerceAtLeast(1f)
                            v.scrollTo(0, (f * h).toInt())
                        }, 350)
                    }
                }
            }
            setOnScrollChangeListener { _, _, y, _, _ ->
                val h = (contentHeight * resources.displayMetrics.density - height).coerceAtLeast(1f)
                if (loaded[0] >= 0) cb.value((y / h).coerceIn(0f, 1f))
            }
            val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val f = e.x / width
                    if (f in 0.3f..0.7f) tapCb.value()
                    else if (fullS.value) {
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
            val dn = FloatArray(1); val edge = BooleanArray(2)
            setOnTouchListener { v, e ->
                gd.onTouchEvent(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dn[0] = e.y; edge[0] = !canScrollVertically(1); edge[1] = !canScrollVertically(-1) }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
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
        if (loaded[0] != ch) {
            restore[0] = jumpOff
            loaded[0] = ch
            w.loadUrl("https://epub.local/" + Uri.encode(epub.spine[ch], "/"))
        } else if (jumpOff > 0f) {
            w.evaluateJavascript("window.scrollTo(0, (document.documentElement.scrollHeight - window.innerHeight) * $jumpOff)", null)
        }
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
    val cache = remember { object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(k: Int, v: Bitmap) = v.byteCount / 1024 } }
    DisposableEffect(Unit) { onDispose { cache.evictAll() } }
    val ls = rememberLazyListState(startPage.coerceIn(0, maxOf(0, r.pageCount - 1)))
    LaunchedEffect(startPage) {
        if (startPage in 0 until r.pageCount) {
            ls.scrollToItem(startPage)
        }
    }
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
