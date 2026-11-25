package com.deniscerri.ytdl.whatsapp

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Cuts parts that start and end exactly where planned by re-encoding them with the phone's encoders,
 * into the format WhatsApp status takes without trouble: H.264 up to 1080p at 30 fps without B-frames,
 * with stereo AAC-LC. [WhatsAppSplitter.rewriteForStatus] then writes the final MP4.
 */
@OptIn(UnstableApi::class)
class ExactSplitter(private val context: Context) {

    /** Encodes the parts one after another; [onProgress] gets the part index and that part's progress from 0 to 1 */
    suspend fun cut(input: File, parts: List<SplitPart>, probe: VideoProbe, outDir: File, onProgress: (Int, Float) -> Unit): List<File> {
        outDir.deleteRecursively()
        outDir.mkdirs()
        return parts.mapIndexed { index, part ->
            val output = File(outDir, String.format(Locale.ROOT, "part_%03d.mp4", index))
            val bitrate = videoBitrateFor(part)
            export(editedItem(input, part, probe, bitrate), output, bitrate) { fraction -> onProgress(index, fraction) }
            output
        }
    }

    /** The video bitrate that keeps the part under [MAX_PART_BYTES], within sensible bounds for its picture size */
    private fun videoBitrateFor(part: SplitPart): Int {
        val seconds = (part.durationUs / 1_000_000.0).coerceAtLeast(1.0)
        return (MAX_PART_BYTES * 8 / seconds - AUDIO_BITRATE).toInt().coerceIn(MIN_VIDEO_BITRATE, MAX_VIDEO_BITRATE)
    }

    private fun editedItem(input: File, part: SplitPart, probe: VideoProbe, videoBitrate: Int): EditedMediaItem {
        val clip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionUs(part.startUs)
            .setEndPositionUs(part.endUs)
            .build()
        val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(input)).setClippingConfiguration(clip).build()

        // Only drops frames when the source has more than 30 per second
        val videoEffects = mutableListOf<Effect>(FrameDropEffect.createDefaultFrameDropEffect(TARGET_FPS))
        // A low bitrate looks better at 720p than at 1080p, as WhatsApp itself does for status
        val shortSide = if (videoBitrate < FULL_HD_MIN_BITRATE) HD_SHORT_SIDE else FULL_HD_SHORT_SIDE
        if (probe.shortSide > shortSide) videoEffects += Presentation.createForShortSide(shortSide)

        // Any audio processor makes Transformer re-encode the sound; without one it copies the site's AAC stream as it came
        return EditedMediaItem.Builder(mediaItem).setEffects(Effects(listOf(stereoMix()), videoEffects)).build()
    }

    /**
     * Mixes mono, stereo and surround sound to stereo. It has a mix for every channel count Media3 supports,
     * because a stream can decode to more channels than its file declares.
     */
    private fun stereoMix(): AudioProcessor = ChannelMixingAudioProcessor().apply {
        for (channels in 1..MAX_INPUT_CHANNELS) {
            runCatching {
                putChannelMixingMatrix(
                    if (channels > 2) ChannelMixingMatrix.createForConstantPower(channels, 2)
                    else ChannelMixingMatrix.createForConstantGain(channels, 2)
                )
            }
        }
    }

    /** Runs one export on the main thread, which Transformer requires, and follows its progress */
    private suspend fun export(item: EditedMediaItem, output: File, videoBitrate: Int, onProgress: (Float) -> Unit) = withContext(Dispatchers.Main) {
        coroutineScope {
            val finished = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            // Every frame refers only to earlier ones, as with x264's ultrafast preset; Media3 otherwise
                            // turns on B-frames or temporal layers on the chips its codec list recommends them for
                            VideoEncoderSettings.Builder()
                                .setBitrate(videoBitrate)
                                .setMaxBFrames(0)
                                .setTemporalLayers(0, 0)
                                .build()
                        )
                        .setRequestedAudioEncoderSettings(
                            AudioEncoderSettings.Builder()
                                .setProfile(MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                                .setBitrate(AUDIO_BITRATE)
                                .build()
                        )
                        .setEnableFallback(true)
                        .build()
                )
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        finished.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        finished.completeExceptionally(exportException)
                    }
                })
                .build()
            transformer.start(item, output.absolutePath)

            val progress = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    delay(PROGRESS_INTERVAL_MS)
                }
            }
            try {
                finished.await()
            } catch (e: CancellationException) {
                transformer.cancel()
                throw e
            } finally {
                progress.cancel()
            }
        }
    }

    companion object {
        private const val TARGET_FPS = 30f
        private const val FULL_HD_SHORT_SIDE = 1080
        private const val HD_SHORT_SIDE = 720
        private const val FULL_HD_MIN_BITRATE = 2_500_000
        // Status videos over about 16 MB are reported to fail, and the app that works for the user makes small 30 s parts
        private const val MAX_PART_BYTES = 15.0 * 1024 * 1024
        private const val AUDIO_BITRATE = 128_000
        // 5.1, the largest layout Media3 has a stereo mix for
        private const val MAX_INPUT_CHANNELS = 6
        private const val MIN_VIDEO_BITRATE = 700_000
        private const val MAX_VIDEO_BITRATE = 3_500_000
        private const val PROGRESS_INTERVAL_MS = 300L
    }
}
