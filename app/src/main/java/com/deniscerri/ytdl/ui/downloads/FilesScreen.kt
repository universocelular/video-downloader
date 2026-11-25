package com.deniscerri.ytdl.ui.downloads

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateUtils
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.ui.home.BigButton
import com.deniscerri.ytdl.ui.tv.SendToTvSheet
import com.deniscerri.ytdl.ui.tv.WatchOnTvSheet
import com.deniscerri.ytdl.ui.tv.localSendFileOf
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.whatsapp.GlassChip
import com.deniscerri.ytdl.whatsapp.Illustration
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.StatusItem
import com.deniscerri.ytdl.whatsapp.StatusThumbnails
import com.deniscerri.ytdl.whatsapp.VdSize
import com.deniscerri.ytdl.whatsapp.WhatsAppSplitter
import com.deniscerri.ytdl.whatsapp.WhatsAppStatuses
import java.io.File

enum class FileKind { Video, Audio, Part, Status }

enum class FileFilter(@StringRes val label: Int, val kinds: Set<FileKind>) {
    All(R.string.vd_filter_all, FileKind.entries.toSet()),
    Videos(R.string.vd_filter_videos, setOf(FileKind.Video)),
    Audio(R.string.vd_filter_audio, setOf(FileKind.Audio)),
    Parts(R.string.vd_filter_parts, setOf(FileKind.Part)),
    Statuses(R.string.vd_filter_statuses, setOf(FileKind.Status)),
}

/**
 * Something the app left on the phone: a download from the history, a part made for statuses, or a saved status.
 * [local] is set for files read through the media store or a content link; [history] for downloads.
 */
@Immutable
data class FileEntry(
    val key: String,
    val kind: FileKind,
    val title: String,
    val extension: String,
    val sizeBytes: Long,
    val timeMs: Long,
    val thumbUrl: String,
    val local: StatusItem?,
    val history: HistoryItem?,
    /** The download is in the history but its file is no longer on the phone. */
    val missing: Boolean,
    /** Plain file path, for downloads and Android 9 files; media store files have none. */
    val path: String?,
) {
    val isVideo: Boolean get() = kind == FileKind.Part || kind == FileKind.Video || local?.isVideo == true
    val mimeType: String
        get() = local?.mimeType
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (kind == FileKind.Audio) "audio/*" else "video/*"
}

@Stable
class FilesUiState {
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
    var loaded by mutableStateOf(false)
    var hideThumbnails by mutableStateOf(false)
    /** Keys of the files chosen with a long press (diseño/imagenes/08-7-seleccion.png); empty outside that mode. */
    var selection by mutableStateOf<Set<String>>(emptySet())
}

interface FilesActions {
    fun onInProgress()
    fun onGoHome()
    /** Videos play inside the app; anything else opens in another app */
    fun onOpen(entry: FileEntry)
    /** The phone's own player or viewer, for those who prefer it */
    fun onOpenWith(entry: FileEntry)
    fun onShare(entry: FileEntry)
    fun onSplit(entry: FileEntry)
    fun onCopyLink(entry: FileEntry)
    fun onDownloadAgain(entry: FileEntry)
    fun onDelete(entry: FileEntry)
    fun onShareMany(entries: List<FileEntry>)
    fun onDeleteMany(entries: List<FileEntry>)
}

private val audioExtensions = setOf("mp3", "m4a", "opus", "ogg", "oga", "flac", "wav", "aac", "alac", "weba")

/** A history download; its first file that still exists stands for it. */
fun historyEntry(context: Context, item: HistoryItem): FileEntry {
    val file = item.downloadPath.map { File(it) }.firstOrNull { it.exists() }
    val shown = file ?: item.downloadPath.firstOrNull()?.let { File(it) }
    val extension = shown?.extension?.lowercase().orEmpty().ifBlank { item.format.container.lowercase() }
    val audio = extension in audioExtensions || (item.type.name == "audio" && extension !in setOf("mp4", "mkv", "webm", "mov"))
    val local = file?.let {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: if (audio) "audio/*" else "video/*"
        StatusItem(uri, it.name, mime, it.lastModified())
    }
    return FileEntry(
        key = "history-${item.id}",
        kind = if (audio) FileKind.Audio else FileKind.Video,
        title = item.title.ifBlank { shown?.nameWithoutExtension ?: item.url },
        extension = extension,
        sizeBytes = file?.length() ?: item.filesize,
        timeMs = item.time * 1000,
        thumbUrl = item.thumb,
        local = local,
        history = item,
        missing = file == null,
        path = file?.absolutePath,
    )
}

/** Parts from "Dividir para estados" and statuses saved by "Guardar estados"; only this app's own files. */
object DeviceFiles {
    fun load(context: Context): List<FileEntry> =
        query(context, "${Environment.DIRECTORY_MOVIES}/${WhatsAppSplitter.GALLERY_FOLDER}", FileKind.Part) +
            query(context, "${Environment.DIRECTORY_DCIM}/${WhatsAppStatuses.ALBUM}", FileKind.Status)

    private fun query(context: Context, folder: String, kind: FileKind): List<FileEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return legacy(context, folder, kind)
        val collections = if (kind == FileKind.Part) listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        else listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val columns = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
        )
        return collections.flatMap { collection ->
            runCatching {
                context.contentResolver.query(collection, columns, "${MediaStore.MediaColumns.RELATIVE_PATH} = ?", arrayOf("$folder/"), null)?.use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                            val name = c.getString(1) ?: continue
                            add(localEntry(uri, name, c.getString(2) ?: "", c.getLong(3), c.getLong(4) * 1000, kind, path = null))
                        }
                    }
                }
            }.getOrNull().orEmpty()
        }
    }

    /** Android 9 and older keep plain files in the public folders. */
    private fun legacy(context: Context, folder: String, kind: FileKind): List<FileEntry> =
        File(Environment.getExternalStorageDirectory(), folder).listFiles().orEmpty().filter { it.isFile }.map { file ->
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "video/*"
            localEntry(uri, file.name, mime, file.length(), file.lastModified(), kind, file.absolutePath)
        }

    private fun localEntry(uri: Uri, name: String, mime: String, size: Long, modifiedMs: Long, kind: FileKind, path: String?) = FileEntry(
        key = uri.toString(),
        kind = kind,
        title = name.substringBeforeLast('.'),
        extension = name.substringAfterLast('.', "").lowercase(),
        sizeBytes = size,
        timeMs = modifiedMs,
        thumbUrl = "",
        local = StatusItem(uri, name, mime, modifiedMs),
        history = null,
        missing = false,
        path = path,
    )
}

/** "Archivos › Listos" (diseño/imagenes/08-1-listos.png, 08-5-detalle.png and 08-6-vacio.png). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(state: FilesUiState, actions: FilesActions) {
    val colors = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableStateOf(FileFilter.All) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<FileEntry?>(null) }
    var watchOnTv by remember { mutableStateOf<FileEntry?>(null) }
    var sendToTv by remember { mutableStateOf<FileEntry?>(null) }
    val thumbnails = rememberThumbnails()
    val visible = remember(state.entries, filter, query) {
        state.entries.filter { it.kind in filter.kinds && (query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)) }
    }
    val selecting = state.selection.isNotEmpty()
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        if (selecting) {
            SelectionBar(state, visible, actions)
        } else {
            Row(Modifier.padding(start = VdSize.ScreenMargin, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.vd_nav_files), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    searching = !searching
                    if (!searching) query = ""
                }) {
                    Icon(painterResource(if (searching) R.drawable.baseline_close_24 else R.drawable.ic_search), stringResource(R.string.vd_files_search), tint = colors.onSurface)
                }
            }
        }
        if (searching) {
            SearchBox(query) { query = it }
        }
        FilesTabs(readyChosen = true, onSwitch = actions::onInProgress)
        // they wrap onto a second line rather than run off the edge of a narrow phone
        FlowRow(
            Modifier.padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FileFilter.entries.forEach { GlassChip(stringResource(it.label), filter == it, onClick = { filter = it }) }
        }
        when {
            state.loaded && state.entries.isEmpty() -> Empty(actions)
            state.loaded && visible.isEmpty() -> Text(
                stringResource(R.string.vd_files_none_of_kind),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.key }) { entry ->
                    val chosen = entry.key in state.selection
                    FileRow(
                        entry, state.hideThumbnails, thumbnails, chosen, selecting,
                        onClick = {
                            if (selecting) state.selection = if (chosen) state.selection - entry.key else state.selection + entry.key
                            else detail = entry
                        },
                        onLongClick = { state.selection = state.selection + entry.key },
                    )
                }
            }
        }
    }
    detail?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainer,
        ) {
            Detail(entry, state.hideThumbnails, thumbnails, actions, onWatchOnTv = { watchOnTv = it }, onSendToTv = { sendToTv = it }) { detail = null }
        }
    }
    // the steps to see it on the TV, then the video itself
    watchOnTv?.let { entry ->
        WatchOnTvSheet(
            onDismiss = { watchOnTv = null },
            onPlay = {
                watchOnTv = null
                actions.onOpen(entry)
            },
        )
    }
    // a copy that stays on the TV, through LocalSend
    sendToTv?.let { entry ->
        val context = LocalContext.current
        SendToTvSheet(
            title = entry.title,
            files = {
                listOfNotNull(entry.local?.let { localSendFileOf(context, it.uri, it.name, entry.sizeBytes, entry.mimeType) })
            },
            onDismiss = { sendToTv = null },
        )
    }
}

/** Replaces the title while files are chosen: how many, "Elegir todos", and what to do with them. */
@Composable
private fun SelectionBar(state: FilesUiState, visible: List<FileEntry>, actions: FilesActions) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val chosen = state.entries.filter { it.key in state.selection }
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.selection = emptySet() }) {
                Icon(painterResource(R.drawable.baseline_close_24), stringResource(R.string.vd_files_clear_selection), tint = colors.onSurface)
            }
            Text(
                resources.getQuantityString(R.plurals.vd_files_selected, chosen.size, chosen.size),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { state.selection = state.selection + visible.map { it.key } }) {
                Text(stringResource(R.string.vd_files_select_all), color = LocalGlass.current.accentText, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(R.drawable.baseline_share_24, stringResource(R.string.vd_files_share), filled = false, modifier = Modifier.weight(1f)) {
                actions.onShareMany(chosen)
            }
            Button(
                onClick = { actions.onDeleteMany(chosen) },
                modifier = Modifier.weight(1f).heightIn(min = VdSize.Control),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError),
            ) {
                Icon(painterResource(R.drawable.baseline_delete_24), contentDescription = null, modifier = Modifier.size(VdSize.ButtonIcon))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vd_files_delete), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun rememberThumbnails(): StatusThumbnails {
    val context = LocalContext.current
    return remember(context) { StatusThumbnails(context.applicationContext) }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = VdSize.Control)
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) Text(stringResource(R.string.vd_files_search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "En curso | Listos" at the top of Archivos; [onSwitch] opens the other one. */
@Composable
internal fun FilesTabs(readyChosen: Boolean, onSwitch: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 12.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small)
            .padding(3.dp),
    ) {
        listOf(R.string.vd_files_in_progress to !readyChosen, R.string.vd_files_ready to readyChosen).forEach { (label, chosen) ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = VdSize.SmallControl - 2.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (chosen) colors.secondaryContainer else Color.Transparent)
                    .clickable(enabled = !chosen, onClick = onSwitch),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (chosen) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Empty(actions: FilesActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Illustration(R.drawable.vd_art_files_empty)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.vd_files_empty_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.vd_files_empty_summary), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        BigButton(R.drawable.vd_ic_nav_home, stringResource(R.string.vd_files_go_home), filled = true, onClick = actions::onGoHome)
    }
}

@Composable
private fun detailLine(entry: FileEntry): String {
    val kind = when (entry.kind) {
        FileKind.Part -> stringResource(R.string.vd_kind_part)
        FileKind.Status -> stringResource(R.string.vd_kind_status)
        else -> null
    }
    if (entry.missing) return listOfNotNull(kind, stringResource(R.string.vd_files_missing)).joinToString(" · ")
    val day = DateUtils.getRelativeTimeSpanString(entry.timeMs, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString().lowercase()
    return listOfNotNull(
        kind,
        entry.extension.uppercase().ifBlank { null },
        entry.sizeBytes.takeIf { it > 0 }?.let { FileUtil.convertFileSize(it) },
        day,
    ).joinToString(" · ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: FileEntry,
    hideThumb: Boolean,
    thumbnails: StatusThumbnails,
    chosen: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (chosen) colors.primaryContainer else colors.surfaceContainer)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
            .alpha(if (entry.missing && !chosen) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (chosen) colors.primary else Color.Transparent)
                    .border(2.dp, if (chosen) colors.primary else colors.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        FilePicture(entry, hideThumb, thumbnails, 56.dp, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(detailLine(entry), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!selecting) Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** The file's own frame when it is on the phone, otherwise the site's thumbnail. */
@Composable
private fun FilePicture(entry: FileEntry, hideThumb: Boolean, thumbnails: StatusThumbnails, size: Dp, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val local = entry.local?.takeIf { !hideThumb && (it.isVideo || it.mimeType.startsWith("image/")) }
    val picture: ImageBitmap? = if (local == null) null else {
        val loaded by produceState<ImageBitmap?>(thumbnails.cached(local, sizePx)?.picture?.asImageBitmap(), local.uri, sizePx) {
            if (value == null) value = thumbnails.thumbnail(local, sizePx)?.picture?.asImageBitmap()
        }
        loaded
    }
    Box(modifier.background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        when {
            picture != null -> Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            entry.thumbUrl.isNotBlank() && !hideThumb -> key(entry.thumbUrl) {
                val placeholder = colors.surfaceContainerHighest.toArgb()
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setBackgroundColor(placeholder)
                            loadThumbnail(false, entry.thumbUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> Icon(
                painterResource(if (entry.kind == FileKind.Audio) R.drawable.ic_music else R.drawable.ic_video),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Detail(
    entry: FileEntry,
    hideThumb: Boolean,
    thumbnails: StatusThumbnails,
    actions: FilesActions,
    onWatchOnTv: (FileEntry) -> Unit,
    onSendToTv: (FileEntry) -> Unit,
    close: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    fun run(action: (FileEntry) -> Unit) {
        close()
        action(entry)
    }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = !entry.missing) { run(actions::onOpen) },
            contentAlignment = Alignment.Center,
        ) {
            FilePicture(entry, hideThumb, thumbnails, 360.dp, Modifier.fillMaxSize())
            if (!entry.missing && entry.isVideo) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_vd_play), stringResource(R.string.vd_files_open), tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Column {
            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detailLine(entry), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (!entry.missing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(R.drawable.ic_vd_play, R.string.vd_files_open, Modifier.weight(1f)) { run(actions::onOpen) }
                ActionTile(R.drawable.baseline_share_24, R.string.vd_files_share, Modifier.weight(1f)) { run(actions::onShare) }
                if (entry.isVideo) ActionTile(R.drawable.ic_cut, R.string.vd_files_split, Modifier.weight(1f)) { run(actions::onSplit) }
            }
        }
        // the other actions as one grouped list, like the system's own sheets
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(colors.surfaceContainerHigh)) {
            if (!entry.missing && entry.isVideo) {
                SheetRow(R.drawable.vd_ic_tv, stringResource(R.string.vd_tool_tv_title), colors.onSurface) { run(onWatchOnTv) }
            }
            if (!entry.missing && entry.local != null) {
                SheetRow(R.drawable.vd_ic_send_tv, stringResource(R.string.vd_tv_send), colors.onSurface) { run(onSendToTv) }
            }
            if (!entry.missing && entry.isVideo) {
                SheetRow(R.drawable.ic_baseline_file_open_24, stringResource(R.string.vd_files_open_with), colors.onSurface) { run(actions::onOpenWith) }
            }
            if (entry.history != null) {
                SheetRow(R.drawable.ic_link, stringResource(R.string.vd_files_copy_link), colors.onSurface) { run(actions::onCopyLink) }
                SheetRow(R.drawable.baseline_download_24, stringResource(R.string.vd_files_redownload), colors.onSurface) { run(actions::onDownloadAgain) }
            }
            SheetRow(R.drawable.baseline_delete_24, stringResource(R.string.vd_files_delete), colors.error) { run(actions::onDelete) }
        }
    }
}

@Composable
private fun ActionTile(@DrawableRes icon: Int, @StringRes label: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalGlass.current.accentText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
    }
}

@Composable
private fun SheetRow(@DrawableRes icon: Int, text: String, tint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
