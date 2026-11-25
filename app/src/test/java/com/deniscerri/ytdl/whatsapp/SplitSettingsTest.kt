package com.deniscerri.ytdl.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SplitSettingsTest {
    private val second = 1_000_000L
    // 7:12 with a keyframe every 2 s
    private val probe = VideoProbe(432 * second, (0L..432L step 2).map { it * second })

    @Test
    fun exactPartsHaveTheChosenLengthAndTheLastOneTheRest() {
        val plan = SplitPlanner.forSettings(probe, 90, SplitMode.WHOLE, exact = true, rangeStartUs = 0, rangeEndUs = probe.durationUs)!!
        assertEquals(5, plan.size)
        // 50 ms under the limit, so WhatsApp never sees a part over it
        assertEquals(List(4) { 89_950_000L }, plan.dropLast(1).map { it.durationUs })
        assertEquals(432 * second, plan.last().endUs)
    }

    @Test
    fun aRangeUnderASecondMakesNoParts() {
        assertNull(SplitPlanner.forSettings(probe, 90, SplitMode.RANGE, exact = true, rangeStartUs = 60 * second, rangeEndUs = 60 * second + 500_000))
    }

    @Test
    fun theRangeOnlyCountsInRangeMode() {
        val whole = SplitPlanner.forSettings(probe, 90, SplitMode.WHOLE, exact = false, rangeStartUs = 60 * second, rangeEndUs = 61 * second)!!
        assertEquals(0L, whole.first().startUs)
        assertEquals(432 * second, whole.last().endUs)
    }
}

class SplitEtaTest {
    @Test
    fun saysNothingUntilThereIsEnoughToTell() {
        assertNull(SplitEta.remainingMs(elapsedMs = 2_000, fraction = 0.5f))
        assertNull(SplitEta.remainingMs(elapsedMs = 10_000, fraction = 0.01f))
    }

    @Test
    fun theRestTakesAsLongAsWhatWasDone() {
        assertEquals(30_000L, SplitEta.remainingMs(elapsedMs = 30_000, fraction = 0.5f))
        assertEquals(10_000L, SplitEta.remainingMs(elapsedMs = 30_000, fraction = 0.75f))
        assertEquals(0L, SplitEta.remainingMs(elapsedMs = 30_000, fraction = 1f))
    }
}
