package com.treader.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.treader.Book
import com.treader.Bookmark
import com.treader.R
import com.treader.Store
import com.treader.ui.library.Cover
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Notes(s: Store, onOpen: (Book, Bookmark) -> Unit) {
    val marks by remember { derivedStateOf { s.bookmarks.sortedByDescending { it.time } } }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
        item { Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
        item { Text(stringResource(R.string.notes_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)) }
        if (marks.isEmpty()) item { Text(stringResource(R.string.notes_empty),
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
