package dev.claudiocodigo.nexo.domain.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlNormalizerTest {

    @Test
    fun `host without scheme is assumed https and path is preserved`() {
        val result = ServerUrlNormalizer.normalize("cloud.example.com/nextcloud")
        assertTrue(result is ServerUrlNormalizer.Result.Ok)
        result as ServerUrlNormalizer.Result.Ok
        assertEquals("https://cloud.example.com/nextcloud", result.server)
        assertEquals("cloud.example.com", result.host)
    }

    @Test
    fun `explicit https is kept and trailing slash removed`() {
        val result = ServerUrlNormalizer.normalize("https://cloud.example.com/")
        assertTrue(result is ServerUrlNormalizer.Result.Ok)
        assertEquals("https://cloud.example.com", (result as ServerUrlNormalizer.Result.Ok).server)
    }

    @Test
    fun `http is rejected`() {
        val result = ServerUrlNormalizer.normalize("http://cloud.example.com")
        assertTrue(result is ServerUrlNormalizer.Result.Error)
        assertTrue((result as ServerUrlNormalizer.Result.Error).reason.contains("HTTPS"))
    }

    @Test
    fun `blank server and missing host are rejected`() {
        assertTrue(ServerUrlNormalizer.normalize("") is ServerUrlNormalizer.Result.Error)
        assertTrue(ServerUrlNormalizer.normalize("https://") is ServerUrlNormalizer.Result.Error)
    }

    @Test
    fun `embedded credentials are rejected`() {
        val result = ServerUrlNormalizer.normalize("https://user:pass@cloud.example.com")
        assertTrue(result is ServerUrlNormalizer.Result.Error)
    }

    @Test
    fun `ipv4 host with port is accepted`() {
        val result = ServerUrlNormalizer.normalize("192.168.1.10:8080")
        assertTrue(result is ServerUrlNormalizer.Result.Ok)
        assertEquals("https://192.168.1.10:8080", (result as ServerUrlNormalizer.Result.Ok).server)
    }
}
