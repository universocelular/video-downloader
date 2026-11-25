package com.deniscerri.ytdl.whatsapp

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * What the splitter needs to know about a video file, read from its index without decoding any frame:
 * duration, keyframe times and the shorter side of the picture
 */
data class VideoProbe(val durationUs: Long, val keyframesUs: List<Long>, val shortSide: Int = 0) {
    companion object {
        private const val NEXT_KEYFRAME_STEP_US = 1_000L
        private const val LONG_VIDEO_US = 10_000_000L

        fun read(file: File): VideoProbe {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val videoTrack = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
                } ?: throw IllegalArgumentException("${file.name} has no video track")
                val durationUs = durationOf(file, extractor)
                val videoFormat = extractor.getTrackFormat(videoTrack)
                val shortSide = minOf(videoFormat.getIntOr(MediaFormat.KEY_WIDTH), videoFormat.getIntOr(MediaFormat.KEY_HEIGHT))
                extractor.selectTrack(videoTrack)

                var keyframes = jumpThroughKeyframes(extractor)
                if (keyframes.size < 2 && durationUs > LONG_VIDEO_US) {
                    // Some files cannot seek to the next keyframe; read every frame's flags instead
                    keyframes = scanAllFrames(extractor)
                }
                return VideoProbe(durationUs, keyframes, shortSide)
            } finally {
                extractor.release()
            }
        }

        /** Length of a finished part, to show it and to check it against the limit */
        fun durationUs(file: File): Long {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            } catch (e: RuntimeException) {
                0L
            } finally {
                retriever.release()
            }
        }

        private fun jumpThroughKeyframes(extractor: MediaExtractor): List<Long> {
            val keyframes = mutableListOf<Long>()
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (true) {
                val time = extractor.sampleTime
                if (time < 0 || (keyframes.isNotEmpty() && time <= keyframes.last())) break
                keyframes += time
                extractor.seekTo(time + NEXT_KEYFRAME_STEP_US, MediaExtractor.SEEK_TO_NEXT_SYNC)
            }
            return keyframes
        }

        private fun scanAllFrames(extractor: MediaExtractor): List<Long> {
            val keyframes = mutableListOf<Long>()
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            do {
                val time = extractor.sampleTime
                if (time < 0) break
                if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) keyframes += time
            } while (extractor.advance())
            return keyframes
        }

        private fun MediaFormat.getIntOr(key: String, default: Int = 0): Int =
            if (containsKey(key)) getInteger(key) else default

        private fun durationOf(file: File, extractor: MediaExtractor): Long {
            val fromTracks = (0 until extractor.trackCount).maxOfOrNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            } ?: 0L
            return if (fromTracks > 0) fromTracks else durationUs(file)
        }
    }
}
