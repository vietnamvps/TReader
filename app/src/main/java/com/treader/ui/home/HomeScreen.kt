package com.treader.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.treader.Book
import com.treader.LibFilter
import com.treader.R
import com.treader.Store
import com.treader.ui.components.IconButtonText
import com.treader.ui.components.QuickAction
import com.treader.ui.library.Cover
import com.treader.ui.library.pr

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
            Text(stringResource(R.string.nav_home), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                IconButtonText("⋮") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_backup)) }, onClick = { menu = false; exp.launch("treader-backup.json") })
                    DropdownMenuItem(text = { Text(stringResource(R.string.import_backup)) }, onClick = { menu = false; imp.launch(arrayOf("application/json", "*/*")) })
                }
            }
            IconButtonText("🔍") { onFilter(LibFilter.None) }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onImport) { Text(stringResource(R.string.add_button)) }
        }
        Spacer(Modifier.height(14.dp))
        if (recent != null) Card(Modifier.fillMaxWidth().clickable { onOpen(recent) }, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(recent, Modifier.width(92.dp).aspectRatio(0.7f))
                Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(recent.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recent.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(stringResource(R.string.reading_progress, (recent.pr() * 100).toInt()), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator({ recent.pr() }, Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
        } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Text(stringResource(R.string.home_empty_reading), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction("♡", stringResource(R.string.quick_favorites), Color(0xFFC2536E)) { onFilter(LibFilter.Fav) }
            QuickAction("⤓", stringResource(R.string.quick_recent_added), Color(0xFF2F8FB3)) { onFilter(LibFilter.RecentAdded) }
            QuickAction("↗", stringResource(R.string.quick_recent_read), Color(0xFF2FA372)) { onFilter(LibFilter.None) }
            QuickAction("🗑", stringResource(R.string.quick_trash), Color(0xFFB3902F)) { onFilter(LibFilter.Trash) }
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(14.dp)) {
                Text(stringResource(R.string.home_recently_added_title), fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp))
                if (recentAdded.isEmpty()) Text(stringResource(R.string.home_no_books_added), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentAdded.forEach { b -> Column(Modifier.width(96.dp).clickable { onOpen(b) }) {
                        Cover(b, Modifier.fillMaxWidth().aspectRatio(0.7f))
                        Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp))
                    } }
                }
                Text(stringResource(R.string.total_books_count, active.size), Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
