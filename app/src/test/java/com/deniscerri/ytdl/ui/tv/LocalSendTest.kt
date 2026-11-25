package com.deniscerri.ytdl.ui.tv

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Date

class LocalSendTest {
    // What the TV's LocalSend 1.18.2 answered to register on 04/11/2026
    private val tvAnswer = """{"alias":"Smart Carrot","version":"2.2","deviceModel":"Tcl","deviceType":"mobile",""" +
        """"fingerprint":"DE726E5ECB6A914DF8985A3C3F13098FA3EEAD0E353EF7DD410FC670546EE821","download":false}"""

    @Test
    fun theCertificateIsSelfSignedForTwentyYears() {
        val identity = LocalSendIdentity.create("Video Downloader")
        val certificate = identity.certificate
        certificate.verify(certificate.publicKey)
        assertEquals("CN=Video Downloader", certificate.subjectX500Principal.name)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals(3, certificate.version)
        assertTrue(certificate.notAfter.after(Date(System.currentTimeMillis() + 19L * 365 * 24 * 60 * 60 * 1000)))
        certificate.checkValidity()
    }

    @Test
    fun theFingerprintIsTheUppercaseSha256OfTheCertificate() {
        val identity = LocalSendIdentity.create("Video Downloader")
        assertTrue(identity.fingerprint.matches(Regex("[0-9A-F]{64}")))
        assertEquals(sha256Hex(identity.certificate.encoded), identity.fingerprint)
    }

    @Test
    fun anIdentityComesBackFromItsFile() {
        val identity = LocalSendIdentity.create("Video Downloader")
        val file = File.createTempFile("localsend", ".id")
        try {
            identity.save(file)
            val loaded = LocalSendIdentity.load(file)!!
            assertEquals(identity.fingerprint, loaded.fingerprint)
            assertArrayEquals(identity.privateKey.encoded, loaded.privateKey.encoded)
        } finally {
            file.delete()
        }
    }

    @Test
    fun aBrokenFileGivesNoIdentity() {
        val file = File.createTempFile("localsend", ".id")
        try {
            file.writeText("nada")
            assertNull(LocalSendIdentity.load(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun objectIdentifiersAreWrittenInBase128() {
        val ecdsaWithSha256 = byteArrayOf(0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x04, 0x03, 0x02)
        assertArrayEquals(ecdsaWithSha256, Der.oid("1.2.840.10045.4.3.2"))
        assertArrayEquals(byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03), Der.oid("2.5.4.3"))
    }

    @Test
    fun theTvsAnswerBecomesADevice() {
        val device = parseDevice(tvAnswer, "192.168.1.44", own = "MINE")!!
        assertEquals("Smart Carrot", device.alias)
        assertEquals("Tcl", device.model)
        assertEquals("192.168.1.44", device.ip)
        assertEquals(LOCALSEND_PORT, device.port)
        assertEquals("DE726E5ECB6A914DF8985A3C3F13098FA3EEAD0E353EF7DD410FC670546EE821", device.fingerprint)
    }

    @Test
    fun thisPhoneAndOtherMessagesAreLeftOut() {
        assertNull(parseDevice(tvAnswer, "192.168.1.44", own = "de726e5ecb6a914df8985a3c3f13098fa3eead0e353ef7dd410fc670546ee821"))
        assertNull(parseDevice("""{"alias":"Sin huella"}""", "192.168.1.9", own = "MINE"))
        assertNull(parseDevice("no es JSON", "192.168.1.9", own = "MINE"))
    }

    @Test
    fun aReceiverWithoutEncryptionIsReachedByPlainHttp() {
        val device = parseDevice("""{"alias":"PC","fingerprint":"abc","protocol":"http","port":53318}""", "192.168.1.9", own = "MINE")!!
        assertFalse(device.https)
        assertEquals(53318, device.port)
        assertTrue(parseDevice(tvAnswer, "192.168.1.44", own = "MINE")!!.https)
    }

    @Test
    fun plainAnswersAreReadWithTheirLength() {
        val answer = "HTTP/1.1 200 OK\r\ncontent-type: application/json\r\nContent-Length: 13\r\n\r\n{\"ok\":\"bien\"}tail"
        assertEquals(200 to "{\"ok\":\"bien\"}", readHttpAnswer(ByteArrayInputStream(answer.toByteArray())))
    }

    @Test
    fun plainAnswersSentInChunksAreJoined() {
        val answer = "HTTP/1.1 403 Forbidden\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nRech\r\n5\r\nazado\r\n0\r\n\r\n"
        assertEquals(403 to "Rechazado", readHttpAnswer(ByteArrayInputStream(answer.toByteArray())))
    }

    @Test
    fun plainAnswersWithoutLengthRunToTheEnd() {
        val answer = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
        assertEquals(204 to "", readHttpAnswer(ByteArrayInputStream(answer.toByteArray())))
    }

    @Test
    fun prepareUploadDescribesEachFile() {
        val info = senderInfo("Video Downloader", "moto g", "AB12")
        val file = LocalSendFile("f1", "Viaje.mp4", 1234, "video/mp4") { ByteArrayInputStream(ByteArray(0)) }
        val body = prepareUploadBody(info, listOf(file))
        assertEquals("Video Downloader", body.getValue("info").jsonObject.getValue("alias").jsonPrimitive.content)
        assertEquals("https", body.getValue("info").jsonObject.getValue("protocol").jsonPrimitive.content)
        val described = body.getValue("files").jsonObject.getValue("f1").jsonObject
        assertEquals("Viaje.mp4", described.getValue("fileName").jsonPrimitive.content)
        assertEquals(1234L, described.getValue("size").jsonPrimitive.long)
        assertEquals("video/mp4", described.getValue("fileType").jsonPrimitive.content)
    }

    @Test
    fun theAnswerToPrepareUploadGivesTheSessionAndTokens() {
        assertEquals("s1" to mapOf("f1" to "t1"), parsePrepareAnswer("""{"sessionId":"s1","files":{"f1":"t1"}}"""))
        assertNull(parsePrepareAnswer("""{"files":{}}"""))
    }

    @Test
    fun refusalsFollowTheProtocolCodes() {
        assertEquals(LocalSendRefusal.Pin, refusalOf(401))
        assertEquals(LocalSendRefusal.Rejected, refusalOf(403))
        assertEquals(LocalSendRefusal.Busy, refusalOf(409))
        assertEquals(LocalSendRefusal.Busy, refusalOf(429))
        assertEquals(LocalSendRefusal.Failed, refusalOf(500))
    }

    @Test
    fun theNeighboursAreTheRestOfTheHomeNetwork() {
        val nearby = neighbours("192.168.1.2")
        assertEquals(253, nearby.size)
        assertEquals("192.168.1.1", nearby.first())
        assertEquals("192.168.1.254", nearby.last())
        assertTrue("192.168.1.2" !in nearby)
        assertTrue(neighbours("no es una dirección").isEmpty())
    }
}
