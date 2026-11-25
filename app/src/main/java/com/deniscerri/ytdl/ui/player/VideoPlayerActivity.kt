package com.deniscerri.ytdl.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.deniscerri.ytdl.ui.BaseActivity

/**
 * Plays a downloaded video or a part inside the app. When Android casts only Video Downloader to a TV, only what the
 * app shows reaches it: a video opened in the phone's own player would leave the TV black with just the sound.
 */
class VideoPlayerActivity : BaseActivity() {
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val exoPlayer = ExoPlayer.Builder(this)
            // pauses for calls and other apps' sound, and when headphones are unplugged
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.seekTo(savedInstanceState?.getLong(KEY_POSITION) ?: 0L)
        exoPlayer.playWhenReady = savedInstanceState?.getBoolean(KEY_PLAYING) ?: true
        exoPlayer.prepare()
        player = exoPlayer

        setContentView(PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            this.player = exoPlayer
            // the full-screen button turns the video sideways, also with the phone's rotation locked
            setFullscreenButtonClickListener { wide ->
                requestedOrientation = if (wide) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        })
    }

    override fun onStop() {
        // the sound stops when the user leaves the app
        player?.pause()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let {
            outState.putLong(KEY_POSITION, it.currentPosition)
            outState.putBoolean(KEY_PLAYING, it.playWhenReady)
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val KEY_POSITION = "position"
        private const val KEY_PLAYING = "playing"

        /** [uri] is a file of the app's own (FileProvider or the gallery), readable without asking */
        fun intent(context: Context, uri: Uri): Intent =
            Intent(context, VideoPlayerActivity::class.java).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
