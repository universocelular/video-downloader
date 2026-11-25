package com.deniscerri.ytdl.ui.tv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.whatsapp.GlassBody
import com.deniscerri.ytdl.whatsapp.GlassButton
import com.deniscerri.ytdl.whatsapp.GlassPrimaryButton
import com.deniscerri.ytdl.whatsapp.GlassProgress
import com.deniscerri.ytdl.whatsapp.GlassTitle
import com.deniscerri.ytdl.whatsapp.Illustration
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize
import java.io.FileNotFoundException
import java.util.UUID

/** A file of the phone, as LocalSend sends it; null when Android cannot say how big it is */
fun localSendFileOf(context: Context, uri: Uri, name: String, knownSize: Long, mimeType: String): LocalSendFile? {
    val resolver = context.applicationContext.contentResolver
    val size = knownSize.takeIf { it > 0 } ?: runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull() ?: return null
    return LocalSendFile(UUID.randomUUID().toString(), name, size, mimeType) {
        resolver.openInputStream(uri) ?: throw FileNotFoundException(name)
    }
}

/**
 * "Enviar a la TV": looks for the TV's LocalSend, lets the user pick it and shows the file on its way, so it stays in
 * the TV's gallery to watch later with VLC.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToTvSheet(title: String, files: () -> List<LocalSendFile>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val state by TvSender.state.collectAsState()
    LaunchedEffect(Unit) {
        if (!TvSender.busy) TvSender.search(context)
    }
    val close = {
        TvSender.closed()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Illustration(R.drawable.vd_art_tv, size = 120.dp)
                Text(stringResource(R.string.vd_tv_send), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            when (val now = state) {
                TvSendState.Idle, TvSendState.Searching -> {
                    GlassProgress(null)
                    GlassBody(stringResource(R.string.vd_tv_send_searching))
                    GlassBody(stringResource(R.string.vd_tv_send_hint), soft = true)
                }
                is TvSendState.Found -> if (now.devices.isEmpty()) {
                    GlassBody(stringResource(R.string.vd_tv_send_none))
                    GlassBody(stringResource(R.string.vd_tv_send_hint), soft = true)
                    GlassButton(stringResource(R.string.vd_tv_send_retry), onClick = { TvSender.search(context) }, modifier = Modifier.fillMaxWidth())
                } else {
                    GlassBody(stringResource(R.string.vd_tv_send_choose), soft = true)
                    now.devices.forEach { device -> DeviceRow(device) { TvSender.send(context, device, files()) } }
                    TextButton(onClick = { TvSender.search(context) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.vd_tv_send_retry), color = LocalGlass.current.accentText, style = MaterialTheme.typography.labelLarge)
                    }
                }
                is TvSendState.Waiting -> {
                    GlassProgress(null)
                    GlassBody(stringResource(R.string.vd_tv_send_waiting, now.device.alias))
                    GlassButton(stringResource(R.string.cancel), onClick = TvSender::cancel, modifier = Modifier.fillMaxWidth())
                }
                is TvSendState.Sending -> {
                    GlassProgress(if (now.total > 0) now.sent.toFloat() / now.total else null)
                    GlassBody(stringResource(R.string.vd_tv_send_sending, now.device.alias))
                    GlassBody(
                        stringResource(R.string.vd_tv_send_progress, FileUtil.convertFileSize(now.sent), FileUtil.convertFileSize(now.total)),
                        soft = true,
                    )
                    GlassButton(stringResource(R.string.cancel), onClick = TvSender::cancel, modifier = Modifier.fillMaxWidth())
                }
                is TvSendState.Done -> {
                    GlassTitle(stringResource(R.string.vd_tv_send_done_title))
                    GlassBody(stringResource(R.string.vd_tv_send_done))
                    GlassPrimaryButton(stringResource(R.string.vd_close), onClick = close)
                }
                is TvSendState.Failed -> {
                    GlassBody(stringResource(refusalText(now.reason)))
                    GlassButton(stringResource(R.string.vd_tv_send_retry), onClick = { TvSender.search(context) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@StringRes
private fun refusalText(reason: LocalSendRefusal) = when (reason) {
    LocalSendRefusal.Rejected -> R.string.vd_tv_send_rejected
    LocalSendRefusal.Busy -> R.string.vd_tv_send_busy
    LocalSendRefusal.Pin -> R.string.vd_tv_send_pin
    LocalSendRefusal.Failed -> R.string.vd_tv_send_failed
}

@Composable
private fun DeviceRow(device: LocalSendDevice, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.vd_tool_tv), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.alias, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            device.model?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1) }
        }
        Icon(painterResource(R.drawable.vd_ic_chevron), contentDescription = null, tint = colors.onSurfaceVariant)
    }
}
