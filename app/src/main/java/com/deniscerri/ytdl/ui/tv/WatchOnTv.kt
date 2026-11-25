package com.deniscerri.ytdl.ui.tv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.ui.home.BigButton
import com.deniscerri.ytdl.whatsapp.Illustration
import com.deniscerri.ytdl.whatsapp.LocalGlass
import com.deniscerri.ytdl.whatsapp.VdSize

/**
 * "Ver en la TV": Android's own screen casting (Transmitir, Smart View or Miracast, depending on the phone).
 * It is all Castto - Screen Mirroring does (notas/screen-mirroring-funciones.md), here without its ads.
 */
object TvCast {
    // Not every phone has the cast screen; some older ones only have the wireless display one
    private val castScreens = listOf(Settings.ACTION_CAST_SETTINGS, "android.settings.WIFI_DISPLAY_SETTINGS")

    /** False when the phone has neither screen */
    fun openCastScreen(context: Context): Boolean = castScreens.any { open(context, it) }

    fun openWifi(context: Context) = open(context, Settings.ACTION_WIFI_SETTINGS)

    private fun open(context: Context, action: String): Boolean = try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

/** The three steps and "Elegir TV"; [onPlay] adds "Reproducir el video" when the sheet was opened for one video */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchOnTvSheet(onDismiss: () -> Unit, onPlay: (() -> Unit)? = null) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var noCastScreen by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = VdSize.ScreenMargin, end = VdSize.ScreenMargin, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Illustration(R.drawable.vd_art_tv, size = 120.dp)
                Text(stringResource(R.string.vd_tool_tv_title), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            }
            Step(1, stringResource(R.string.vd_tv_step_wifi)) {
                TextButton(onClick = { TvCast.openWifi(context) }) {
                    Text(stringResource(R.string.vd_tv_wifi), style = MaterialTheme.typography.labelLarge, color = LocalGlass.current.accentText)
                }
            }
            Step(2, stringResource(R.string.vd_tv_step_tv))
            Step(3, stringResource(R.string.vd_tv_step_choose))
            if (noCastScreen) {
                Text(stringResource(R.string.vd_tv_no_cast_screen), style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }
            BigButton(R.drawable.vd_ic_tv, stringResource(R.string.vd_tv_choose), filled = true) {
                noCastScreen = !TvCast.openCastScreen(context)
            }
            if (onPlay != null) BigButton(R.drawable.ic_vd_play, stringResource(R.string.vd_tv_play), filled = false, onClick = onPlay)
            Text(stringResource(R.string.vd_tv_stop_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(number: Int, text: String, extra: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            extra?.invoke()
        }
    }
}
