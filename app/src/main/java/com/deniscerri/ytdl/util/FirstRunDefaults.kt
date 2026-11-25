package com.deniscerri.ytdl.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * Video Downloader: settings applied once, on first launch, so the app starts in Spanish and dark mode,
 * and does not use accounts or contact third-party services unless the user changes it.
 * Bump [APPLIED_KEY] when adding a default, so installs that already ran the older set get it too.
 */
object FirstRunDefaults {
    private const val APPLIED_KEY = "first_run_defaults_v2"
    private const val LANGUAGE = "es"

    fun apply(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(APPLIED_KEY, false)) return

        useSpanishIfFollowingSystem(context)
        prefs.edit {
            putBoolean("use_cookies", false)
            putBoolean("use_sponsorblock", false)
            putBoolean("search_suggestions", false)
            putString("ytdlnis_theme", "Dark")
            putBoolean(APPLIED_KEY, true)
        }
    }

    private fun useSpanishIfFollowingSystem(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // AppCompat ignores locale changes until an activity exists, so talk to the framework directly
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            if (localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(LANGUAGE)
            }
        } else if (!LocaleUtil.isOverridden()) {
            LocaleUtil.setAppLocale(Locale.forLanguageTag(LANGUAGE))
        }
    }
}
