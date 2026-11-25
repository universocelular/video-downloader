package com.deniscerri.ytdl.whatsapp

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.R

/** Everything the statuses screen can ask for; the activity wires each one to the view model or to Android */
class StatusesActions(
    val onApp: (WhatsAppApp) -> Unit,
    val onFilter: (StatusFilter) -> Unit,
    val onGrantAccess: () -> Unit,
    val onView: (StatusItem?) -> Unit,
    val onSave: (StatusItem) -> Unit,
    /** Choosing several statuses: "Elegir" or a long press starts it, a tap chooses or leaves one, then what to do with them */
    val onStartChoosing: () -> Unit,
    val onToggle: (StatusItem) -> Unit,
    val onChooseAll: (List<StatusItem>) -> Unit,
    val onStopChoosing: () -> Unit,
    val onSaveAll: (List<StatusItem>) -> Unit,
    /** Once the user confirmed it */
    val onDelete: (List<StatusItem>) -> Unit,
    /** "Abrir WhatsApp" when there are no statuses yet */
    val onOpenApp: (WhatsAppApp) -> Unit,
    val onClose: () -> Unit,
)

private val TILE_MIN_SIZE = 96.dp
// Big enough for the widest tile the grid makes
private val THUMBNAIL_SIZE = 160.dp
private const val FULL_PICTURE_PX = 2048

@Composable
fun StatusesScreen(state: StatusesUiState, thumbnails: StatusThumbnails, actions: StatusesActions) {
    val shown = state.shown
    val thumbnailPx = with(LocalDensity.current) { THUMBNAIL_SIZE.roundToPx() }
    // Kept here so the grid comes back at the same place after the full-screen view
    val gridState = rememberLazyGridState()
    val viewing = state.viewing?.let { name -> shown.indexOfFirst { it.name == name } }?.takeIf { it >= 0 }
    // The statuses waiting for the user to confirm deleting them
    var confirming by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    GlassBackground {
        if (viewing != null) {
            // A list that changed (a status expired) starts the pager again on the same status
            key(shown) { StatusViewer(shown, viewing, state, thumbnails, thumbnailPx, actions) { confirming = it } }
        } else {
            StatusGrid(state, shown, gridState, thumbnails, thumbnailPx, actions) { confirming = it }
        }
    }
    if (confirming.isNotEmpty()) {
        val resources = LocalContext.current.resources
        DeleteDialog(
            title = resources.getQuantityString(R.plurals.vd_statuses_delete_confirm, confirming.size, confirming.size),
            text = resources.getQuantityString(R.plurals.vd_statuses_delete_note, confirming.size),
            note = if (state.canDelete) null else stringResource(R.string.vd_statuses_delete_access),
            onConfirm = {
                actions.onDelete(confirming)
                confirming = emptyList()
            },
            onDismiss = { confirming = emptyList() },
        )
    }
}

@Composable
private fun StatusGrid(
    state: StatusesUiState,
    shown: List<StatusItem>,
    gridState: LazyGridState,
    thumbnails: StatusThumbnails,
    thumbnailPx: Int,
    actions: StatusesActions,
    onAskDelete: (List<StatusItem>) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(TILE_MIN_SIZE),
        state = gridState,
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = VdSize.ScreenMargin, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.choosing) {
            wide("choosing") { ChoosingBar(state, shown, actions, onAskDelete) }
        } else {
            wide("header") {
                GlassHeader(stringResource(R.string.vd_statuses_title), actions.onClose) {
                    if (state.hasAccess && state.items.isNotEmpty()) {
                        TextButton(onClick = actions.onStartChoosing) {
                            Text(stringResource(R.string.vd_statuses_choose), color = LocalGlass.current.accentText, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        if (state.apps.size > 1 && !state.choosing) wide("apps") { AppChips(state, actions.onApp) }
        val app = state.app
        when {
            !state.checked || (state.loading && state.items.isEmpty()) -> wide("loading") {
                GlassPanel {
                    GlassTitle(stringResource(R.string.vd_statuses_loading))
                    GlassProgress(null)
                }
            }
            app == null -> wide("no_whatsapp") {
                GlassPanel { GlassBody(stringResource(R.string.vd_statuses_no_whatsapp)) }
            }
            !state.hasAccess -> wide("access") { AccessPanel(app, actions.onGrantAccess) }
            state.items.isEmpty() -> wide("empty") { EmptyPanel(app) { actions.onOpenApp(app) } }
            else -> {
                wide("filter") { FilterChips(state.filter, actions.onFilter) }
                if (shown.isEmpty()) wide("none_of_kind") { GlassBody(stringResource(R.string.vd_statuses_none_of_kind), soft = true) }
                items(shown, key = { "status:" + it.name }) { item ->
                    StatusTile(
                        item, saved = item.name in state.saved, saving = item.name in state.saving,
                        choosing = state.choosing, chosen = item.name in state.chosen, thumbnails, thumbnailPx,
                        onOpen = { if (state.choosing) actions.onToggle(item) else actions.onView(item) },
                        onLongPress = { actions.onToggle(item) },
                        onSave = { actions.onSave(item) },
                    )
                }
                wide("where") { GlassBody(stringResource(R.string.vd_statuses_saved_where, WhatsAppStatuses.ALBUM), soft = true) }
            }
        }
    }
}

/** Replaces the title while choosing: how many, "Elegir todos", and what to do with them (diseño/imagenes/08-7-seleccion.png) */
@Composable
private fun ChoosingBar(state: StatusesUiState, shown: List<StatusItem>, actions: StatusesActions, onAskDelete: (List<StatusItem>) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val chosen = state.items.filter { it.name in state.chosen }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onStopChoosing) {
                Icon(painterResource(R.drawable.baseline_close_24), stringResource(R.string.vd_files_clear_selection), tint = colors.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (chosen.isEmpty()) stringResource(R.string.vd_statuses_choose_title)
                else resources.getQuantityString(R.plurals.vd_files_selected, chosen.size, chosen.size),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { actions.onChooseAll(shown) }) {
                Text(stringResource(R.string.vd_files_select_all), color = LocalGlass.current.accentText, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                stringResource(R.string.vd_statuses_save),
                onClick = { actions.onSaveAll(chosen) },
                modifier = Modifier.weight(1f),
                enabled = chosen.any { it.name !in state.saved },
            )
            GlassDeleteButton(onClick = { onAskDelete(chosen) }, modifier = Modifier.weight(1f), enabled = chosen.isNotEmpty())
        }
    }
}

/** A row of the grid as wide as the screen */
private fun LazyGridScope.wide(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** WhatsApp and WhatsApp Business side by side, each with the status ring (diseño/imagenes/07-2-grilla.png) */
@Composable
private fun AppChips(state: StatusesUiState, onApp: (WhatsAppApp) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.apps.forEach { app -> Pill(app.shortLabel, app == state.app, R.drawable.vd_ic_status, Modifier.weight(1f)) { onApp(app) } }
    }
}

@Composable
private fun FilterChips(filter: StatusFilter, onFilter: (StatusFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(stringResource(R.string.vd_statuses_all), filter == StatusFilter.ALL, null, Modifier.weight(1f)) { onFilter(StatusFilter.ALL) }
        Pill(stringResource(R.string.vd_statuses_photos), filter == StatusFilter.PHOTOS, null, Modifier.weight(1f)) { onFilter(StatusFilter.PHOTOS) }
        Pill(stringResource(R.string.vd_statuses_videos), filter == StatusFilter.VIDEOS, null, Modifier.weight(1f)) { onFilter(StatusFilter.VIDEOS) }
    }
}

/** A rounded choice that fills its share of the row, solid when chosen */
@Composable
private fun Pill(text: String, selected: Boolean, @DrawableRes icon: Int?, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .heightIn(min = VdSize.SmallControl)
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainer)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = if (selected) colors.onPrimary else colors.onSurface
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(VdSize.ButtonIcon))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A drawing, then a title, a text and what to do (diseño/imagenes/07-1-permiso.png, 07-4-vacio.png) */
@Composable
private fun Explainer(@DrawableRes art: Int, title: String, text: String, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Illustration(art)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun AccessPanel(app: WhatsAppApp, onGrantAccess: () -> Unit) {
    Explainer(R.drawable.vd_art_statuses_access, stringResource(R.string.vd_statuses_access_title), stringResource(R.string.vd_statuses_access, app.label)) {
        GlassPrimaryButton(stringResource(R.string.vd_statuses_access_button), onGrantAccess)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.vd_statuses_access_hint, app.label, app.packageName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyPanel(app: WhatsAppApp, onOpenApp: () -> Unit) {
    Explainer(R.drawable.vd_art_statuses_empty, stringResource(R.string.vd_statuses_empty_title), stringResource(R.string.vd_statuses_empty, app.label)) {
        GlassPrimaryButton(stringResource(R.string.vd_statuses_open_app, app.label), onOpenApp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusTile(
    item: StatusItem,
    saved: Boolean,
    saving: Boolean,
    choosing: Boolean,
    chosen: Boolean,
    thumbnails: StatusThumbnails,
    thumbnailPx: Int,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onSave: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    val thumbnail by produceState(thumbnails.cached(item, thumbnailPx), item, thumbnailPx) {
        if (value == null) value = thumbnails.thumbnail(item, thumbnailPx)
    }
    val picture = remember(thumbnail) { thumbnail?.picture?.asImageBitmap() }
    val length = thumbnail?.durationMs?.let { SplitClock.format(it * 1000) }
    val description = when {
        !item.isVideo -> stringResource(R.string.vd_statuses_photo)
        length != null -> stringResource(R.string.vd_statuses_video_length, length)
        else -> stringResource(R.string.vd_statuses_video)
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(role = Role.Image, onClick = onOpen, onLongClick = onLongPress)
            .semantics {
                contentDescription = description
                if (choosing) selected = chosen
            },
    ) {
        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isVideo) PlayBadge(length, Modifier.align(Alignment.BottomStart).padding(6.dp))
        if (choosing) ChoiceMark(chosen, Modifier.align(Alignment.TopEnd).padding(8.dp))
        else SaveButton(saved, saving, onSave, Modifier.align(Alignment.TopEnd))
    }
}

/** The circle in a tile's corner while choosing, filled with a check on the chosen ones */
@Composable
private fun ChoiceMark(chosen: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (chosen) colors.primary else Color.Black.copy(alpha = 0.35f))
            .border(2.dp, if (chosen) colors.primary else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** A round button over the corner of a tile; the touch area is larger than the circle */
@Composable
private fun SaveButton(saved: Boolean, saving: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(if (saved) R.string.vd_statuses_saved else R.string.vd_statuses_save)
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = !saved && !saving, role = Role.Button, onClick = onSave),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (saved) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(if (saved) R.drawable.ic_check else R.drawable.baseline_download_24),
                    contentDescription = label,
                    tint = if (saved) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** One status at a time over a dark background; swiping goes to the next or previous one */
@Composable
private fun StatusViewer(
    items: List<StatusItem>,
    start: Int,
    state: StatusesUiState,
    thumbnails: StatusThumbnails,
    thumbnailPx: Int,
    actions: StatusesActions,
    onAskDelete: (List<StatusItem>) -> Unit,
) {
    val pager = rememberPagerState(initialPage = start) { items.size }
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage }.collect { page -> actions.onView(items[page]) }
    }
    val current = items[pager.currentPage.coerceIn(items.indices)]
    val saved = current.name in state.saved
    Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.vd_statuses_position, pager.currentPage + 1, items.size),
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            TextButton(onClick = { actions.onView(null) }) { Text(stringResource(R.string.vd_close), color = Color.White) }
        }
        PositionLine(pager.currentPage, items.size)
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { items[it].name }) { page ->
            val item = items[page]
            // Only the status that is still on screen plays, so a swipe never leaves two videos running
            if (item.isVideo && page == pager.settledPage) StatusVideo(item.uri) else StatusPicture(item, thumbnails, thumbnailPx)
        }
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassDeleteButton(onClick = { onAskDelete(listOf(current)) }, modifier = Modifier.weight(1f), outlined = true)
            GlassPrimaryButton(
                stringResource(if (saved) R.string.vd_statuses_saved else R.string.vd_statuses_save),
                onClick = { actions.onSave(current) },
                enabled = !saved && current.name !in state.saving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A thin line with the current status lit along it: there are more to either side (diseño/imagenes/07-3-vista-completa.png) */
@Composable
private fun PositionLine(page: Int, count: Int) {
    if (count < 2) return
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp).height(3.dp)) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.25f), cornerRadius = radius)
        val piece = size.width / count
        drawRoundRect(Color.White, topLeft = Offset(piece * page, 0f), size = Size(piece.coerceAtLeast(size.height * 2), size.height), cornerRadius = radius)
    }
}

/** A photo at screen size; a video that is not playing shows its preview */
@Composable
private fun StatusPicture(item: StatusItem, thumbnails: StatusThumbnails, thumbnailPx: Int) {
    val picture by produceState(thumbnails.cached(item, thumbnailPx)?.picture, item) {
        val decoded = if (item.isVideo) thumbnails.thumbnail(item, thumbnailPx)?.picture else thumbnails.picture(item, FULL_PICTURE_PX)
        if (decoded != null) value = decoded
    }
    val image = remember(picture) { picture?.asImageBitmap() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(
                image,
                contentDescription = stringResource(if (item.isVideo) R.string.vd_statuses_video else R.string.vd_statuses_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Plays a video status in a loop; a tap pauses or resumes it */
@Composable
private fun StatusVideo(uri: Uri) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    var playing by remember(player) { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycle) {
        // The sound stops when the user leaves the app
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val playLabel = stringResource(R.string.vd_statuses_play)
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        // Taps land on this layer instead of the video view, so swiping to the next status still works over a video
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = playLabel) {
                    player.playWhenReady = !player.playWhenReady
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!playing) {
                Icon(
                    painterResource(R.drawable.ic_vd_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).padding(14.dp),
                )
            }
        }
    }
}
