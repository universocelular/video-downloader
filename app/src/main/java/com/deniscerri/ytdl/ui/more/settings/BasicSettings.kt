package com.deniscerri.ytdl.ui.more.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import com.deniscerri.ytdl.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The settings pages show only what most people use; the rest appears after "Mostrar opciones avanzadas" at the end
 * of a page (the user asked for it on 25/11/2026). The same choice, [EXPERT_MODE], unlocks Avanzado in the Ajustes tab.
 */
object BasicSettings {
    const val EXPERT_MODE = "vd_expert_mode"
    private const val TOGGLE = "vd_toggle_advanced"

    /** The settings everyone sees; every other one in a page with a toggle is advanced */
    private val BASIC = setOf(
        // Apariencia y búsqueda
        "app_language", "ytdlnis_theme", "ytdlnis_icon", "theme_accent", "high_contrast", "hide_thumbnails",
        "search_engine", "check_clipboard_home", "search_suggestions", "recommendations_home",
        // Descargas
        "incognito", "download_card", "preferred_download_type", "prevent_duplicate_downloads", "metered_networks",
        "concurrent_downloads", "use_scheduler", "schedule_start", "schedule_end",
        // Carpetas y nombres
        "music_path", "video_path", "save_subdirectory", "temporary_files",
        // Video y audio
        "use_sponsorblock", "embed_metadata", "audio_format", "audio_bitrate", "embed_thumbnail", "embed_lyrics",
        "video_quality", "video_format", "embed_subtitles", "subs_lang", "add_chapters",
        // Motor de descargas, yt-dlp and Aplicación
        "ytdlp_update_screen", "app_update_screen", "auto_update_ytdlp", "update_app", "changelog",
        // on every page
        "reset_preferences",
    )

    fun isExpert(context: Context) = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(EXPERT_MODE, false)

    /** Hides the advanced settings of [screen] unless the person chose to see them, and adds the switch at its end */
    fun apply(screen: PreferenceScreen, context: Context) {
        if (leaves(screen).none { it.key != null && it.key !in BASIC && it.key != TOGGLE }) return
        if (screen.findPreference<Preference>(TOGGLE) == null) {
            screen.addPreference(Preference(context).apply {
                key = TOGGLE
                order = Int.MAX_VALUE
                isPersistent = false
                setOnPreferenceClickListener {
                    if (isExpert(context)) setExpert(context, screen, false) else confirm(context, screen)
                    true
                }
            })
        }
        show(screen, context)
    }

    private fun confirm(context: Context, screen: PreferenceScreen) {
        MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.vd_setting_advanced)
            .setTitle(R.string.vd_expert_title)
            .setMessage(context.getString(R.string.vd_expert_risk) + ".\n\n" + context.getString(R.string.vd_expert_page_reset_hint))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.vd_expert_button_short) { _, _ -> setExpert(context, screen, true) }
            .show()
    }

    private fun setExpert(context: Context, screen: PreferenceScreen, expert: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(EXPERT_MODE, expert) }
        show(screen, context)
    }

    private fun show(screen: PreferenceScreen, context: Context) {
        val expert = isExpert(context)
        leaves(screen).forEach { if (it.key != TOGGLE) it.isVisible = expert || it.key in BASIC }
        // a section whose settings are all hidden hides its title too
        groups(screen).filterIsInstance<PreferenceCategory>().forEach { category ->
            category.isVisible = (0 until category.preferenceCount).any { category.getPreference(it).isVisible }
        }
        screen.findPreference<Preference>(TOGGLE)?.apply {
            setTitle(if (expert) R.string.vd_expert_hide else R.string.vd_expert_show)
            setSummary(if (expert) R.string.vd_expert_hide_summary else R.string.vd_expert_show_summary)
            setIcon(if (expert) R.drawable.vd_pref_eye else R.drawable.vd_setting_advanced)
        }
    }

    private fun leaves(group: PreferenceGroup): List<Preference> = (0 until group.preferenceCount).flatMap {
        val p = group.getPreference(it)
        if (p is PreferenceGroup) leaves(p) else listOf(p)
    }

    private fun groups(group: PreferenceGroup): List<PreferenceGroup> = (0 until group.preferenceCount).flatMap {
        val p = group.getPreference(it)
        if (p is PreferenceGroup) listOf(p) + groups(p) else emptyList()
    }
}
