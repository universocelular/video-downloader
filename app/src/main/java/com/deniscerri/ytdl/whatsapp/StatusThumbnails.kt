package com.deniscerri.ytdl.whatsapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** A status preview: a square picture and, for a video, its length */
class StatusThumbnail(val picture: Bitmap, val durationMs: Long?)

/** Decodes status previews off the main thread, a few at a time, and keeps the recent ones while the screen is open */
class StatusThumbnails(private val context: Context) {
    private val cache = object : LruCache<String, StatusThumbnail>(cacheBytes()) {
        override fun sizeOf(key: String, value: StatusThumbnail) = value.picture.allocationByteCount
    }
    private val decoding = Semaphore(PARALLEL_DECODES)

    fun cached(item: StatusItem, sizePx: Int): StatusThumbnail? = cache.get(key(item, sizePx))

    /** A square preview [sizePx] wide, or null when the file cannot be read, for example because WhatsApp deleted it */
    suspend fun thumbnail(item: StatusItem, sizePx: Int): StatusThumbnail? {
        cached(item, sizePx)?.let { return it }
        return decoding.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (item.isVideo) videoThumbnail(item, sizePx)
                    else StatusThumbnail(square(decodeImage(item, sizePx, cover = true), sizePx), null)
                }.getOrNull()?.also { cache.put(key(item, sizePx), it) }
            }
        }
    }

    /** The whole picture of a photo status, at most [maxSidePx] on its longer side, for the full-screen view */
    suspend fun picture(item: StatusItem, maxSidePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeImage(item, maxSidePx, cover = false) }.getOrNull()
    }

    /** Decodes a photo never larger than it is: covering [target] with its shorter side, or fitting its longer side in it */
    private fun decodeImage(item: StatusItem, target: Int, cover: Boolean): Bitmap {
        fun scale(width: Int, height: Int): Float =
            (target.toFloat() / (if (cover) minOf(width, height) else maxOf(width, height))).coerceAtMost(1f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, item.uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = scale(info.size.width, info.size.height)
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
                // Ordinary memory, so the picture can be cropped into a square
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Cannot read the size of ${item.name}")
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((1 / scale(bounds.outWidth, bounds.outHeight)).toInt().coerceAtLeast(1))
        }
        return context.contentResolver.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode ${item.name}")
    }

    private fun videoThumbnail(item: StatusItem, sizePx: Int): StatusThumbnail {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, item.uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Twice the square on both sides, so the shorter side of any shape still covers it
                retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, sizePx * 2, sizePx * 2)
            } else {
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: throw IOException("No frame in ${item.name}")
            return StatusThumbnail(square(frame, sizePx), durationMs)
        } finally {
            retriever.release()
        }
    }

    /** The middle square of [picture], scaled to [sizePx]; the original is recycled when a new bitmap is made */
    private fun square(picture: Bitmap, sizePx: Int): Bitmap =
        ThumbnailUtils.extractThumbnail(picture, sizePx, sizePx, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)

    private fun key(item: StatusItem, sizePx: Int) = "${item.uri}#${item.modifiedMs}@$sizePx"

    private companion object {
        const val PARALLEL_DECODES = 3
        const val MAX_CACHE_BYTES = 48 * 1024 * 1024

        /** An eighth of the memory the app may use, which keeps a few screens of previews */
        fun cacheBytes() = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(MAX_CACHE_BYTES.toLong()).toInt()
    }
}
