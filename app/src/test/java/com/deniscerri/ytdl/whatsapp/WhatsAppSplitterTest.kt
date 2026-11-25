package com.deniscerri.ytdl.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppSplitterTest {
    @Test
    fun titlesKeepLettersAndDropEmojiAndSymbols() {
        assertEquals(
            "La CRUDA REALIDAD de ESPAÑA HOY Ojo si pensás venir",
            WhatsAppSplitter.cleanTitle("La CRUDA REALIDAD de ESPAÑA HOY ⚠️ ¡Ojo si pensás venir!.mp4"),
        )
    }

    @Test
    fun anEmptyTitleBecomesVideo() {
        assertEquals("video", WhatsAppSplitter.cleanTitle("⚠️🔥.mp4"))
    }
}
