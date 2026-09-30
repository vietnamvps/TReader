package com.treader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.treader.R

data class ReadingThemeSpec(
    val id: Int,
    val nameResId: Int,
    val bg: Color,
    val fg: Color,
    val cssBg: String
)

object ReadingThemes {
    val list = listOf(
        ReadingThemeSpec(0, R.string.theme_light, Color(0xFFFFFFFF), Color(0xFF111111), "background:#FFFFFF!important;"),
        ReadingThemeSpec(1, R.string.theme_dark, Color(0xFF121212), Color(0xFFDDDDDD), "background:#121212!important;"),
        ReadingThemeSpec(2, R.string.theme_sepia, Color(0xFFF4ECD8), Color(0xFF3B3226), "background:#F4ECD8!important;"),
        ReadingThemeSpec(3, R.string.theme_scratched, Color(0xFFEFE6D5), Color(0xFF2C2219),
            "background-color:#EFE6D5!important;background-image:repeating-linear-gradient(45deg,rgba(0,0,0,0.025) 0px,rgba(0,0,0,0.025) 2px,transparent 2px,transparent 8px),repeating-linear-gradient(-45deg,rgba(0,0,0,0.02) 0px,rgba(0,0,0,0.02) 1px,transparent 1px,transparent 6px)!important;"),
        ReadingThemeSpec(4, R.string.theme_grainy, Color(0xFFF0EAE1), Color(0xFF2A2B2A),
            "background-color:#F0EAE1!important;background-image:radial-gradient(rgba(0,0,0,0.05) 1px,transparent 0)!important;background-size:6px 6px!important;"),
        ReadingThemeSpec(5, R.string.theme_dark_sepia, Color(0xFF26201B), Color(0xFFD4C5B9), "background:#26201B!important;"),
        ReadingThemeSpec(6, R.string.theme_mint, Color(0xFFE3EAD8), Color(0xFF233225), "background:#E3EAD8!important;")
    )

    fun get(id: Int): ReadingThemeSpec = list.firstOrNull { it.id == id } ?: list[0]
}

data class ReadingFontSpec(
    val code: String,
    val nameResId: Int,
    val fontFamily: FontFamily
)

object ReadingFonts {
    val list = listOf(
        ReadingFontSpec("serif", R.string.font_serif, FontFamily.Serif),
        ReadingFontSpec("sans-serif", R.string.font_sans, FontFamily.SansSerif),
        ReadingFontSpec("sans-serif-medium", R.string.font_medium, FontFamily.SansSerif),
        ReadingFontSpec("sans-serif-condensed", R.string.font_condensed, FontFamily.SansSerif),
        ReadingFontSpec("monospace", R.string.font_mono, FontFamily.Monospace),
        ReadingFontSpec("casual", R.string.font_casual, FontFamily.Cursive),
        ReadingFontSpec("cursive", R.string.font_cursive, FontFamily.Cursive)
    )

    fun getFontFamily(code: String): FontFamily =
        list.firstOrNull { it.code == code }?.fontFamily ?: FontFamily.Serif
}

fun themeColors(t: Int): Pair<Color, Color> {
    val spec = ReadingThemes.get(t)
    return spec.bg to spec.fg
}
