package com.deniscerri.ytdl.ui.home

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.database.repository.DownloadRepository
import com.deniscerri.ytdl.database.viewmodel.DownloadCardViewModel
import com.deniscerri.ytdl.database.viewmodel.DownloadViewModel
import com.deniscerri.ytdl.database.enums.DownloadType
import com.deniscerri.ytdl.ui.downloadcard.CutVideoBottomSheetDialog
import com.deniscerri.ytdl.ui.downloadcard.VideoCutListener
import com.deniscerri.ytdl.util.Extensions.loadThumbnail
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.FormatUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.whatsapp.GlassChip
import com.deniscerri.ytdl.whatsapp.GlassTheme
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A quality the user can pick; [formatId] is one the download engine understands without a format list. */
@Immutable
data class QualityOption(@StringRes val label: Int, val formatId: String, val detail: String)

@Stable
class DownloadChoices(val qualities: List<QualityOption>, audio: Boolean) {
    var audio by mutableStateOf(audio)
    var quality by mutableIntStateOf(0)
    var audioFormat by mutableStateOf("mp3")
    var highBitrate by mutableStateOf(true)
    var cover by mutableStateOf(true)
    var lyrics by mutableStateOf(false)
    /** Sections in the engine's "start-end;" form; empty for the whole video. */
    var cut by mutableStateOf("")
    var scheduledAt by mutableStateOf<Long?>(null)
}

/** Where [SimpleDownloadSheet] was opened from: Inicio, or the sheet for a link shared from another app. */
interface SimpleDownloadHost {
    /** "Más opciones": the full sheet with every setting */
    fun openFullDownloadSheet(result: ResultItem, type: DownloadType)
    fun onDownloadQueued()
}

/**
 * "Descargar" with only the common choices: video or audio, three qualities, and the big button
 * (diseño/imagenes/03-2-elegir-video.png and 03-3-elegir-audio.png). "Más opciones" opens the full sheet.
 * Reads the analyzed video from [DownloadCardViewModel], like the full sheet.
 */
class SimpleDownloadSheet : BottomSheetDialogFragment() {
    private val host: SimpleDownloadHost? get() = parentFragment as? SimpleDownloadHost ?: activity as? SimpleDownloadHost
    private lateinit var downloadViewModel: DownloadViewModel
    private var result: ResultItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        downloadViewModel = ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        result = ViewModelProvider(requireActivity())[DownloadCardViewModel::class.java].resultItem
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val item = result
        if (item == null) {
            // recreated after the app was closed: the analyzed video is gone
            dismissAllowingStateLoss()
            return View(requireContext())
        }
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val choices = DownloadChoices(
            qualityOptions(item),
            audio = downloadViewModel.getDownloadType(url = item.url) == DownloadType.audio ||
                preferences.getString("preferred_download_type", "video") == "audio",
        )
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GlassTheme {
                    DownloadOptions(
                        result = item,
                        choices = choices,
                        hideThumb = preferences.getStringSet("hide_thumbnails", emptySet())!!.contains("home"),
                        onClose = { dismiss() },
                        onCut = { cut(item, choices) },
                        onSchedule = { schedule(choices) },
                        onMore = { more(item, choices) },
                        onDownload = { download(item, choices) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    // Generic heights the engine turns into "-S res:N"; a site's odd heights round down to one of these.
    private val genericHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240)

    private fun qualityOptions(result: ResultItem): List<QualityOption> {
        val isAudioOnly = { codec: String -> codec.isBlank() || codec == "none" }
        val video = result.formats.filter { (it.height ?: 0) > 0 && !isAudioOnly(it.vcodec) }
        val bestAudio = result.formats.filter { isAudioOnly(it.vcodec) }.maxOfOrNull { it.filesize } ?: 0L
        val heights = video.mapNotNull { it.height }.distinct().sortedDescending()
        if (heights.isEmpty()) {
            return listOf(
                QualityOption(R.string.vd_quality_best, "best", getString(R.string.vd_quality_highest)),
                QualityOption(R.string.vd_quality_medium, "720p_ytdlnisgeneric", "720p"),
                QualityOption(R.string.vd_quality_light, "480p_ytdlnisgeneric", "480p"),
            )
        }
        fun detail(height: Int): String {
            val size = video.filter { it.height == height }
                .maxOfOrNull { it.filesize + if (isAudioOnly(it.acodec)) bestAudio else 0L } ?: 0L
            return listOfNotNull("${height}p", size.takeIf { it > 0 }?.let { FileUtil.convertFileSize(it) }).joinToString(" · ")
        }
        fun generic(height: Int) = genericHeights.firstOrNull { it <= height } ?: genericHeights.last()
        val best = heights.first()
        val medium = heights.firstOrNull { generic(it) < generic(best) && it <= 720 } ?: heights.firstOrNull { generic(it) < generic(best) }
        val light = medium?.let { m -> heights.firstOrNull { generic(it) < generic(m) && it <= 480 } ?: heights.firstOrNull { generic(it) < generic(m) } }
        return listOfNotNull(
            QualityOption(R.string.vd_quality_best, "best", detail(best)),
            medium?.let { QualityOption(R.string.vd_quality_medium, "${generic(it)}p_ytdlnisgeneric", detail(it)) },
            light?.let { QualityOption(R.string.vd_quality_light, "${generic(it)}p_ytdlnisgeneric", detail(it)) },
        )
    }

    private fun cut(result: ResultItem, choices: DownloadChoices) {
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                downloadViewModel.createDownloadItemFromResult(result, result.url, if (choices.audio) DownloadType.audio else DownloadType.video)
            }
            item.downloadSections = choices.cut
            CutVideoBottomSheetDialog(item, result.urls, result.chapters, object : VideoCutListener {
                override fun onChangeCut(list: List<String>) {
                    choices.cut = list.joinToString("") { "$it;" }
                }

                override fun onMergeCuts(merge: Boolean) {}
            }).show(parentFragmentManager, "cutVideoSheet")
        }
    }

    private fun schedule(choices: DownloadChoices) {
        UiUtil.showDatePicker(parentFragmentManager, PreferenceManager.getDefaultSharedPreferences(requireContext())) {
            choices.scheduledAt = it.timeInMillis
        }
    }

    private fun more(result: ResultItem, choices: DownloadChoices) {
        val host = host
        dismiss()
        host?.openFullDownloadSheet(result, if (choices.audio) DownloadType.audio else DownloadType.video)
    }

    private fun download(result: ResultItem, choices: DownloadChoices) {
        val context = requireContext().applicationContext
        val host = host
        lifecycleScope.launch {
            val type = if (choices.audio) DownloadType.audio else DownloadType.video
            val item = withContext(Dispatchers.IO) { downloadViewModel.createDownloadItemFromResult(result, result.url, type) }
            if (choices.audio) {
                item.container = choices.audioFormat
                item.audioPreferences.bitrate = if (choices.highBitrate) "320K" else "128K"
                item.audioPreferences.embedThumb = choices.cover
                item.audioPreferences.embedLyrics = choices.lyrics
            } else {
                val option = choices.qualities[choices.quality]
                // "best" keeps the format picked from the user's preferences
                if (option.formatId != "best") {
                    FormatUtil(context).getGenericVideoFormats(context.resources)
                        .firstOrNull { it.format_id == option.formatId }
                        ?.let { item.format = it }
                }
            }
            if (choices.cut.isNotBlank()) item.downloadSections = choices.cut
            choices.scheduledAt?.let {
                item.status = DownloadRepository.Status.Scheduled.toString()
                item.downloadStartTime = it
            }
            val queued = withContext(Dispatchers.IO) { downloadViewModel.queueDownloads(listOf(item)) }
            Toast.makeText(context, queued.message.ifBlank { context.getString(R.string.vd_sheet_added) }, Toast.LENGTH_SHORT).show()
            host?.onDownloadQueued()
            dismiss()
        }
    }
}

@Composable
private fun DownloadOptions(
    result: ResultItem,
    choices: DownloadChoices,
    hideThumb: Boolean,
    onClose: () -> Unit,
    onCut: () -> Unit,
    onSchedule: () -> Unit,
    onMore: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val duration = result.duration.takeUnless { it.isBlank() || it == "-1" || it == "0:00" }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vd_tool_download_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.baseline_close_24), stringResource(R.string.cancel), tint = colors.onSurfaceVariant)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(colors.surfaceContainerHigh).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            key(result.thumb, hideThumb) {
                val placeholder = colors.surfaceContainerHighest.toArgb()
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setBackgroundColor(placeholder)
                            if (result.thumb.isNotBlank()) loadThumbnail(hideThumb, result.thumb)
                        }
                    },
                    modifier = Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.title.ifBlank { result.url }, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(hostOf(result.url), duration).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Segments(choices)
        if (choices.audio) AudioChoices(choices) else VideoChoices(choices)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (duration != null) {
                OptionRow(R.drawable.ic_cut, stringResource(R.string.vd_sheet_cut), choices.cut.trimEnd(';').replace(";", " · ").ifBlank { null }, onCut)
            }
            OptionRow(
                R.drawable.baseline_calendar_month_24,
                stringResource(R.string.vd_sheet_schedule),
                choices.scheduledAt?.let {
                    stringResource(
                        R.string.vd_sheet_scheduled_for,
                        DateUtils.getRelativeDateTimeString(androidx.compose.ui.platform.LocalContext.current, it, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString(),
                    )
                },
                onSchedule,
            )
            OptionRow(R.drawable.vd_ic_sliders, stringResource(R.string.vd_sheet_more), null, onMore)
        }
        BigButton(
            R.drawable.baseline_download_24,
            when {
                choices.scheduledAt != null -> stringResource(R.string.vd_sheet_schedule)
                choices.audio -> stringResource(R.string.vd_sheet_download_audio)
                else -> stringResource(R.string.vd_tool_download_title)
            },
            filled = true,
            onClick = onDownload,
        )
    }
}

/** "Video | Audio" */
@Composable
private fun Segments(choices: DownloadChoices) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium)
            .padding(4.dp),
    ) {
        Segment(R.drawable.ic_vd_play, R.string.vd_sheet_video, selected = !choices.audio, Modifier.weight(1f)) { choices.audio = false }
        Segment(R.drawable.ic_music, R.string.vd_sheet_audio, selected = choices.audio, Modifier.weight(1f)) { choices.audio = true }
    }
}

@Composable
private fun Segment(@DrawableRes icon: Int, @StringRes label: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier
            .heightIn(min = VdSize.SmallControl + 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) colors.primary else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(VdSize.ButtonIcon))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun VideoChoices(choices: DownloadChoices) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.vd_sheet_quality), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            choices.qualities.forEachIndexed { index, option ->
                ChoiceCard(stringResource(option.label), option.detail, choices.quality == index, Modifier.weight(1f)) { choices.quality = index }
            }
        }
    }
}

@Composable
private fun AudioChoices(choices: DownloadChoices) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.vd_sheet_format), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus").forEach { (value, label) ->
                GlassChip(label, choices.audioFormat == value, onClick = { choices.audioFormat = value })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.vd_sheet_quality), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (highName, highDetail) = stringResource(R.string.vd_audio_high).split(" · ", limit = 2).let { it.first() to it.getOrElse(1) { "" } }
            val (normalName, normalDetail) = stringResource(R.string.vd_audio_normal).split(" · ", limit = 2).let { it.first() to it.getOrElse(1) { "" } }
            ChoiceCard(highName, highDetail, choices.highBitrate, Modifier.weight(1f)) { choices.highBitrate = true }
            ChoiceCard(normalName, normalDetail, !choices.highBitrate, Modifier.weight(1f)) { choices.highBitrate = false }
        }
        ToggleRow(stringResource(R.string.vd_audio_cover), choices.cover) { choices.cover = it }
        ToggleRow(stringResource(R.string.vd_audio_lyrics), choices.lyrics) { choices.lyrics = it }
    }
}

/** A small card in a row of choices; the chosen one gets the main color's border and a check. */
@Composable
private fun ChoiceCard(title: String, detail: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        // only the title leaves room for the check, so choosing a card never rewraps its text
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 22.dp),
            )
            if (detail.isNotBlank()) {
                // "2160p · 2.1 GB" as two lines, which fit a third of a narrow phone
                Text(
                    detail.replace(" · ", "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.outline,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

@Composable
private fun OptionRow(@DrawableRes icon: Int, title: String, detail: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalGlass.current.accentText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant)
    }
}
