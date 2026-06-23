package com.github.trivialloop.scorehub.utils

/**
 * Central registry of languages supported by the app.
 *
 * Update this list whenever a new `values-XX` folder is added (e.g. once
 * Weblate provides a complete-enough `values-de`). This is the single
 * source of truth used by SettingsActivity's language spinner and by
 * LocaleHelper, instead of hardcoding "en"/"fr" in multiple places.
 */
object SupportedLanguages {

    data class LanguageEntry(
        val code: String,        // ISO 639-1 code, must match the values-XX suffix
        val displayNameResId: Int // string resource for the human-readable name
    )

    // NOTE: "en" has no values-XX suffix (it's the default `values/` folder)
    val ALL: List<LanguageEntry> = listOf(
        LanguageEntry("en", com.github.trivialloop.scorehub.R.string.english),
        LanguageEntry("fr", com.github.trivialloop.scorehub.R.string.french)
        // Add new languages here as they become available, e.g.:
        // LanguageEntry("de", com.github.trivialloop.scorehub.R.string.german)
    )

    fun codes(): List<String> = ALL.map { it.code }

    fun indexOf(code: String): Int = ALL.indexOfFirst { it.code == code }.let { if (it < 0) 0 else it }
}
