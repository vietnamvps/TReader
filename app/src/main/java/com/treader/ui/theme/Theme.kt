package com.treader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TTheme(appTheme: String = "system", content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appTheme) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val ctx = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF9FA8FF))
        else -> lightColorScheme(primary = Color(0xFF4A56E2))
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
