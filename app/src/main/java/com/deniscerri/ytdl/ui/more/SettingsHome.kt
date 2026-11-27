package com.deniscerri.ytdl.ui.more

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.whatsapp.GlassPrimaryButton
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize

/**
 * Rows of "Ajustes", from the common to the advanced (diseño/imagenes/09-1-ajustes.png). Each [icon] is a picture in
 * full color, as the user asked on 03/11/2026, so the rows are told apart at a glance.
 */
enum class SettingsCategory(@DrawableRes val icon: Int, @StringRes val title: Int, @StringRes val summary: Int) {
    Appearance(R.drawable.vd_setting_appearance, R.string.vd_set_appearance, R.string.vd_set_appearance_summary),
    Home(R.drawable.vd_setting_home, R.string.vd_set_home, R.string.vd_set_home_summary),
    Downloads(R.drawable.vd_setting_downloads, R.string.vd_set_downloads, R.string.vd_set_downloads_summary),
    Accounts(R.drawable.vd_pref_cookie, R.string.vd_set_accounts, R.string.vd_set_accounts_summary),
    Folders(R.drawable.vd_setting_folders, R.string.vd_set_folders, R.string.vd_set_folders_summary),
    Media(R.drawable.vd_setting_media, R.string.vd_set_media, R.string.vd_set_media_summary),
    Engine(R.drawable.vd_setting_engine, R.string.vd_set_engine, R.string.vd_set_engine_summary),
    Backup(R.drawable.vd_setting_backup, R.string.vd_set_backup, R.string.vd_set_backup_summary),
    Advanced(R.drawable.vd_setting_advanced, R.string.vd_set_advanced, R.string.vd_set_advanced_summary),
    About(R.drawable.vd_setting_about, R.string.vd_set_about, R.string.vd_set_about_summary),
}

enum class SettingsPage { List, Advanced }

@Stable
class SettingsUiState {
    var page by mutableStateOf(SettingsPage.List)
    /** "Avanzado" shows its tools only after "Soy experto…" (pref vd_expert_mode); the user asked for it on 25/11/2026 */
    var expert by mutableStateOf(false)
}

interface SettingsActions {
    fun onCategory(category: SettingsCategory)
    fun onTerminal()
    fun onTemplates()
    fun onLogs()
    fun onWatchedChannels()
    fun onCloseApp()
    /** "Soy experto…" turns the tools on; "Ocultar…" hides them again */
    fun onExpert(expert: Boolean)
    /** Asks, then puts the settings of the advanced screen back as they came with the app */
    fun onResetAdvanced()
}

@Composable
fun SettingsHome(state: SettingsUiState, actions: SettingsActions) {
    AnimatedContent(
        targetState = state.page,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
        label = "settings",
    ) { page ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                SettingsPage.List -> {
                    Text(
                        stringResource(R.string.vd_nav_settings),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    SettingsGroup {
                        SettingsCategory.entries.forEachIndexed { index, category ->
                            if (index > 0) RowDivider()
                            SettingRow(category.icon, stringResource(category.title), stringResource(category.summary)) {
                                if (category == SettingsCategory.Advanced) state.page = SettingsPage.Advanced
                                else actions.onCategory(category)
                            }
                        }
                    }
                }
                SettingsPage.Advanced -> AdvancedPage(state, actions)
            }
        }
    }
}

/** "Ajustes › Avanzado": the tools that used to sit in "Más" (diseño/imagenes/11-4-avanzado.png). */
@Composable
private fun AdvancedPage(state: SettingsUiState, actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        IconButton(onClick = { state.page = SettingsPage.List }) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.vd_back), tint = colors.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.vd_set_advanced), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
    }
    if (!state.expert) {
        ExpertGate(actions)
        return
    }
    SectionTitle(stringResource(R.string.vd_adv_tools))
    SettingsGroup {
        SettingRow(R.drawable.vd_setting_terminal, stringResource(R.string.terminal), null, onClick = actions::onTerminal)
        RowDivider()
        SettingRow(R.drawable.vd_setting_templates, stringResource(R.string.vd_adv_templates), null, onClick = actions::onTemplates)
        RowDivider()
        SettingRow(R.drawable.vd_setting_logs, stringResource(R.string.vd_adv_logs), null, onClick = actions::onLogs)
        RowDivider()
        SettingRow(R.drawable.vd_setting_watched, stringResource(R.string.vd_adv_watched), null, onClick = actions::onWatchedChannels)
        RowDivider()
        SettingRow(R.drawable.vd_setting_more, stringResource(R.string.vd_adv_more), stringResource(R.string.vd_adv_more_summary)) {
            actions.onCategory(SettingsCategory.Advanced)
        }
    }
    Spacer(Modifier.height(4.dp))
    SettingsGroup {
        SettingRow(R.drawable.vd_pref_reset, stringResource(R.string.vd_expert_reset), stringResource(R.string.vd_expert_reset_summary), onClick = actions::onResetAdvanced)
        RowDivider()
        SettingRow(R.drawable.vd_pref_eye, stringResource(R.string.vd_expert_hide), null) { actions.onExpert(false) }
        RowDivider()
        SettingRow(R.drawable.vd_setting_close, stringResource(R.string.vd_adv_close_app), null, tint = colors.error, onClick = actions::onCloseApp)
    }
}

/** Before the advanced tools: who they are for, that they are used at one's own risk, and how to undo changes */
@Composable
private fun ExpertGate(actions: SettingsActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.vd_setting_advanced), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.vd_expert_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.vd_expert_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(LocalGlass.current.warning.copy(alpha = 0.14f))
            .border(1.dp, LocalGlass.current.warning.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(VdSize.CardPadding),
    ) {
        Icon(painterResource(R.drawable.vd_ic_warning), contentDescription = null, tint = LocalGlass.current.warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.vd_expert_risk), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(stringResource(R.string.vd_expert_reset_hint), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(4.dp))
    GlassPrimaryButton(stringResource(R.string.vd_expert_button), onClick = { actions.onExpert(true) })
}

/** A section's name in the main color with a short bar before it, as in the settings pages (GroupedPreferences.kt) */
@Composable
private fun SectionTitle(text: String) {
    val accent = LocalGlass.current.accentText
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
        Spacer(Modifier.size(width = 4.dp, height = 16.dp).clip(MaterialTheme.shapes.extraSmall).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = accent)
    }
}

/** Rows that belong together, in one card, like the phone's own settings */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().clip(shape).background(colors.surfaceContainer).border(1.dp, colors.outlineVariant, shape),
        content = content,
    )
}

/** The hairline between two rows of a group, starting under the text */
@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 60.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A row with its full-color [icon]; [tint] colors the title of a row that ends something, which has no arrow */
@Composable
private fun SettingRow(@DrawableRes icon: Int, title: String, summary: String?, tint: Color? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = VdSize.CardPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint ?: colors.onSurface)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (tint == null) Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
