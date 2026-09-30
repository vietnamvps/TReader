package com.treader.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.treader.Book
import com.treader.LibFilter
import com.treader.R
import com.treader.Store
import com.treader.ui.components.IconButtonText
import com.treader.ui.components.QuickAction
import com.treader.ui.library.Cover
import com.treader.ui.library.pr
import com.treader.ui.library.prPercent

@Composable
fun Home(s: Store, onOpen: (Book) -> Unit, onFilter: (LibFilter) -> Unit, onImport: () -> Unit) {
    val ctx = LocalContext.current
    val active by remember { derivedStateOf { s.books.filter { !it.trashed } } }
    val recent = active.maxByOrNull { it.last }?.takeIf { it.last > 0 }
    val recentAdded = active.sortedByDescending { it.addedAt }.take(4)
    var menu by remember { mutableStateOf(false) }

    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(s.json().toByteArray()) } }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { runCatching { s.load(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText(), true); s.save() } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 8.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP APP BAR
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.nav_home),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButtonText("⋮") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_backup)) },
                        onClick = { menu = false; exp.launch("treader-backup.json") }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_backup)) },
                        onClick = { menu = false; imp.launch(arrayOf("application/json", "*/*")) }
                    )
                }
            }
            IconButtonText("🔍") { onFilter(LibFilter.None) }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(onImport) { Text(stringResource(R.string.add_button)) }
        }

        // 2. "ĐANG ĐỌC" (Reading Now) HERO CARD
        if (recent != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(recent) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cover(recent, Modifier.width(84.dp).aspectRatio(0.68f))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                stringResource(R.string.reading_now),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            recent.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            recent.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { recent.pr() },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        )
                        Text(
                            stringResource(R.string.reading_progress_fmt, recent.prPercent(), (recent.sec / 60).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Text(
                    stringResource(R.string.home_empty_reading),
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 3. QUICK ACTION BUTTONS
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction("♡", stringResource(R.string.quick_favorites), Color(0xFFC2536E)) { onFilter(LibFilter.Fav) }
            QuickAction("⤓", stringResource(R.string.quick_recent_added), Color(0xFF2F8FB3)) { onFilter(LibFilter.RecentAdded) }
            QuickAction("↗", stringResource(R.string.quick_recent_read), Color(0xFF2FA372)) { onFilter(LibFilter.None) }
            QuickAction("🗑", stringResource(R.string.quick_trash), Color(0xFFB3902F)) { onFilter(LibFilter.Trash) }
        }

        // 4. "THÊM GẦN ĐÂY" (RECENTLY ADDED) REDESIGNED
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.home_recently_added_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onFilter(LibFilter.RecentAdded) }) {
                    Text(
                        stringResource(R.string.see_all),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (recentAdded.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text(
                        stringResource(R.string.home_no_books_added),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                recentAdded.forEach { b ->
                    val percent = b.prPercent()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(b) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cover(b, Modifier.width(54.dp).aspectRatio(0.68f))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        stringResource(R.string.added_recently_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    b.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    b.author,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { b.pr() },
                                        modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                                        color = if (percent >= 100) Color(0xFF388E3C) else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        text = if (percent == 0) stringResource(R.string.progress_unread)
                                               else if (percent >= 100) stringResource(R.string.reading_completed)
                                               else "$percent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = if (percent > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    stringResource(R.string.total_books_count, active.size),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
