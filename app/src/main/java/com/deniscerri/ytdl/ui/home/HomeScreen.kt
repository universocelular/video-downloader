package com.deniscerri.ytdl.ui.home

import android.net.Uri
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.ui.tv.WatchOnTvSheet
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize

/** What Home shows below the link box. */
sealed interface HomePhase {
    data object Dashboard : HomePhase
    data class Analyzing(val url: String) : HomePhase
    data class Ready(val result: ResultItem) : HomePhase
    data class Failed(val url: String, val message: String) : HomePhase
}

/** A download in "En curso"; [progress] is null while the amount of work is unknown. */
@Immutable
data class HomeTask(val id: Long, val title: String, val thumb: String, val status: String, val progress: Float?)

/** State of the new Home (diseño/imagenes/02-1 to 02-3 and 16-2), owned by HomeFragment. */
@Stable
class HomeUiState {
    var input by mutableStateOf("")
    var clipboard by mutableStateOf<List<String>>(emptyList())
    var phase by mutableStateOf<HomePhase>(HomePhase.Dashboard)
    var tasks by mutableStateOf<List<HomeTask>>(emptyList())
    var hideThumbnails by mutableStateOf(false)
    /** Bumped to put the cursor in the link box. */
    var focusRequests by mutableIntStateOf(0)
    /** App and package updates found at startup; the old toolbar button that listed them is hidden under this screen */
    var updates by mutableIntStateOf(0)
}

interface HomeActions {
    fun onInputChange(text: String)
    fun onSubmit()
    fun onPaste()
    fun onClear()
    fun onUseClipboard()
    fun onDismissClipboard()
    fun onToolDownload()
    fun onToolSplit()
    fun onToolStatuses()
    fun onDownload(result: ResultItem)
    fun onSplit(url: String)
    fun onWatch(result: ResultItem)
    fun onRetry(url: String)
    fun onDownloadAnyway(url: String)
    fun onSeeAllTasks()
    fun onShowUpdates()
}

/** "youtube.com" from a link, for short labels. */
fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host?.removePrefix("www.")?.removePrefix("m.") }.getOrNull() ?: url

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 32.dp),
    ) {
        Header()
        AnimatedVisibility(visible = state.updates > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(16.dp))
                UpdateBanner(state.updates, actions)
            }
        }
        AnimatedVisibility(
            visible = state.clipboard.isNotEmpty() && state.phase == HomePhase.Dashboard && state.input.isBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                ClipboardBanner(state.clipboard, actions)
            }
        }
        Spacer(Modifier.height(20.dp))
        LinkBox(state, actions)
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = state.phase,
            contentKey = { it::class },
            transitionSpec = { (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.97f)) togetherWith fadeOut(tween(150)) },
            label = "phase",
        ) { phase ->
            when (phase) {
                HomePhase.Dashboard -> Dashboard(state, actions)
                is HomePhase.Analyzing -> AnalyzingCard(phase.url, actions)
                is HomePhase.Ready -> ReadyCard(phase.result, state.hideThumbnails, actions)
                is HomePhase.Failed -> FailedCard(phase, actions)
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        Image(painterResource(R.drawable.ic_app_icon), contentDescription = null, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** "Hay una versión nueva": opens the list of updates, where the app update downloads and asks to install */
@Composable
private fun UpdateBanner(count: Int, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.tertiaryContainer)
            .clickable(onClick = actions::onShowUpdates)
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.vd_pref_phone_update), contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            if (count == 1) stringResource(R.string.vd_home_update_one) else stringResource(R.string.vd_home_update_many, count),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onTertiaryContainer,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = actions::onShowUpdates,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(VdSize.SmallControl),
        ) {
            Text(stringResource(R.string.vd_home_update_action), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ClipboardBanner(urls: List<String>, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val text = if (urls.size == 1) stringResource(R.string.vd_home_clipboard_one, hostOf(urls.first()))
    else stringResource(R.string.vd_home_clipboard_many, urls.size)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.primaryContainer)
            .border(1.dp, colors.primary.copy(alpha = 0.6f), shape)
            .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_clipboard), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = actions::onUseClipboard,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(VdSize.SmallControl),
        ) {
            Text(stringResource(R.string.vd_home_use), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = actions::onDismissClipboard) {
            Icon(painterResource(R.drawable.baseline_close_24), stringResource(R.string.vd_home_not_now), tint = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun LinkBox(state: HomeUiState, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(state.focusRequests) {
        if (state.focusRequests > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val border by animateColorAsState(
        if (focused || state.phase is HomePhase.Analyzing) colors.primary else colors.outline,
        label = "border",
    )
    val big = MaterialTheme.typography.bodyLarge
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, border, shape)
            .clickable {
                focusRequester.requestFocus()
                keyboard?.show()
            }
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = state.input,
            onValueChange = actions::onInputChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = big.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                keyboard?.hide()
                actions.onSubmit()
            }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (state.input.isEmpty()) {
                        Text(stringResource(R.string.vd_home_input_hint), style = big, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            },
        )
        if (state.input.isEmpty()) {
            Button(
                onClick = actions::onPaste,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(VdSize.Control),
            ) {
                Icon(painterResource(R.drawable.ic_clipboard), contentDescription = null, modifier = Modifier.size(VdSize.ButtonIcon))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.vd_home_paste), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            IconButton(onClick = actions::onClear) {
                Icon(painterResource(R.drawable.baseline_close_24), stringResource(R.string.vd_home_clear), tint = colors.onSurfaceVariant)
            }
            if (state.phase == HomePhase.Dashboard) {
                FilledIconButton(onClick = {
                    keyboard?.hide()
                    actions.onSubmit()
                }) {
                    Icon(painterResource(R.drawable.outline_arrow_forward_24), stringResource(R.string.vd_home_go))
                }
            }
        }
    }
}

@Composable
private fun Dashboard(state: HomeUiState, actions: HomeActions) {
    var watchOnTv by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.tasks.isEmpty()) {
            ToolCard(R.drawable.vd_tool_download, R.string.vd_tool_download_title, R.string.vd_tool_download_summary, actions::onToolDownload)
            ToolCard(R.drawable.vd_tool_split, R.string.vd_tool_split_title, R.string.vd_tool_split_summary, actions::onToolSplit)
            ToolCard(R.drawable.vd_tool_statuses, R.string.vd_tool_statuses_title, R.string.vd_tool_statuses_summary, actions::onToolStatuses)
            ToolCard(R.drawable.vd_tool_tv, R.string.vd_tool_tv_title, R.string.vd_tool_tv_summary) { watchOnTv = true }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallTool(R.drawable.vd_tool_download, R.string.vd_tool_download_title, Modifier.weight(1f), actions::onToolDownload)
                SmallTool(R.drawable.vd_tool_split, R.string.vd_tool_split_short, Modifier.weight(1f), actions::onToolSplit)
                SmallTool(R.drawable.vd_tool_statuses, R.string.vd_tool_statuses_short, Modifier.weight(1f), actions::onToolStatuses)
                SmallTool(R.drawable.vd_tool_tv, R.string.vd_tool_tv_short, Modifier.weight(1f)) { watchOnTv = true }
            }
            Tasks(state, actions)
        }
        if (watchOnTv) WatchOnTvSheet(onDismiss = { watchOnTv = false })
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp)) {
            Icon(painterResource(R.drawable.baseline_share_24), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.vd_home_share_hint),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The surface card of Inicio and Archivos › En curso. */
internal fun Modifier.card(shape: androidx.compose.ui.graphics.Shape, fill: Color, line: Color) =
    clip(shape).background(fill).border(1.dp, line, shape)

// Compact, so the four tools fit on the screen together with the link box (the user asked for it on 03/11/2026)
@Composable
private fun ToolCard(@DrawableRes icon: Int, @StringRes title: Int, @StringRes summary: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = VdSize.CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(VdSize.ToolIcon))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SmallTool(@DrawableRes icon: Int, @StringRes label: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Tasks(state: HomeUiState, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vd_files_in_progress), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            TextButton(onClick = actions::onSeeAllTasks) {
                Text(stringResource(R.string.vd_home_see_all), color = LocalGlass.current.accentText, style = MaterialTheme.typography.labelLarge)
            }
        }
        state.tasks.take(3).forEach { task ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
                    .clickable(onClick = actions::onSeeAllTasks)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(task.thumb, state.hideThumbnails, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(task.status, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    task.progress?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = colors.primary,
                            trackColor = colors.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Thumbnail(url: String, hide: Boolean, modifier: Modifier) {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    key(url, hide) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(placeholder)
                    if (url.isNotBlank()) loadThumbnail(hide, url)
                }
            },
            modifier = modifier,
        )
    }
}

/** "Analizando el enlace…": an empty card that a violet light sweeps across (diseño/imagenes/16-2-anim-analizando.png). */
@Composable
private fun AnalyzingCard(url: String, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val transition = rememberInfiniteTransition(label = "analyzing")
    val sweep by transition.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    val glow by transition.animateFloat(0.25f, 0.95f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")
    val shine = colors.primary.copy(alpha = 0.38f)
    val shape = MaterialTheme.shapes.medium
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = glass.accentText, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vd_home_analyzing), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainer)
                .border(1.5.dp, colors.primary.copy(alpha = glow), shape)
                .drawWithContent {
                    drawContent()
                    val x = size.width * sweep
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, shine, Color.Transparent),
                            start = Offset(x - size.width * 0.22f, 0f),
                            end = Offset(x + size.width * 0.22f, size.height),
                        ),
                    )
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(width = 112.dp, height = 72.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_video), contentDescription = null, tint = colors.onSurfaceVariant.copy(alpha = 0.5f))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surfaceContainerHighest))
                Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surfaceContainerHighest))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = actions::onClear) {
            Text(stringResource(R.string.cancel), color = glass.accentText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The analyzed video with its two actions (diseño/imagenes/02-2-inicio-enlace.png). */
@Composable
private fun ReadyCard(result: ResultItem, hideThumb: Boolean, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    val big = RoundedCornerShape(20.dp)
    val duration = result.duration.takeUnless { it.isBlank() || it == "-1" || it == "0:00" }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().card(big, colors.surfaceContainer, colors.outlineVariant).padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHighest)
                    .clickable { actions.onWatch(result) },
                contentAlignment = Alignment.Center,
            ) {
                Thumbnail(result.thumb, hideThumb, Modifier.fillMaxSize())
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_vd_play), stringResource(R.string.vd_home_watch), tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(result.title.ifBlank { result.url }, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(hostOf(result.url), duration).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            BigButton(R.drawable.baseline_download_24, stringResource(R.string.vd_tool_download_title), filled = true) { actions.onDownload(result) }
            Spacer(Modifier.height(10.dp))
            BigButton(R.drawable.ic_cut, stringResource(R.string.vd_tool_split_title), filled = false) { actions.onSplit(result.url) }
        }
        ToolCard(R.drawable.vd_tool_statuses, R.string.vd_tool_statuses_title, R.string.vd_tool_statuses_summary, actions::onToolStatuses)
    }
}

@Composable
private fun FailedCard(phase: HomePhase.Failed, actions: HomeActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.baseline_error_24), contentDescription = null, tint = colors.error)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.vd_home_failed_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        }
        Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        BigButton(R.drawable.ic_link, stringResource(R.string.vd_home_retry), filled = true) { actions.onRetry(phase.url) }
        BigButton(R.drawable.baseline_download_24, stringResource(R.string.vd_home_download_anyway), filled = false) { actions.onDownloadAnyway(phase.url) }
    }
}

@Composable
fun BigButton(@DrawableRes icon: Int, text: String, filled: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(VdSize.ButtonIcon))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val size = modifier.fillMaxWidth().heightIn(min = VdSize.Control)
    if (filled) {
        Button(onClick = onClick, modifier = size, enabled = enabled, shape = MaterialTheme.shapes.small, content = content)
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = size,
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.5.dp, LocalGlass.current.accentText),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = content,
        )
    }
}
