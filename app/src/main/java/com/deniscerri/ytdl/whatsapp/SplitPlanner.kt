package com.deniscerri.ytdl.whatsapp

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/** A part of the video, in microseconds. [overLimit] marks a part with no keyframe to cut it within the limit */
data class SplitPart(val startUs: Long, val endUs: Long, val overLimit: Boolean = false) {
    val durationUs: Long get() = endUs - startUs
}

enum class SplitMode { WHOLE, EVEN, RANGE }

/**
 * Plans where to cut a video for WhatsApp status without re-encoding it.
 * Cuts only fall on keyframes, and every part stays within the limit whenever the keyframes allow it.
 */
object SplitPlanner {
    private const val MAX_EVEN_ATTEMPTS = 20
    // Exact parts end this much before the limit, so container rounding never takes them over it
    private const val EXACT_MARGIN_US = 50_000L
    private const val MIN_RANGE_US = 1_000_000L

    /**
     * The parts the screen's settings make of a video: [seconds] per part, the [mode], exact (re-encoded) or cut on
     * keyframes, and the range for [SplitMode.RANGE]. Null when they make none, as with a range under a second.
     * The options screen and the background job both plan with this, so the parts made are the parts shown.
     */
    fun forSettings(probe: VideoProbe, seconds: Int, mode: SplitMode, exact: Boolean, rangeStartUs: Long, rangeEndUs: Long): List<SplitPart>? {
        val maxUs = seconds * 1_000_000L
        val startUs = if (mode == SplitMode.RANGE) rangeStartUs else 0L
        val endUs = if (mode == SplitMode.RANGE) rangeEndUs else probe.durationUs
        if (endUs - startUs < MIN_RANGE_US) return null
        return runCatching {
            when {
                exact -> exact(mode, probe.durationUs, maxUs - EXACT_MARGIN_US, startUs, endUs)
                mode == SplitMode.RANGE -> range(probe.keyframesUs, probe.durationUs, maxUs, startUs, endUs)
                else -> plan(mode, probe.keyframesUs, probe.durationUs, maxUs)
            }
        }.getOrNull()
    }

    fun plan(mode: SplitMode, keyframesUs: List<Long>, durationUs: Long, maxUs: Long, rangeStartUs: Long = 0, rangeEndUs: Long = durationUs): List<SplitPart> =
        when (mode) {
            SplitMode.WHOLE -> byDuration(keyframesUs, maxUs, 0, durationUs)
            SplitMode.EVEN -> evenParts(keyframesUs, durationUs, maxUs)
            SplitMode.RANGE -> range(keyframesUs, durationUs, maxUs, rangeStartUs, rangeEndUs)
        }

    /** Consecutive parts of at most [maxUs] between [fromUs] and [toUs], each as long as the keyframes allow */
    fun byDuration(keyframesUs: List<Long>, maxUs: Long, fromUs: Long, toUs: Long): List<SplitPart> {
        require(maxUs > 0 && toUs > fromUs)
        val cuts = keyframesUs.filter { it > fromUs && it < toUs }.sorted()
        val parts = mutableListOf<SplitPart>()
        var start = fromUs
        while (toUs - start > maxUs) {
            val limit = start + maxUs
            val cut = cuts.lastOrNull { it > start && it <= limit }
            if (cut != null) {
                parts += SplitPart(start, cut)
                start = cut
                continue
            }
            // No keyframe inside the limit, so this part has to run until the next one
            val next = cuts.firstOrNull { it > limit } ?: break
            parts += SplitPart(start, next, overLimit = true)
            start = next
        }
        parts += SplitPart(start, toUs, overLimit = toUs - start > maxUs)
        return parts
    }

    /** The fewest parts of similar length that each fit in [maxUs] */
    fun evenParts(keyframesUs: List<Long>, durationUs: Long, maxUs: Long): List<SplitPart> {
        require(maxUs > 0 && durationUs > 0)
        val cuts = keyframesUs.filter { it in 1 until durationUs }.sorted()
        var count = ceil(durationUs.toDouble() / maxUs).toInt().coerceAtLeast(1)
        repeat(MAX_EVEN_ATTEMPTS) {
            evenAttempt(cuts, durationUs, maxUs, count)?.let { return it }
            count++
        }
        return byDuration(keyframesUs, maxUs, 0, durationUs)
    }

    private fun evenAttempt(cuts: List<Long>, durationUs: Long, maxUs: Long, count: Int): List<SplitPart>? {
        val target = durationUs.toDouble() / count
        val parts = mutableListOf<SplitPart>()
        var start = 0L
        for (index in 1 until count) {
            val ideal = target * index
            val cut = cuts.filter { it > start && it - start <= maxUs }.minByOrNull { abs(it - ideal) } ?: return null
            parts += SplitPart(start, cut)
            start = cut
        }
        if (durationUs - start > maxUs) return null
        parts += SplitPart(start, durationUs)
        return parts
    }

    /**
     * Parts for a cut that re-encodes the video, so they can start and end on any frame: WHOLE and RANGE give
     * parts of exactly [maxUs] plus the remainder, EVEN spreads the time over the fewest equal parts that fit.
     */
    fun exact(mode: SplitMode, durationUs: Long, maxUs: Long, rangeStartUs: Long = 0, rangeEndUs: Long = durationUs): List<SplitPart> {
        require(maxUs > 0 && durationUs > 0)
        val from = if (mode == SplitMode.RANGE) rangeStartUs.coerceIn(0, durationUs) else 0L
        val to = if (mode == SplitMode.RANGE) rangeEndUs.coerceAtMost(durationUs) else durationUs
        require(to > from)
        val count = ceil((to - from).toDouble() / maxUs).toInt().coerceAtLeast(1)
        val partUs = if (mode == SplitMode.EVEN) ceil((to - from).toDouble() / count).toLong() else maxUs
        return (0 until count)
            .map { index -> SplitPart(from + index * partUs, minOf(from + (index + 1) * partUs, to)) }
            .filter { it.durationUs > 0 }
    }

    /** The range from [startUs] to [endUs], cut into parts of at most [maxUs] when it is longer */
    fun range(keyframesUs: List<Long>, durationUs: Long, maxUs: Long, startUs: Long, endUs: Long): List<SplitPart> {
        val end = endUs.coerceAtMost(durationUs)
        // A copy without re-encoding has to start on a keyframe, so the range begins at the one at or before startUs
        val start = keyframesUs.filter { it <= startUs }.maxOrNull() ?: 0L
        require(end > start)
        return byDuration(keyframesUs, maxUs, start, end)
    }
}

/** How long a job still needs, judged by how long it took so far ("faltan 1:20") */
object SplitEta {
    private const val MIN_FRACTION = 0.03f
    private const val MIN_ELAPSED_MS = 3_000L

    /** Null until enough of the work is done to tell, because the first seconds say little about the rest */
    fun remainingMs(elapsedMs: Long, fraction: Float): Long? {
        if (fraction < MIN_FRACTION || elapsedMs < MIN_ELAPSED_MS) return null
        if (fraction >= 1f) return 0L
        return (elapsedMs * (1 - fraction) / fraction).toLong()
    }
}

/** Reads and writes the m:ss times people type and see */
object SplitClock {
    /** "1:30", "90" or "1:02:03" in microseconds, or null when the text is not a time */
    fun parse(text: String): Long? {
        val pieces = text.trim().split(":")
        if (pieces.size > 3 || pieces.any { it.isBlank() }) return null
        var seconds = 0.0
        for (piece in pieces) {
            val value = piece.trim().replace(',', '.').toDoubleOrNull() ?: return null
            if (value < 0) return null
            seconds = seconds * 60 + value
        }
        return (seconds * 1_000_000).toLong()
    }

    /** The time rounded to the nearest second, as m:ss or h:mm:ss */
    fun format(us: Long): String {
        val total = ((us + 500_000) / 1_000_000).toInt()
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
