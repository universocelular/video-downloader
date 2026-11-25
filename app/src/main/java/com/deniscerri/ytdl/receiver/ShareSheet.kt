package com.deniscerri.ytdl.receiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.ResultItem
import com.deniscerri.ytdl.ui.home.BigButton
import com.deniscerri.ytdl.ui.home.Thumbnail
import com.deniscerri.ytdl.ui.home.hostOf
import com.deniscerri.ytdl.whatsapp.GlassProgress
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.PlayBadge
import com.deniscerri.ytdl.whatsapp.VdSize

/** The sheet for a link shared from another app (diseño/imagenes/02-5-compartir.png), owned by [ShareActivity]. */
@Stable
class ShareUiState(val url: String, val hideThumbnail: Boolean) {
    /** The video, once the link is read; until then the sheet shows "Analizando el enlace…" */
    var result by mutableStateOf<ResultItem?>(null)
    var failed by mutableStateOf(false)
    /** Off while the full sheet ("Más opciones") is on screen */
    var visible by mutableStateOf(true)
}

class ShareActions(
    val onDownload: () -> Unit,
    val onSplit: () -> Unit,
    val onMore: () -> Unit,
    val onDismiss: () -> Unit,
)

/** The video, then "Descargar", "Dividir para estados" and "Más opciones", over the app the link came from */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(state: ShareUiState, actions: ShareActions) {
    if (!state.visible) return
    val colors = MaterialTheme.colorScheme
    val result = state.result
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 128.dp, height = 76.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest)) {
                    if (result != null && result.thumb.isNotBlank()) Thumbnail(result.thumb, state.hideThumbnail, Modifier.fillMaxSize())
                    if (result != null && result.duration.isNotBlank()) PlayBadge(result.duration, Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val title = result?.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(if (state.failed) R.string.vd_home_failed_title else R.string.vd_home_analyzing)
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(hostOf(state.url), result?.duration?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (result == null && !state.failed) GlassProgress(null)
            // a link that could not be read is still downloaded as it is, with the user's preferences
            BigButton(R.drawable.baseline_download_24, stringResource(R.string.vd_tool_download_title), filled = true, enabled = result != null || state.failed, onClick = actions.onDownload)
            BigButton(R.drawable.ic_cut, stringResource(R.string.vd_tool_split_title), filled = false, onClick = actions.onSplit)
            TextButton(onClick = actions.onMore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.vd_sheet_more), style = MaterialTheme.typography.titleMedium, color = LocalGlass.current.accentText)
            }
        }
    }
}
