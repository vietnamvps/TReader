package com.treader.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.treader.R
import com.treader.Reminder
import com.treader.Store
import com.treader.ui.components.SettingsGroup
import com.treader.ui.components.SettingsPage
import com.treader.ui.components.SettingsRow
import com.treader.ui.library.CoverCache
import com.treader.ui.theme.ReadingFonts
import com.treader.ui.theme.ReadingThemes
import com.treader.util.applyLocale
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

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
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        SettingsGroup {
            SettingsRow("🔔", Color(0xFF4A90D9), stringResource(R.string.general_settings), stringResource(R.string.general_settings_sub)) { onGo(SettingsScreen.General) }
            HorizontalDivider()
            SettingsRow("📖", Color(0xFF3FA37A), stringResource(R.string.reading_options), stringResource(R.string.reading_options_sub)) { onGo(SettingsScreen.Reading) }
            HorizontalDivider()
            SettingsRow("🎨", Color(0xFF3FA37A), stringResource(R.string.appearance_settings), stringResource(R.string.appearance_settings_sub)) { onGo(SettingsScreen.Appearance) }
            HorizontalDivider()
            SettingsRow("📊", Color(0xFF3FA37A), stringResource(R.string.reading_stats)) { onGo(SettingsScreen.Stats) }
        }
        SettingsGroup {
            SettingsRow("✉️", Color(0xFFD9A23F), stringResource(R.string.send_feedback)) { onGo(SettingsScreen.Feedback) }
            HorizontalDivider()
            SettingsRow("ℹ️", Color(0xFFD9A23F), stringResource(R.string.about_app)) { onGo(SettingsScreen.About) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.backup_restore), Modifier.clickable { showBackup = true }.padding(8.dp), color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.clear_cache), Modifier.clickable { confirmClearCache = true }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.wipe_data), Modifier.clickable { confirmWipe1 = true }.padding(8.dp), color = MaterialTheme.colorScheme.error)
        }
    }
    if (showBackup) {
        val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
            u?.let { ctx.contentResolver.openOutputStream(it)?.use { o -> o.write(s.json().toByteArray()) } }; showBackup = false
        }
        val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
            u?.let { runCatching { s.load(ctx.contentResolver.openInputStream(it)!!.bufferedReader().readText(), true); s.save() } }; showBackup = false
        }
        AlertDialog(onDismissRequest = { showBackup = false }, title = { Text(stringResource(R.string.backup_dialog_title)) },
            text = { Text(stringResource(R.string.backup_dialog_desc)) },
            confirmButton = { TextButton({ exp.launch("treader-backup.json") }) { Text(stringResource(R.string.export_backup_btn)) } },
            dismissButton = { TextButton({ imp.launch(arrayOf("application/json", "*/*")) }) { Text(stringResource(R.string.import_backup_btn)) } })
    }
    if (confirmClearCache) AlertDialog(onDismissRequest = { confirmClearCache = false }, title = { Text(stringResource(R.string.clear_cache_dialog_title)) },
        text = { Text(stringResource(R.string.clear_cache_dialog_desc)) },
        confirmButton = { TextButton({
            runCatching { File(s.ctx.filesDir, "covers").deleteRecursively() }; CoverCache.c.evictAll()
            confirmClearCache = false; toast = ctx.getString(R.string.cleared_cache_toast)
        }) { Text(stringResource(R.string.clear_btn)) } },
        dismissButton = { TextButton({ confirmClearCache = false }) { Text(stringResource(R.string.cancel)) } })
    if (confirmWipe1) AlertDialog(onDismissRequest = { confirmWipe1 = false }, title = { Text(stringResource(R.string.wipe_data_dialog1_title)) },
        text = { Text(stringResource(R.string.wipe_data_dialog1_desc)) },
        confirmButton = { TextButton({ confirmWipe1 = false; confirmWipe2 = true }) { Text(stringResource(R.string.continue_btn), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmWipe1 = false }) { Text(stringResource(R.string.cancel)) } })
    if (confirmWipe2) AlertDialog(onDismissRequest = { confirmWipe2 = false }, title = { Text(stringResource(R.string.wipe_data_dialog2_title)) },
        text = { Text(stringResource(R.string.wipe_data_dialog2_desc)) },
        confirmButton = { TextButton({ s.wipeAll(); Reminder.cancel(ctx); confirmWipe2 = false }) { Text(stringResource(R.string.wipe_all_btn), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmWipe2 = false }) { Text(stringResource(R.string.cancel)) } })
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
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            s.prefs = s.prefs.copy(remindOn = true); s.save()
            Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin)
        }
    }

    fun updateLang(code: String) {
        ctx.applyLocale(code)
        ctx.applicationContext.applyLocale(code)
        s.prefs = s.prefs.copy(lang = code)
        s.save()
    }

    SettingsPage(stringResource(R.string.general_settings), onBack) {
        // 1. APP THEME MODE
        Text(
            stringResource(R.string.app_theme_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        SettingsGroup {
            val themeOptions = listOf(
                "system" to (stringResource(R.string.app_theme_system) to "⚙️"),
                "light" to (stringResource(R.string.app_theme_light) to "☀️"),
                "dark" to (stringResource(R.string.app_theme_dark) to "🌙")
            )
            themeOptions.forEachIndexed { index, (code, info) ->
                val (label, icon) = info
                val selected = s.prefs.appTheme == code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            s.prefs = s.prefs.copy(appTheme = code)
                            s.save()
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        icon,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selected,
                        onClick = {
                            s.prefs = s.prefs.copy(appTheme = code)
                            s.save()
                        }
                    )
                }
                if (index < themeOptions.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 2. LANGUAGE
        Text(
            stringResource(R.string.language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            val options = listOf(
                "system" to (stringResource(R.string.lang_system) to "🌐"),
                "vi" to (stringResource(R.string.lang_vi) to "🇻🇳"),
                "en" to (stringResource(R.string.lang_en) to "🇬🇧")
            )
            options.forEachIndexed { index, (code, info) ->
                val (label, flag) = info
                val selected = s.prefs.lang == code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { updateLang(code) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        flag,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    RadioButton(
                        selected = selected,
                        onClick = { updateLang(code) }
                    )
                }
                if (index < options.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            stringResource(R.string.daily_reminder),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.daily_reminder), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.at_time, s.prefs.remindHour, s.prefs.remindMin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.prefs.remindOn,
                        onCheckedChange = { on ->
                            if (on) {
                                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else { s.prefs = s.prefs.copy(remindOn = true); s.save(); Reminder.schedule(ctx, s.prefs.remindHour, s.prefs.remindMin) }
                            } else { s.prefs = s.prefs.copy(remindOn = false); s.save(); Reminder.cancel(ctx) }
                        }
                    )
                }
                if (s.prefs.remindOn) {
                    OutlinedButton({
                        TimePickerDialog(ctx, { _, h, m ->
                            s.prefs = s.prefs.copy(remindHour = h, remindMin = m); s.save(); Reminder.schedule(ctx, h, m)
                        }, s.prefs.remindHour, s.prefs.remindMin, true).show()
                    }) { Text(stringResource(R.string.change_reminder_time)) }
                }
            }
        }
    }
}

@Composable
fun AppearanceSettings(s: Store, onBack: () -> Unit) {
    SettingsPage(stringResource(R.string.appearance_settings), onBack) {
        Text(stringResource(R.string.bg_color_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ReadingThemes.list.forEach { theme ->
                val isSelected = s.prefs.theme == theme.id
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { s.prefs = s.prefs.copy(theme = theme.id); s.save() }) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(theme.bg)
                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("A", color = theme.fg, fontWeight = FontWeight.Bold) }
                    Text(stringResource(theme.nameResId), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ReadingSettings(s: Store, onBack: () -> Unit) {
    val p = s.prefs
    val themeSpec = ReadingThemes.get(p.theme)

    SettingsPage(stringResource(R.string.reading_options), onBack) {
        // 1. LIVE BOOK PREVIEW CARD
        Text(
            stringResource(R.string.reading_preview_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = themeSpec.bg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = p.margin.coerceAtLeast(12).dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Chương 1: Bình Minh Mới",
                    color = themeSpec.fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = (p.size * 1.15f).sp,
                    fontFamily = ReadingFonts.getFontFamily(p.font),
                    lineHeight = (p.size * 1.15f * p.line).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.reading_preview_sample),
                    color = themeSpec.fg,
                    fontSize = p.size.sp,
                    fontFamily = ReadingFonts.getFontFamily(p.font),
                    lineHeight = (p.size * p.line).sp,
                    textAlign = TextAlign.Justify
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 2. FONT SELECTION SECTION
        Text(
            stringResource(R.string.font_family_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            Column(Modifier.padding(vertical = 6.dp)) {
                ReadingFonts.list.forEachIndexed { index, fontSpec ->
                    val selected = p.font == fontSpec.code
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                s.prefs = p.copy(font = fontSpec.code)
                                s.save()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(fontSpec.nameResId),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Aa Bb Cc 123",
                                fontFamily = fontSpec.fontFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        RadioButton(
                            selected = selected,
                            onClick = {
                                s.prefs = p.copy(font = fontSpec.code)
                                s.save()
                            }
                        )
                    }
                    if (index < ReadingFonts.list.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 3. TYPOGRAPHY & LAYOUT SLIDERS
        Text(
            stringResource(R.string.layout_options_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Font Size
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.font_size_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("${p.size} sp") }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(
                            value = p.size.toFloat(),
                            onValueChange = { s.prefs = p.copy(size = it.toInt()) },
                            valueRange = 12f..32f,
                            onValueChangeFinished = { s.save() },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text("A", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider()

                // Line Spacing
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.line_spacing_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("%.1f".format(p.line)) }
                        )
                    }
                    Slider(
                        value = p.line,
                        onValueChange = { s.prefs = p.copy(line = it) },
                        valueRange = 1.2f..2.2f,
                        onValueChangeFinished = { s.save() }
                    )
                }

                HorizontalDivider()

                // Margin
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.margin_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("${p.margin} px") }
                        )
                    }
                    Slider(
                        value = p.margin.toFloat(),
                        onValueChange = { s.prefs = p.copy(margin = it.toInt()) },
                        valueRange = 0f..40f,
                        onValueChangeFinished = { s.save() }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 4. IMAGES IN EPUB
        Text(
            stringResource(R.string.images_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val imgOpts = listOf(
                    0 to stringResource(R.string.img_hide),
                    1 to stringResource(R.string.img_compact),
                    2 to stringResource(R.string.img_full)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imgOpts.forEach { (i, n) ->
                        val selected = p.img == i
                        FilterChip(
                            selected = selected,
                            onClick = { s.prefs = p.copy(img = i); s.save() },
                            label = { Text(n) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.reading_options_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun StatsSettings(s: Store, onBack: () -> Unit) {
    val goal = s.prefs.goal
    val todayMin = ((s.daily[s.today()] ?: 0) / 60).toInt()

    var streakDate = LocalDate.now()
    var streak = 0
    while ((s.daily[streakDate.toString()] ?: 0) >= goal * 60L) {
        streak++
        streakDate = streakDate.minusDays(1)
    }

    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val dayMins = days.map { ((s.daily[it.toString()] ?: 0) / 60).toInt() }
    val maxMin = maxOf(dayMins.maxOrNull() ?: 0, goal, 1)
    val avgMin = if (dayMins.isNotEmpty()) dayMins.average().toInt() else 0

    val activeBooks = s.books.filter { !it.trashed }
    val totalSec = activeBooks.sumOf { it.sec }
    val totalHours = (totalSec / 3600).toInt()
    val totalMins = ((totalSec % 3600) / 60).toInt()

    val daysOfWeek = listOf(
        stringResource(R.string.mon), stringResource(R.string.tue),
        stringResource(R.string.wed), stringResource(R.string.thu),
        stringResource(R.string.fri), stringResource(R.string.sat),
        stringResource(R.string.sun)
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val cardBg = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

    SettingsPage(stringResource(R.string.stats_title), onBack) {
        // 1. TODAY'S PROGRESS RING CARD
        Card(modifier = Modifier.fillMaxWidth(), colors = cardBg) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.stats_today_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val strokeWidth = 18.dp.toPx()
                        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round)
                        drawArc(trackColor, 0f, 360f, false, style = stroke, size = Size(size.width, size.height))
                        val sweepAngle = 360f * (todayMin.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                        drawArc(primaryColor, -90f, sweepAngle, false, style = stroke, size = Size(size.width, size.height))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$todayMin",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.minutes_unit, goal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (todayMin >= goal) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (todayMin >= goal)
                            stringResource(R.string.stats_goal_reached)
                        else
                            stringResource(R.string.stats_goal_remaining, (goal - todayMin)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(stringResource(R.string.goal_per_day), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = { s.prefs = s.prefs.copy(goal = maxOf(5, goal - 5)); s.save() },
                            modifier = Modifier.size(36.dp)
                        ) { Text("−", fontWeight = FontWeight.Bold) }
                        Text("$goal m", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        FilledTonalIconButton(
                            onClick = { s.prefs = s.prefs.copy(goal = goal + 5); s.save() },
                            modifier = Modifier.size(36.dp)
                        ) { Text("+", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. METRICS OVERVIEW GRID
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFF6F00).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🔥", style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.stats_streak_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$streak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.consecutive_days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF0288D1).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("⏱️", style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.stats_total_time_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        stringResource(R.string.stats_hours_mins_fmt, totalHours, totalMins),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.total_time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF388E3C).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("📚", style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.stats_active_books_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${activeBooks.size}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.books_count, activeBooks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF7B1FA2).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("📊", style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.stats_daily_avg_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$avgMin m", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.stats_mins_fmt, avgMin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. WEEKLY BAR CHART CARD
        Text(
            stringResource(R.string.stats_weekly_chart_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(modifier = Modifier.fillMaxWidth(), colors = cardBg) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { i, dt ->
                        val mins = dayMins[i]
                        val isToday = dt == LocalDate.now()
                        val heightFrac = (mins.toFloat() / maxMin).coerceIn(0.05f, 1f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(
                                text = if (mins > 0) "${mins}m" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mins >= goal) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .weight(heightFrac, fill = false)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(
                                        when {
                                            isToday -> primaryColor
                                            mins >= goal -> primaryColor.copy(alpha = 0.85f)
                                            mins > 0 -> primaryColor.copy(alpha = 0.35f)
                                            else -> trackColor
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (dt.dayOfWeek.value == 7) stringResource(R.string.sun) else daysOfWeek[dt.dayOfWeek.value - 1],
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) primaryColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. TOP BOOKS BY READING TIME
        Text(
            stringResource(R.string.stats_by_book_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        SettingsGroup {
            val topBooks = activeBooks.sortedByDescending { it.sec }.take(5)
            if (topBooks.isEmpty()) {
                Text(
                    stringResource(R.string.stats_top_books_empty),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxBookSec = maxOf(topBooks.firstOrNull()?.sec ?: 1L, 1L)
                topBooks.forEachIndexed { index, book ->
                    val mins = (book.sec / 60).toInt()
                    val frac = (book.sec.toFloat() / maxBookSec).coerceIn(0.05f, 1f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "#${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < topBooks.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    SettingsPage(stringResource(R.string.about_title), onBack) {
        Text("TReader", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.version_x, ver), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FeedbackSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val emailSubject = stringResource(R.string.email_subject)
    SettingsPage(stringResource(R.string.feedback_title), onBack) {
        Text(stringResource(R.string.feedback_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button({
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_SUBJECT, emailSubject)
            runCatching { ctx.startActivity(i) }
        }) { Text(stringResource(R.string.open_email)) }
    }
}
