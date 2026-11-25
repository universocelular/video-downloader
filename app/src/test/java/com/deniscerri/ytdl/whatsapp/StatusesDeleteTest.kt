package com.deniscerri.ytdl.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusesDeleteTest {
    private val shown = listOf("a.jpg", "b.mp4", "c.jpg", "d.mp4")

    @Test
    fun deletingTheViewedStatusGoesOnWithTheNextOne() {
        assertEquals("c.jpg", nextViewing(shown, "b.mp4", setOf("b.mp4")))
    }

    @Test
    fun deletingTheLastStatusGoesBackToTheOneBefore() {
        assertEquals("c.jpg", nextViewing(shown, "d.mp4", setOf("d.mp4")))
    }

    @Test
    fun statusesDeletedTogetherAreSkipped() {
        assertEquals("d.mp4", nextViewing(shown, "b.mp4", setOf("b.mp4", "c.jpg")))
        assertEquals("a.jpg", nextViewing(shown, "c.jpg", setOf("b.mp4", "c.jpg", "d.mp4")))
    }

    @Test
    fun deletingEveryStatusClosesTheView() {
        assertNull(nextViewing(shown, "a.jpg", shown.toSet()))
    }

    @Test
    fun deletingOtherStatusesKeepsTheViewedOne() {
        assertEquals("b.mp4", nextViewing(shown, "b.mp4", setOf("a.jpg")))
        assertNull(nextViewing(shown, null, setOf("a.jpg")))
    }
}
