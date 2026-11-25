package com.deniscerri.ytdl.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlannerTest {
    private val second = 1_000_000L

    private fun keyframesEvery(stepUs: Long, durationUs: Long) = (0..durationUs step stepUs).toList()

    @Test
    fun cutsAtTheLastKeyframeBeforeTheLimit() {
        // The example in the guide: a 3:30 video with a keyframe every 4.2 s, split into parts of 1:30
        val parts = SplitPlanner.byDuration(keyframesEvery(4_200_000, 210 * second), 90 * second, 0, 210 * second)
        assertEquals(
            listOf(SplitPart(0, 88_200_000), SplitPart(88_200_000, 176_400_000), SplitPart(176_400_000, 210 * second)),
            parts,
        )
    }

    @Test
    fun aVideoWithinTheLimitStaysWhole() {
        val parts = SplitPlanner.byDuration(keyframesEvery(2 * second, 60 * second), 60 * second, 0, 60 * second)
        assertEquals(listOf(SplitPart(0, 60 * second)), parts)
    }

    @Test
    fun marksPartsThatHaveNoKeyframeToCutWithinTheLimit() {
        val parts = SplitPlanner.byDuration(listOf(0, 100 * second, 200 * second), 90 * second, 0, 250 * second)
        assertEquals(
            listOf(
                SplitPart(0, 100 * second, overLimit = true),
                SplitPart(100 * second, 200 * second, overLimit = true),
                SplitPart(200 * second, 250 * second),
            ),
            parts,
        )
    }

    @Test
    fun evenPartsHaveSimilarLengths() {
        val parts = SplitPlanner.evenParts(keyframesEvery(4_200_000, 210 * second), 210 * second, 90 * second)
        assertEquals(listOf(0L, 71_400_000L, 138_600_000L), parts.map { it.startUs })
        assertTrue(parts.all { it.durationUs <= 90 * second && !it.overLimit })
    }

    @Test
    fun evenPartsAddAPartWhenTheKeyframesLeaveTheLastOneTooLong() {
        // Two parts of 1:30 would need a cut at exactly 90 s, but the nearest keyframe before it is at 84 s
        val parts = SplitPlanner.evenParts(keyframesEvery(7 * second, 180 * second), 180 * second, 90 * second)
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.durationUs <= 90 * second })
    }

    @Test
    fun aRangeStartsOnTheKeyframeAtOrBeforeItsStart() {
        val parts = SplitPlanner.range(keyframesEvery(4_200_000, 210 * second), 210 * second, 90 * second, 30 * second, 200 * second)
        assertEquals(listOf(SplitPart(29_400_000, 117_600_000), SplitPart(117_600_000, 200 * second)), parts)
    }

    @Test
    fun aRangeEndingAfterTheVideoStopsAtTheEnd() {
        val parts = SplitPlanner.range(keyframesEvery(2 * second, 40 * second), 40 * second, 90 * second, 10 * second, 75 * second)
        assertEquals(listOf(SplitPart(10 * second, 40 * second)), parts)
        assertFalse(parts.single().overLimit)
    }

    @Test
    fun exactPartsLastExactlyTheChosenLength() {
        val parts = SplitPlanner.exact(SplitMode.WHOLE, 210 * second, 90 * second)
        assertEquals(listOf(SplitPart(0, 90 * second), SplitPart(90 * second, 180 * second), SplitPart(180 * second, 210 * second)), parts)
    }

    @Test
    fun exactEvenPartsShareTheTimeEqually() {
        val parts = SplitPlanner.exact(SplitMode.EVEN, 210 * second, 90 * second)
        assertEquals(listOf(70 * second, 70 * second, 70 * second), parts.map { it.durationUs })
    }

    @Test
    fun exactRangeStartsWhereTheUserSaid() {
        val parts = SplitPlanner.exact(SplitMode.RANGE, 210 * second, 90 * second, 30 * second, 200 * second)
        assertEquals(listOf(SplitPart(30 * second, 120 * second), SplitPart(120 * second, 200 * second)), parts)
    }

    @Test
    fun readsAndWritesClockTimes() {
        assertEquals(90 * second, SplitClock.parse("1:30"))
        assertEquals(90 * second, SplitClock.parse("90"))
        assertEquals(3723 * second, SplitClock.parse("1:02:03"))
        assertNull(SplitClock.parse("1:"))
        assertNull(SplitClock.parse("uno"))
        assertEquals("1:28", SplitClock.format(88_200_000))
        assertEquals("0:34", SplitClock.format(33_600_000))
        assertEquals("1:02:03", SplitClock.format(3723 * second))
    }
}
