package com.deniscerri.ytdl.whatsapp

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsAppStatusesTest {
    private val whatsapp = WhatsAppApp.WHATSAPP.statusFolders
    private val business = WhatsAppApp.BUSINESS.statusFolders

    @Test
    fun theStatusesFolderItselfNeedsNoSteps() {
        assertEquals(listOf(emptyList<String>()), WhatsAppStatuses.statusesRoutes(whatsapp, ".Statuses"))
    }

    @Test
    fun androidMediaLeadsThroughWhatsAppsOwnFolder() {
        assertEquals(
            listOf("com.whatsapp", "WhatsApp", "Media", ".Statuses"),
            WhatsAppStatuses.statusesRoutes(whatsapp, "media").first(),
        )
    }

    @Test
    fun whatsAppsMediaFolderIsOneStepAwayBeforeTheSameNameInAnotherCase() {
        assertEquals(
            listOf(listOf(".Statuses"), listOf("com.whatsapp", "WhatsApp", "Media", ".Statuses")),
            WhatsAppStatuses.statusesRoutes(whatsapp, "Media"),
        )
    }

    @Test
    fun anyOtherFolderIsTakenAsTheTopOfTheStorage() {
        assertEquals(
            listOf(
                listOf("Android", "media", "com.whatsapp.w4b", "WhatsApp Business", "Media", ".Statuses"),
                listOf("WhatsApp Business", "Media", ".Statuses"),
            ),
            WhatsAppStatuses.statusesRoutes(business, "Almacenamiento interno"),
        )
    }

    @Test
    fun whatsAppsFolderIsNotTakenForBusiness() {
        assertEquals(
            WhatsAppStatuses.statusesRoutes(business, "Almacenamiento interno"),
            WhatsAppStatuses.statusesRoutes(business, "com.whatsapp"),
        )
    }
}
