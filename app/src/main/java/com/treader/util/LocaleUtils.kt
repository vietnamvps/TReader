package com.treader.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

fun Context.applyLocale(lang: String) {
    val systemLocale = Resources.getSystem().configuration.locales.get(0)
    val locale = when (lang) {
        "vi" -> Locale("vi")
        "en" -> Locale("en")
        else -> systemLocale
    }
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
}
