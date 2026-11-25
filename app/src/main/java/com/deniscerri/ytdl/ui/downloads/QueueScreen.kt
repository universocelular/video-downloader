package com.deniscerri.ytdl.ui.downloads

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.home.BigButton
import com.deniscerri.ytdl.ui.home.Thumbnail
import com.deniscerri.ytdl.ui.home.card
import com.deniscerri.ytdl.whatsapp.GlassChip
import com.deniscerri.ytdl.whatsapp.GlassProgress
import com.deniscerri.ytdl.whatsapp.Illustration
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize
import kotlinx.coroutines.delay

/** The five lists of Archivos › En curso (diseño/imagenes/08-2-en-curso.png to 08-4-programados.png). */
enum class QueueTab(@StringRes val label: Int, @StringRes val empty: Int, @DrawableRes val emptyIcon: Int) {
    Now(R.string.vd_queue_now, R.string.vd_queue_empty_now, R.drawable.baseline_download_24),
    Scheduled(R.string.vd_queue_scheduled, R.string.vd_queue_empty_scheduled, R.drawable.ic_clock),
    Errors(R.string.vd_queue_errors, R.string.vd_queue_empty_errors, R.drawable.vd_ic_warning),
    Cancelled(R.string.vd_queue_cancelled, R.string.vd_queue_empty_cancelled, R.drawable.baseline_close_24),
    Saved(R.string.vd_queue_saved, R.string.vd_queue_empty_saved, R.drawable.baseline_save_alt_24),
}

/** What a card is, which decides its buttons. */
enum class QueueKind { Running, Paused, Waiting, Scheduled, Failed, Cancelled, Saved }

/** One card. The fragment writes its texts: the status, when it starts, why it failed. */
@Immutable
data class QueueEntry(
    val id: Long,
    val kind: QueueKind,
    val title: String,
    val thumb: String,
    val duration: String,
    val line: String,
    val progress: Float? = null,
    val speed: String? = null,
)

/** The cards of one tab, kept with their tab so a switch never shows the previous cards under the new chip. */
@Immutable
data class QueueList(val tab: QueueTab, val entries: List<QueueEntry>)

/** "Pausar todo" or "Reanudar todo" above the cards, as DownloadViewModel.pausedAllDownloads says. */
enum class PauseAll { Hidden, Pause, Resume }

/** State of Archivos › En curso, owned by DownloadQueueMainFragment. */
@Stable
class QueueUiState {
    var tab by mutableStateOf(QueueTab.Now)
    var list by mutableStateOf<QueueList?>(null)
    var pauseAll by mutableStateOf(PauseAll.Hidden)
    var hideThumbnails by mutableStateOf(false)
}

interface QueueActions {
    /** "Listos" in the selector. */
    fun onReady()
    fun onGoHome()
    fun onPause(id: Long)
    fun onResume(id: Long)
    fun onCancel(id: Long)
    fun onPauseAll()
    fun onResumeAll()
    fun onCancelAll()
    /** Every card of "Ahora" in the order on screen after the user dragged one; only the queued ones take it. */
    fun onReorder(ids: List<Long>)
    fun onDownloadNow(id: Long)
    fun onDownloadAll()
    fun onRetry(id: Long)
    fun onRetryAll()
    fun onDetail(id: Long)
    fun onDelete(id: Long)
    fun onDeleteAll()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(state: QueueUiState, actions: QueueActions) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        // as tall as the title row of Listos with its search button, so the selector stays put when switching
        Row(Modifier.padding(start = VdSize.ScreenMargin, end = 4.dp, top = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vd_nav_files), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
        }
        FilesTabs(readyChosen = false, onSwitch = actions::onReady)
        // they wrap onto a second line rather than run off the edge of a narrow phone
        FlowRow(
            Modifier.padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QueueTab.entries.forEach { GlassChip(stringResource(it.label), state.tab == it, onClick = { state.tab = it }) }
        }
        val entries = state.list?.takeIf { it.tab == state.tab }?.entries
        when {
            entries == null -> Unit
            entries.isEmpty() -> Empty(state.tab, actions)
            state.tab == QueueTab.Now -> NowList(state, entries, actions)
            else -> key(state.tab) { OtherList(state, entries, actions) }
        }
    }
}

/** "Ahora": what is downloading, paused or waiting its turn; the waiting ones can be dragged into another order. */
@Composable
private fun NowList(state: QueueUiState, entries: List<QueueEntry>, actions: QueueActions) {
    val listState = rememberLazyListState()
    val drag = remember(listState) { QueueDrag(listState) }
    val shown = remember(entries, drag.order) { drag.arrange(entries) }
    val reorderable = entries.count { it.kind == QueueKind.Waiting } > 1
    val cardShape = MaterialTheme.shapes.medium
    SideEffect {
        drag.shownIds = shown.map { it.id }
        drag.movable = entries.filter { it.kind == QueueKind.Waiting }.map { it.id }.toSet()
    }
    LaunchedEffect(entries) { drag.settle(entries) }
    // the order on screen waits for the database, but not forever
    LaunchedEffect(drag.dragged) {
        if (drag.dragged == null && drag.order != null) {
            delay(3000)
            drag.order = null
        }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.pauseAll != PauseAll.Hidden || entries.size > 1) {
            item(key = "actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    when (state.pauseAll) {
                        PauseAll.Pause -> BulkButton(R.drawable.ic_lyrics_pause, R.string.vd_queue_pause_all, actions::onPauseAll)
                        PauseAll.Resume -> BulkButton(R.drawable.ic_lyrics_play, R.string.vd_queue_resume_all, actions::onResumeAll)
                        PauseAll.Hidden -> Spacer(Modifier.width(1.dp))
                    }
                    if (entries.size > 1) BulkButton(R.drawable.baseline_close_24, R.string.vd_queue_cancel_all, actions::onCancelAll)
                }
            }
        }
        items(shown, key = { it.id }) { entry ->
            val handle = if (entry.kind == QueueKind.Waiting && reorderable) {
                Modifier.pointerInput(entry.id) {
                    detectDragGestures(
                        onDragStart = { drag.begin(entry.id) },
                        onDragEnd = { drag.end()?.let(actions::onReorder) },
                        onDragCancel = { drag.cancel() },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.move(amount.y)
                        },
                    )
                }
            } else {
                null
            }
            val placement = if (entry.id == drag.dragged) {
                Modifier.zIndex(1f).graphicsLayer {
                    translationY = drag.offsetOf(entry.id)
                    shadowElevation = 12.dp.toPx()
                    shape = cardShape
                }
            } else {
                Modifier.animateItem()
            }
            NowCard(entry, state.hideThumbnails, reorderable, handle, actions, placement)
        }
    }
}

/**
 * Dragging a waiting download to another place. The list shows the new order while the card moves and until the
 * database has it; the gesture reads the current cards through [shownIds] and [movable], set after each composition.
 */
@Stable
private class QueueDrag(private val listState: LazyListState) {
    var order by mutableStateOf<List<Long>?>(null)
    var dragged by mutableStateOf<Long?>(null)
    private var start by mutableFloatStateOf(0f)
    private var delta by mutableFloatStateOf(0f)
    private var before: List<Long> = emptyList()
    var shownIds: List<Long> = emptyList()
    var movable: Set<Long> = emptySet()

    /** [entries] in the dragged order; cards that appeared meanwhile go last. */
    fun arrange(entries: List<QueueEntry>): List<QueueEntry> {
        val ids = order ?: return entries
        val byId = entries.associateBy { it.id }
        val placed = ids.toSet()
        return ids.mapNotNull { byId[it] } + entries.filter { it.id !in placed }
    }

    fun begin(id: Long) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        before = shownIds.filter { it in movable }
        order = shownIds
        dragged = id
        start = item.offset.toFloat()
        delta = 0f
    }

    fun move(dy: Float) {
        val id = dragged ?: return
        val ids = order ?: return
        delta += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val item = items.firstOrNull { it.key == id } ?: return
        // the card takes the place of the waiting card its middle is over
        val middle = start + delta + item.size / 2f
        val target = items.firstOrNull { other ->
            val key = other.key as? Long
            key != null && key != id && key in movable && middle > other.offset && middle < other.offset + other.size
        } ?: return
        val from = ids.indexOf(id)
        val to = ids.indexOf(target.key as Long)
        if (from < 0 || to < 0) return
        // the list keeps its first visible card in view, so moving that card would scroll everything along with it
        val first = listState.firstVisibleItemIndex
        if (item.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        order = ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** How far the dragged card is drawn from the place the list gives it, so it stays under the finger. */
    fun offsetOf(id: Long): Float {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0f
        return start + delta - item.offset
    }

    /**
     * Every card in its new order, or null when the card went back where it was. Paused cards still hold a place in
     * the queue, so the whole order is saved, not only the waiting cards that moved.
     */
    fun end(): List<Long>? {
        if (dragged == null) return null
        dragged = null
        val ids = order
        if (ids == null || ids.filter { it in movable } == before) {
            order = null
            return null
        }
        return ids
    }

    fun cancel() {
        dragged = null
        order = null
    }

    /** Once the database lists the cards in the order on screen, the list follows the database again. */
    fun settle(entries: List<QueueEntry>) {
        if (order == null || dragged != null) return
        if (entries.map { it.id } == arrange(entries).map { it.id }) order = null
    }
}

@Composable
private fun NowCard(
    entry: QueueEntry,
    hideThumb: Boolean,
    withHandles: Boolean,
    handle: Modifier?,
    actions: QueueActions,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .padding(start = if (withHandles) 4.dp else 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withHandles) {
            // running and paused cards keep the room, so every thumbnail lines up
            Box(Modifier.size(width = 32.dp, height = 64.dp).then(handle ?: Modifier), contentAlignment = Alignment.Center) {
                if (handle != null) Icon(painterResource(R.drawable.vd_ic_drag), stringResource(R.string.vd_queue_move), tint = colors.onSurfaceVariant)
            }
        }
        Picture(entry, hideThumb, Modifier.size(width = 80.dp, height = 60.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(entry.line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // without a percentage yet (getting ready, joining audio and video) a segment runs along the bar
                GlassProgress(if (entry.kind == QueueKind.Running) entry.progress else 0f, Modifier.weight(1f))
                when (entry.kind) {
                    QueueKind.Running -> SquareButton(R.drawable.ic_lyrics_pause, R.string.vd_queue_pause) { actions.onPause(entry.id) }
                    QueueKind.Paused -> SquareButton(R.drawable.ic_lyrics_play, R.string.vd_queue_resume) { actions.onResume(entry.id) }
                    else -> Unit
                }
                SquareButton(R.drawable.baseline_close_24, R.string.vd_queue_cancel) { actions.onCancel(entry.id) }
            }
            entry.speed?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Programados, Con error, Cancelados and Para después. */
@Composable
private fun OtherList(state: QueueUiState, entries: List<QueueEntry>, actions: QueueActions) {
    LazyColumn(
        contentPadding = PaddingValues(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.tab != QueueTab.Scheduled) {
            item(key = "actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    when (state.tab) {
                        QueueTab.Errors -> BulkButton(R.drawable.ic_refresh, R.string.vd_queue_retry_all, actions::onRetryAll)
                        QueueTab.Saved -> BulkButton(R.drawable.baseline_download_24, R.string.vd_queue_download_all, actions::onDownloadAll)
                        else -> Spacer(Modifier.width(1.dp))
                    }
                    BulkButton(R.drawable.ic_baseline_delete_outline_24, R.string.vd_queue_delete_all, actions::onDeleteAll)
                }
            }
        }
        items(entries, key = { it.id }) { entry ->
            ListCard(entry, state.hideThumbnails, actions, Modifier.animateItem())
        }
    }
}

@Composable
private fun ListCard(entry: QueueEntry, hideThumb: Boolean, actions: QueueActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .padding(VdSize.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            when (entry.kind) {
                QueueKind.Failed -> Badge(R.drawable.vd_ic_warning, colors.error)
                QueueKind.Scheduled -> Badge(R.drawable.ic_clock, LocalGlass.current.accentText)
                else -> Unit
            }
            // a failed card shows why instead of a picture (diseño/imagenes/08-3-con-error.png)
            if (entry.kind != QueueKind.Failed) {
                Picture(entry, hideThumb, Modifier.size(width = 80.dp, height = 60.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(entry.line, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (entry.kind) {
                QueueKind.Scheduled -> {
                    CardButton(R.drawable.baseline_download_24, R.string.vd_queue_download_now, filled = true) { actions.onDownloadNow(entry.id) }
                    CardButton(R.drawable.baseline_close_24, R.string.vd_queue_cancel, filled = false) { actions.onCancel(entry.id) }
                }
                QueueKind.Failed -> {
                    CardButton(R.drawable.ic_refresh, R.string.vd_queue_retry, filled = true) { actions.onRetry(entry.id) }
                    CardButton(R.drawable.ic_info, R.string.vd_queue_detail, filled = false) { actions.onDetail(entry.id) }
                }
                QueueKind.Cancelled -> {
                    CardButton(R.drawable.ic_refresh, R.string.vd_queue_retry, filled = true) { actions.onRetry(entry.id) }
                    CardButton(R.drawable.ic_baseline_delete_outline_24, R.string.vd_files_delete, filled = false) { actions.onDelete(entry.id) }
                }
                QueueKind.Saved -> {
                    CardButton(R.drawable.baseline_download_24, R.string.vd_queue_download_now, filled = true) { actions.onDownloadNow(entry.id) }
                    CardButton(R.drawable.ic_baseline_delete_outline_24, R.string.vd_files_delete, filled = false) { actions.onDelete(entry.id) }
                }
                else -> Unit
            }
        }
    }
}

/** The thumbnail with the length in a corner. */
@Composable
private fun Picture(entry: QueueEntry, hideThumb: Boolean, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        Thumbnail(entry.thumb, hideThumb, Modifier.fillMaxSize())
        if (entry.duration.isNotBlank()) {
            Text(
                entry.duration,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** The soft square with an icon at the start of a failed or scheduled card. */
@Composable
private fun Badge(@DrawableRes icon: Int, tint: Color) {
    Box(
        Modifier.padding(end = 12.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Pause, resume and cancel on a running card. */
@Composable
private fun SquareButton(@DrawableRes icon: Int, @StringRes description: Int, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 8.dp).size(VdSize.SmallControl),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, LocalGlass.current.accentText),
    ) {
        Icon(painterResource(icon), stringResource(description), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

/** The two buttons under a card; smaller than BigButton so both fit side by side on a narrow phone. */
@Composable
private fun RowScope.CardButton(@DrawableRes icon: Int, @StringRes text: Int, filled: Boolean, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(VdSize.ButtonIcon))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val modifier = Modifier.weight(1f).height(VdSize.SmallControl)
    val padding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    if (filled) {
        Button(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small, contentPadding = padding, content = content)
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            contentPadding = padding,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            content = content,
        )
    }
}

/** "Pausar todo", "Reintentar todos", "Borrar todos"… above the cards. */
@Composable
private fun BulkButton(@DrawableRes icon: Int, @StringRes text: Int, onClick: () -> Unit) {
    val accent = LocalGlass.current.accentText
    TextButton(onClick = onClick) {
        Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(VdSize.ButtonIcon))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(text), color = accent, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Empty(tab: QueueTab, actions: QueueActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // "Ahora" gets the drawing; the other lists keep their small sign, which tells them apart
        if (tab == QueueTab.Now) {
            Illustration(R.drawable.vd_art_queue_empty)
        } else {
            Box(Modifier.size(72.dp).clip(CircleShape).background(colors.surfaceContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(tab.emptyIcon), contentDescription = null, tint = LocalGlass.current.accentText, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(tab.empty), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        if (tab == QueueTab.Now) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.vd_files_empty_summary), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            BigButton(R.drawable.vd_ic_nav_home, stringResource(R.string.vd_files_go_home), filled = true, onClick = actions::onGoHome)
        }
    }
}
