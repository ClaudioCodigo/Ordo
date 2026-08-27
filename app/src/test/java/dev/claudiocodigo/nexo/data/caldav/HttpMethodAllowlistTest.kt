package dev.claudiocodigo.nexo.data.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HttpMethodAllowlistTest {

    @Test
    fun `read methods are allowed`() {
        listOf("OPTIONS", "PROPFIND", "REPORT", "GET", "HEAD").forEach { method ->
            HttpMethodAllowlist.ensureAllowed(method)
        }
    }

    @Test
    fun `mutating methods are rejected`() {
        listOf("PUT", "POST", "PATCH", "DELETE", "PROPPATCH", "MKCALENDAR", "MOVE", "COPY").forEach { method ->
            assertThrows(DisallowedHttpMethodException::class.java) {
                HttpMethodAllowlist.ensureAllowed(method)
            }
        }
    }

    @Test
    fun `method comparison is case-insensitive`() {
        HttpMethodAllowlist.ensureAllowed("propfind")
        assertThrows(DisallowedHttpMethodException::class.java) {
            HttpMethodAllowlist.ensureAllowed("delete")
        }
    }

    @Test
    fun `rejected method message names the method`() {
        val ex = assertThrows(DisallowedHttpMethodException::class.java) {
            HttpMethodAllowlist.ensureAllowed("PUT")
        }
        assertEquals(true, ex.message!!.contains("PUT"))
    }
}
