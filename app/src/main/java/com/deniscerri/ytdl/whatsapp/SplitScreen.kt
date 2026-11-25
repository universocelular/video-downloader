package com.deniscerri.ytdl.whatsapp

import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.downloads.ordinal
import com.deniscerri.ytdl.ui.home.card
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Everything the screen can ask for; the activity wires each one to the view model or to Android */
class SplitActions(
    val onPaste: () -> Unit,
    val onPickVideo: () -> Unit,
    val onRecent: (RecentVideo) -> Unit,
    /** A job from "En proceso" */
    val onOpenJob: (String) -> Unit,
    val onSeconds: (Int) -> Unit,
    val onMode: (SplitMode) -> Unit,
    val onExact: (Boolean) -> Unit,
    val onRange: (Long, Long) -> Unit,
    val onSplit: () -> Unit,
    /** Stops the job on screen, after the user confirmed it */
    val onCancelJob: () -> Unit,
    val onShare: (List<Uri>) -> Unit,
    val onOpenPart: (Uri) -> Unit,
    /** A part in the app's own player, so it reaches a TV that only shows this app */
    val onPlayPart: (Uri) -> Unit,
    val onAnother: () -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * "Dividir para estados" (diseño/imagenes/06-1-elegir.png to 06-5-un-tramo.png). [jobs] and [progress] come from the
 * [SplitQueue], which splits in the background, so a job's screen shows how it goes even after leaving and coming back.
 */
@Composable
fun SplitScreen(state: SplitUiState, jobs: List<SplitJob>, progress: Map<String, SplitProgress>, actions: SplitActions) {
    val job = jobs.firstOrNull { it.id == state.jobId }.takeIf { state.stage == SplitStage.JOB }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            GlassHeader(stringResource(R.string.vd_split_screen_title), actions.onBack)
        }
        Crossfade(targetState = state.stage to job?.status, animationSpec = tween(200), label = "split", modifier = Modifier.weight(1f)) { (stage, status) ->
            when (stage) {
                SplitStage.CHOOSE -> ChooseVideo(state, jobs, progress, actions)
                SplitStage.OBTAINING -> ObtainingVideo(state, actions)
                SplitStage.OPTIONS -> Options(state, actions)
                SplitStage.JOB -> when {
                    job == null -> Unit
                    status == SplitJobStatus.DONE -> Done(job, actions)
                    status == SplitJobStatus.FAILED -> Failed(job.error.orEmpty(), onRetry = actions.onBack, onAnother = actions.onAnother)
                    else -> JobWorking(state, job, jobs, progress[job.id], actions)
                }
                SplitStage.ERROR -> Failed(state.errorMessage.orEmpty(), onRetry = actions.onRetry, onAnother = actions.onAnother)
            }
        }
    }
}

private val RECENT_TILE = 120.dp

@Composable
private fun ChooseVideo(state: SplitUiState, jobs: List<SplitJob>, progress: Map<String, SplitProgress>, actions: SplitActions) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val thumbnails = remember(context) { StatusThumbnails(context.applicationContext) }
    val tilePx = with(LocalDensity.current) { RECENT_TILE.roundToPx() }
    // "En proceso" is only what still works: running first, then waiting in their order. A finished split leaves the
    // list; its parts are in Archivos › Partes and in the "¡Listo!" notification, which reopens it
    val shownJobs = remember(jobs) {
        jobs.filter { it.status == SplitJobStatus.RUNNING } + jobs.filter { it.status == SplitJobStatus.QUEUED }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.vd_split_which),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChoiceCard(R.drawable.ic_clipboard, stringResource(R.string.vd_split_paste_link), actions.onPaste, Modifier.weight(1f).fillMaxHeight())
                ChoiceCard(R.drawable.ic_image, stringResource(R.string.vd_split_from_gallery), actions.onPickVideo, Modifier.weight(1f).fillMaxHeight())
            }
        }
        if (shownJobs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.vd_split_in_progress),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
            items(shownJobs, key = { "job:" + it.id }, span = { GridItemSpan(maxLineSpan) }) { job ->
                JobRow(job, jobs, progress[job.id], thumbnails) { actions.onOpenJob(job.id) }
            }
        }
        if (state.recents.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.vd_split_recent),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
            }
            items(state.recents, key = { it.key }) { recent ->
                RecentTile(recent, thumbnails, tilePx) { actions.onRecent(recent) }
            }
        }
    }
}

@Composable
private fun ChoiceCard(@DrawableRes icon: Int, text: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 96.dp)
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalGlass.current.accentText, modifier = Modifier.size(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Text(text, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RecentTile(recent: RecentVideo, thumbnails: StatusThumbnails, sizePx: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val thumbnail by produceState(thumbnails.cached(recent.item, sizePx), recent.item, sizePx) {
        if (value == null) value = thumbnails.thumbnail(recent.item, sizePx)
    }
    val picture = remember(thumbnail) { thumbnail?.picture?.asImageBitmap() }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surfaceContainerHighest)
            .clickable(role = Role.Button, onClickLabel = recent.title, onClick = onClick),
    ) {
        if (picture != null) {
            Image(picture, contentDescription = recent.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_video), contentDescription = recent.title, tint = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
        }
        thumbnail?.durationMs?.let { PlayBadge(SplitClock.format(it * 1000), Modifier.align(Alignment.BottomStart).padding(6.dp)) }
    }
}

@Composable
private fun Options(state: SplitUiState, actions: SplitActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoCard(state)
        Section(stringResource(R.string.vd_split_duration))
        DurationChoice(state, actions)
        Section(stringResource(R.string.vd_split_which_part))
        Segmented(
            listOf(
                SplitMode.WHOLE to stringResource(R.string.vd_split_mode_whole),
                SplitMode.EVEN to stringResource(R.string.vd_split_mode_even),
                SplitMode.RANGE to stringResource(R.string.vd_split_mode_range),
            ),
            state.mode,
            actions.onMode,
        )
        if (state.mode == SplitMode.RANGE) RangePicker(state, actions) else if (state.plan.isNotEmpty()) PartsBar(state.plan)
        Text(
            planText(LocalContext.current.resources, state),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Section(stringResource(R.string.vd_split_quality))
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QualityCard(stringResource(R.string.vd_split_quality_exact), state.exact, { actions.onExact(true) }, Modifier.weight(1f).fillMaxHeight())
            QualityCard(stringResource(R.string.vd_split_quality_quick), !state.exact, { actions.onExact(false) }, Modifier.weight(1f).fillMaxHeight())
        }
        Text(
            stringResource(if (state.exact) R.string.vd_split_cut_exact_hint else R.string.vd_split_cut_original_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        GlassPrimaryButton(stringResource(R.string.vd_split_do), actions.onSplit, enabled = state.plan.isNotEmpty())
    }
}

/** The video's picture, length and name at the top of the options */
@Composable
private fun VideoCard(state: SplitUiState) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 112.dp, height = 68.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest)) {
            (state.frames.getOrNull(1) ?: state.frames.firstOrNull())?.let { Frame(it, Modifier.fillMaxSize()) }
            PlayBadge(SplitClock.format(state.durationUs), Modifier.align(Alignment.BottomStart).padding(4.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(state.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

/** 30 s, 60 s, 90 s or "Otra", which opens a box for any length */
@Composable
private fun DurationChoice(state: SplitUiState, actions: SplitActions) {
    var custom by remember { mutableStateOf(state.seconds !in SplitViewModel.PRESET_SECONDS) }
    val options = SplitViewModel.PRESET_SECONDS.map { it to stringResource(R.string.vd_split_seconds, it) } + (CUSTOM to stringResource(R.string.vd_split_other))
    Segmented(options, if (custom) CUSTOM else state.seconds) { chosen ->
        custom = chosen == CUSTOM
        if (!custom) actions.onSeconds(chosen)
    }
    if (custom) {
        var text by remember { mutableStateOf(state.seconds.toString()) }
        val glass = LocalGlass.current
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.filter { it.isDigit() }.take(3)
                text.toIntOrNull()?.let(actions.onSeconds)
            },
            label = { Text(stringResource(R.string.vd_split_seconds_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = glass.text,
                unfocusedTextColor = glass.text,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = glass.accentText,
                unfocusedLabelColor = glass.textSoft,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.vd_split_limit_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val CUSTOM = -1

/** Equal buttons side by side, one of them chosen */
@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val chosen = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = VdSize.SmallControl + 4.dp)
                    .clip(shape)
                    .background(if (chosen) colors.primary else colors.surfaceContainer)
                    .border(1.dp, if (chosen) colors.primary else colors.outlineVariant, shape)
                    .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (chosen) colors.onPrimary else colors.onSurface, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** The video as a bar cut into its numbered parts (diseño/imagenes/06-2-opciones.png) */
@Composable
private fun PartsBar(plan: List<SplitPart>) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val numbered = plan.size <= MAX_NUMBERED_PARTS
    Row(Modifier.fillMaxWidth().height(36.dp).clip(shape).border(1.dp, colors.primary, shape)) {
        plan.forEachIndexed { index, part ->
            if (index > 0) Box(Modifier.width(2.dp).fillMaxHeight().background(colors.background))
            Box(
                Modifier
                    .weight(part.durationUs.toFloat().coerceAtLeast(1f))
                    .fillMaxHeight()
                    .background(colors.primary.copy(alpha = if (index % 2 == 0) 0.6f else 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                if (numbered) Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
            }
        }
    }
}

private const val MAX_NUMBERED_PARTS = 12

/** "Un tramo": the frames of the video, the chosen stretch lit, and the two handles below (diseño/imagenes/06-5-un-tramo.png) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePicker(state: SplitUiState, actions: SplitActions) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val totalSeconds = (state.durationUs / 1_000_000f).coerceAtLeast(1f)
    val start = (state.rangeStartUs / 1_000_000f).coerceIn(0f, totalSeconds)
    val end = (state.rangeEndUs / 1_000_000f).coerceIn(start, totalSeconds)
    // whole seconds, except the very end, which is the video's own end
    fun toUs(seconds: Float) = if (seconds >= totalSeconds) state.durationUs else (seconds.roundToLong() * 1_000_000).coerceAtMost(state.durationUs)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vd_split_from_time, SplitClock.format(state.rangeStartUs)), style = MaterialTheme.typography.titleSmall, color = glass.accentText)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.vd_split_to_time, SplitClock.format(state.rangeEndUs)), style = MaterialTheme.typography.titleSmall, color = glass.accentText)
        }
        Box(Modifier.fillMaxWidth().height(64.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHighest)) {
            Row(Modifier.fillMaxSize()) {
                state.frames.forEach { Frame(it, Modifier.weight(1f).fillMaxHeight()) }
            }
            Canvas(Modifier.fillMaxSize()) {
                val from = size.width * (start / totalSeconds)
                val to = size.width * (end / totalSeconds)
                val dim = Color.Black.copy(alpha = 0.6f)
                drawRect(dim, size = Size(from, size.height))
                drawRect(dim, topLeft = Offset(to, 0f), size = Size(size.width - to, size.height))
                val line = 3.dp.toPx()
                drawRoundRect(
                    colors.primary,
                    topLeft = Offset(from + line / 2, line / 2),
                    size = Size((to - from - line).coerceAtLeast(0f), size.height - line),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(line),
                )
            }
        }
        RangeSlider(
            value = start..end,
            onValueChange = { range -> actions.onRange(toUs(range.start), toUs(range.endInclusive)) },
            valueRange = 0f..totalSeconds,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.outlineVariant,
            ),
        )
    }
}

/** "5 partes de 1:30 (la última, de 1:12)" */
private fun planText(resources: Resources, state: SplitUiState): String {
    val plan = state.plan
    if (plan.isEmpty()) return if (state.mode == SplitMode.RANGE && state.durationUs > 0) resources.getString(R.string.vd_split_range_short) else ""
    val lengths = plan.map { it.durationUs }
    val first = lengths.first()
    fun same(a: Long, b: Long) = abs(a - b) < HALF_SECOND_US
    return when {
        lengths.all { same(it, first) } -> resources.getQuantityString(R.plurals.vd_split_parts_of, plan.size, plan.size, SplitClock.format(first))
        lengths.dropLast(1).all { same(it, first) } ->
            resources.getString(R.string.vd_split_parts_last, plan.size, SplitClock.format(first), SplitClock.format(lengths.last()))
        else -> resources.getString(R.string.vd_split_parts_up_to, plan.size, SplitClock.format(lengths.max()))
    }
}

private const val HALF_SECOND_US = 500_000L

/** "Exacta (recomendada)" and "Rápida", as two cards with a check */
@Composable
private fun QualityCard(title: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .clip(shape)
            .background(if (chosen) colors.primary else colors.surfaceContainer)
            .border(1.dp, if (chosen) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .then(if (chosen) Modifier.background(colors.onPrimary) else Modifier.border(2.dp, colors.outline, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (chosen) colors.onPrimary else colors.onSurface)
    }
}

/** Getting the video ready: the moving cut over an empty film and its one step; the user waits on this screen */
@Composable
private fun ObtainingVideo(state: SplitUiState, actions: SplitActions) {
    val resources = LocalContext.current.resources
    Working(
        frames = emptyList(),
        plan = emptyList(),
        steps = listOf(
            StepItem(
                stepLabel(resources, SplitStep.OBTAIN, state.exact, state.fromLink),
                StepStatus.Active,
                SplitProgress(SplitStep.OBTAIN, state.obtainProgress, null),
            )
        ),
        notice = null,
        hint = stringResource(R.string.vd_split_keep_open),
        onAnother = null,
        onCancel = actions.onBack,
    )
}

/** A job of the queue, waiting or running: it goes on when the user leaves, and "Cancelar" asks first */
@Composable
private fun JobWorking(state: SplitUiState, job: SplitJob, jobs: List<SplitJob>, progress: SplitProgress?, actions: SplitActions) {
    val resources = LocalContext.current.resources
    val fromDraft = job.id in state.draftJobs
    val waiting = job.status == SplitJobStatus.QUEUED
    val step = progress?.step ?: SplitStep.PROCESS
    val steps = listOf(
        StepItem(stepLabel(resources, SplitStep.OBTAIN, job.exact, fromDraft && state.fromLink), StepStatus.Done, null),
        StepItem(
            stepLabel(resources, SplitStep.PROCESS, job.exact, fromLink = false),
            when {
                waiting -> StepStatus.Pending
                step == SplitStep.PROCESS -> StepStatus.Active
                else -> StepStatus.Done
            },
            progress.takeIf { !waiting && step == SplitStep.PROCESS },
        ),
        StepItem(
            stepLabel(resources, SplitStep.SAVE, job.exact, fromLink = false),
            if (!waiting && step == SplitStep.SAVE) StepStatus.Active else StepStatus.Pending,
            null,
        ),
    )
    var confirming by remember { mutableStateOf(false) }
    Working(
        // the frames and the cuts are known only for the video loaded on this screen
        frames = if (fromDraft) state.frames else emptyList(),
        plan = if (fromDraft) state.plan else emptyList(),
        steps = steps,
        notice = if (waiting) waitingText(resources, job, jobs) else null,
        hint = stringResource(R.string.vd_split_background_hint),
        onAnother = actions.onAnother,
        onCancel = { confirming = true },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.vd_split_cancel_title)) },
            text = { Text(stringResource(R.string.vd_split_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    actions.onCancelJob()
                }) {
                    Text(stringResource(R.string.vd_split_cancel_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.vd_split_cancel_keep), color = LocalGlass.current.accentText)
                }
            },
        )
    }
}

/** "En espera · segundo en la fila" */
private fun waitingText(resources: Resources, job: SplitJob, jobs: List<SplitJob>): String {
    val place = jobs.filter { it.status == SplitJobStatus.QUEUED }.indexOfFirst { it.id == job.id } + 1
    return if (place > 0) resources.getString(R.string.vd_task_waiting_place, ordinal(resources, place)) else resources.getString(R.string.vd_task_waiting)
}

private enum class StepStatus { Done, Active, Pending }

private data class StepItem(val label: String, val status: StepStatus, val progress: SplitProgress?)

/** A moving cut over the film, the steps below it, what to know meanwhile, and the ways out (diseño 06-3) */
@Composable
private fun Working(
    frames: List<Bitmap>,
    plan: List<SplitPart>,
    steps: List<StepItem>,
    notice: String?,
    hint: String,
    onAnother: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = VdSize.ScreenMargin, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CuttingAnimation(frames, plan, Modifier.padding(vertical = 12.dp))
        if (notice != null) {
            Text(notice, style = MaterialTheme.typography.titleMedium, color = glass.accentText, textAlign = TextAlign.Center)
        }
        steps.forEach { StepRow(it) }
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (onAnother != null) {
            TextButton(onClick = onAnother) {
                Text(stringResource(R.string.vd_split_another), style = MaterialTheme.typography.titleMedium, color = glass.accentText)
            }
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.vd_split_cancel), style = MaterialTheme.typography.titleMedium, color = glass.accentText)
        }
    }
}

@Composable
private fun StepRow(item: StepItem) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val fraction = item.progress?.fraction?.takeIf { item.status == StepStatus.Active }
    Row(
        Modifier.fillMaxWidth().card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            when (item.status) {
                StepStatus.Done -> Box(Modifier.size(30.dp).clip(CircleShape).background(colors.tertiary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(20.dp))
                }
                StepStatus.Active -> if (fraction != null) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(26.dp), color = colors.primary, strokeWidth = 3.dp, trackColor = colors.outlineVariant)
                } else {
                    CircularProgressIndicator(Modifier.size(26.dp), color = colors.primary, strokeWidth = 3.dp)
                }
                StepStatus.Pending -> Box(Modifier.size(26.dp).border(2.dp, colors.outline, CircleShape))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                // "Preparando para WhatsApp · 64 % · faltan 1:20"
                if (item.status == StepStatus.Active) stepText(resources, item.label, item.progress) else item.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.status == StepStatus.Pending) colors.onSurfaceVariant else colors.onSurface,
            )
            if (fraction != null) {
                Spacer(Modifier.height(8.dp))
                GlassProgress(fraction)
            }
        }
    }
}

/** A strip of the video's frames with a line of light running across it, and the cuts the plan makes (diseño 06-3) */
@Composable
private fun CuttingAnimation(frames: List<Bitmap>, plan: List<SplitPart>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val glow = LocalGlass.current.accentText
    val transition = rememberInfiniteTransition(label = "cut")
    val sweep by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "line",
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(156.dp)) {
        val film = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .align(Alignment.Center)
                .clip(film)
                .background(colors.surfaceContainerHighest)
                .border(3.dp, colors.primary, film)
                .padding(6.dp),
        ) {
            Row(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
                frames.forEach { Frame(it, Modifier.weight(1f).fillMaxHeight()) }
            }
            if (plan.size > 1) {
                val from = plan.first().startUs
                val span = (plan.last().endUs - from).coerceAtLeast(1).toFloat()
                Canvas(Modifier.fillMaxSize()) {
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                    plan.drop(1).forEach { part ->
                        val x = size.width * ((part.startUs - from) / span)
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), pathEffect = dashes)
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * sweep
            val bottom = size.height - 34.dp.toPx()
            drawLine(glow.copy(alpha = 0.3f), Offset(x, 8.dp.toPx()), Offset(x, bottom), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                Brush.verticalGradient(listOf(glow, Color.White, glow)),
                Offset(x, 8.dp.toPx()),
                Offset(x, bottom),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val width = maxWidth
        Icon(
            painterResource(R.drawable.ic_cut),
            contentDescription = null,
            tint = glow,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset { IntOffset((width * sweep - 15.dp).roundToPx(), 0) }
                .size(30.dp),
        )
    }
}

/** A finished job: its parts, ready to share (diseño/imagenes/06-4-listo.png) */
@Composable
private fun Done(job: SplitJob, actions: SplitActions) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val context = LocalContext.current
    val thumbnails = remember(context) { StatusThumbnails(context.applicationContext) }
    val pictureSizePx = with(LocalDensity.current) { PART_PICTURE.roundToPx() }
    val uris = remember(job.parts) { job.parts.map { Uri.parse(it.uri) } }
    LazyColumn(
        contentPadding = PaddingValues(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Illustration(R.drawable.vd_art_split_done, size = 130.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(R.plurals.vd_split_done, job.parts.size, job.parts.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(job.title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        itemsIndexed(job.parts) { index, part ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
                    .clickable(role = Role.Button) { actions.onPlayPart(uris[index]) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(width = 104.dp, height = 60.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest)) {
                    PartPicture(uris[index], index, thumbnails, pictureSizePx)
                    PlayBadge(SplitClock.format(part.durationUs), Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vd_split_part_length, index + 1, SplitClock.format(part.durationUs)), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    if (part.overLimit) Text(stringResource(R.string.vd_split_part_over), style = MaterialTheme.typography.bodySmall, color = glass.warning)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassPrimaryButton(stringResource(R.string.vd_split_share), { actions.onShare(uris) })
                // the phone's player, where the other parts are a swipe away
                TextButton(onClick = { uris.firstOrNull()?.let(actions.onOpenPart) }) {
                    Text(stringResource(R.string.vd_split_gallery), style = MaterialTheme.typography.titleMedium, color = glass.accentText)
                }
                Text(stringResource(R.string.vd_split_share_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                TextButton(onClick = actions.onAnother, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.vd_split_another), color = glass.accentText)
                }
            }
        }
    }
}

private val PART_PICTURE = 104.dp

/** A frame of a saved part, read from the gallery, so it shows also after the app was closed */
@Composable
private fun PartPicture(uri: Uri, index: Int, thumbnails: StatusThumbnails, sizePx: Int) {
    val item = remember(uri) { StatusItem(uri, "part-${index + 1}.mp4", "video/mp4", 0) }
    val thumbnail by produceState(thumbnails.cached(item, sizePx), item, sizePx) {
        if (value == null) value = thumbnails.thumbnail(item, sizePx)
    }
    val picture = remember(thumbnail) { thumbnail?.picture?.asImageBitmap() }
    if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** A job under "En proceso": how it goes, or what it made */
@Composable
private fun JobRow(job: SplitJob, jobs: List<SplitJob>, progress: SplitProgress?, thumbnails: StatusThumbnails, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val resources = LocalContext.current.resources
    val pictureSizePx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val line = when (job.status) {
        SplitJobStatus.RUNNING -> stepText(resources, stepLabel(resources, progress?.step ?: SplitStep.PROCESS, job.exact, fromLink = false), progress)
        SplitJobStatus.QUEUED -> waitingText(resources, job, jobs)
        SplitJobStatus.DONE -> pluralStringResource(R.plurals.vd_split_done, job.parts.size, job.parts.size)
        else -> stringResource(R.string.vd_split_failed)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .card(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            val first = job.parts.firstOrNull()
            when {
                first != null -> PartPicture(Uri.parse(first.uri), 0, thumbnails, pictureSizePx)
                job.status == SplitJobStatus.FAILED -> Icon(painterResource(R.drawable.vd_ic_warning), contentDescription = null, tint = colors.error, modifier = Modifier.size(26.dp))
                else -> Icon(painterResource(R.drawable.ic_cut), contentDescription = null, tint = LocalGlass.current.accentText, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.status == SplitJobStatus.FAILED) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (job.status == SplitJobStatus.RUNNING) {
                Spacer(Modifier.height(6.dp))
                GlassProgress(progress?.fraction)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Why it failed, and what to do now */
@Composable
private fun Failed(message: String, onRetry: () -> Unit, onAnother: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Illustration(R.drawable.vd_art_error)
        Text(stringResource(R.string.vd_split_failed), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        GlassPrimaryButton(stringResource(R.string.vd_split_try_again), onRetry)
        TextButton(onClick = onAnother) { Text(stringResource(R.string.vd_split_another), color = LocalGlass.current.accentText) }
    }
}

@Composable
private fun Frame(bitmap: Bitmap, modifier: Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
