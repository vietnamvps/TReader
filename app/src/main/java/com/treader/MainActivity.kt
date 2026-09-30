@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package com.treader

import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treader.ui.home.Home
import com.treader.ui.library.CoverCache
import com.treader.ui.library.ImportBar
import com.treader.ui.library.ImportOutcome
import com.treader.ui.library.ImportProgress
import com.treader.ui.library.Library
import com.treader.ui.library.importBook
import com.treader.ui.library.nameOf
import com.treader.ui.notes.Notes
import com.treader.ui.reader.Reader
import com.treader.ui.settings.Settings
import com.treader.ui.theme.TTheme
import com.treader.util.applyLocale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Active { @Volatile var on = true }
object Flush { var fn: (() -> Unit)? = null }
enum class LibFilter { None, Fav, RecentAdded, Trash }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        val s = Store(this)
        applyLocale(s.prefs.lang)
        applicationContext.applyLocale(s.prefs.lang)
        Reminder.createChannel(this)
        Thread { s.backfillHashes() }.start()
        setContent {
            CompositionLocalProvider(
                LocalConfiguration provides remember(s.prefs.lang) { Configuration(resources.configuration) }
            ) {
                TTheme(s.prefs.appTheme) { Surface(Modifier.fillMaxSize()) { App(s) } }
            }
        }
    }
    override fun onPause() { Active.on = false; Flush.fn?.invoke(); super.onPause() }
    override fun onResume() { super.onResume(); Active.on = true }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) CoverCache.c.evictAll()
    }
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
    var pendingUris by remember { mutableStateOf<List<Uri>?>(null) }
    var selectedTag by remember { mutableStateOf("") }
    var newTagText by remember { mutableStateOf("") }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) {
            pendingUris = us
            selectedTag = ""
            newTagText = ""
        }
    }

    pendingUris?.let { us ->
        val existingTags = s.books.map { it.tag }.filter { it.isNotBlank() }.distinct()
        AlertDialog(
            onDismissRequest = { pendingUris = null },
            title = { Text(stringResource(R.string.select_category_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (existingTags.isNotEmpty()) {
                        Text(stringResource(R.string.filter_by_category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedTag.isEmpty() && newTagText.isBlank(),
                                onClick = { selectedTag = ""; newTagText = "" },
                                label = { Text(stringResource(R.string.category_uncategorized)) },
                                shape = RoundedCornerShape(20.dp)
                            )
                            existingTags.forEach { t ->
                                FilterChip(
                                    selected = selectedTag == t && newTagText.isBlank(),
                                    onClick = { selectedTag = t; newTagText = "" },
                                    label = { Text(t) },
                                    shape = RoundedCornerShape(20.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.new_category_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTag = newTagText.trim().ifEmpty { selectedTag }
                        val targetUris = us
                        pendingUris = null
                        sc.launch {
                            var dup = 0
                            targetUris.forEachIndexed { i, u ->
                                importing = ImportProgress(targetUris.size, i, nameOf(ctx, u), 0f)
                                if (importBook(s, ctx, u, categoryTag = finalTag) { f -> importing = importing?.copy(frac = f) } == ImportOutcome.DUPLICATE) dup++
                            }
                            importing = null
                            if (dup > 0) {
                                importMsg = if (dup == 1) ctx.getString(R.string.skipped_duplicate_books_one) else ctx.getString(R.string.skipped_duplicate_books_many, dup)
                                delay(2500)
                                importMsg = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(stringResource(R.string.confirm_import_btn, us.size))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUris = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    fun doImport() = pick.launch(arrayOf("application/epub+zip", "application/pdf"))
    fun goLib(f: LibFilter) { libFilter = f; tab = 1 }
    BackHandler(openId != null) { openId = null }
    val o = openId?.let { id -> s.books.firstOrNull { it.id == id } }
    if (o != null) key(o.id, jumpNonce) { Reader(s, o, { jumpNonce++ }) { openId = null } }
    else Box(Modifier.fillMaxSize()) { Scaffold(
        bottomBar = { NavigationBar {
            NavigationBarItem(tab == 0, { tab = 0 }, { Text("🏠") }, label = { Text(stringResource(R.string.nav_home)) })
            NavigationBarItem(tab == 1, { tab = 1; libFilter = LibFilter.None }, { Text("📚") }, label = { Text(stringResource(R.string.nav_library)) })
            NavigationBarItem(tab == 2, { tab = 2 }, { Text("🔖") }, label = { Text(stringResource(R.string.nav_notes)) })
            NavigationBarItem(tab == 3, { tab = 3 }, { Text("⚙️") }, label = { Text(stringResource(R.string.nav_settings)) })
        } },
        floatingActionButton = { if (tab == 0 || tab == 1) ExtendedFloatingActionButton({ doImport() }) { Text(stringResource(R.string.import_book_button)) } }
    ) { pad -> Box(Modifier.padding(pad)) {
        when (tab) {
            0 -> Home(s, { openId = it.id }, { f -> goLib(f) }, { doImport() })
            1 -> Library(s, libFilter) { openId = it.id }
            2 -> Notes(s) { b, bm -> s.update(b.copy(pos = bm.pos, off = bm.off)); jumpNonce++; openId = b.id }
            else -> Settings(s)
        }
    } }
        importing?.let { ip -> ImportBar(ip, Modifier.align(Alignment.BottomCenter).padding(16.dp, 0.dp, 16.dp, 88.dp)) }
        importMsg?.let { msg -> Card(Modifier.align(Alignment.BottomCenter).padding(16.dp, 0.dp, 16.dp, 88.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(6.dp)) { Text(msg, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) } }
    }
}
