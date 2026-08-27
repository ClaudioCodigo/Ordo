package dev.claudiocodigo.nexo.domain.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudQrParserTest {

    @Test
    fun `parses a full payload with percent encoding`() {
        val data = NextcloudQrParser.parse(
            "nc://login/user:john%40example.com&password:p%40ss%2Bword&server:https://cloud.example.com"
        )
        assertEquals("john@example.com", data.user)
        assertEquals("p@ss+word", String(data.password()))
        assertEquals("https://cloud.example.com", data.server)
        data.wipe()
    }

    @Test
    fun `payload parsing is order-independent`() {
        val data = NextcloudQrParser.parse(
            "nc://login/server:https://s.example.com&password:abc&user:maria"
        )
        assertEquals("maria", data.user)
        assertEquals("abc", String(data.password()))
        assertEquals("https://s.example.com", data.server)
        data.wipe()
    }

    @Test
    fun `a plus sign is preserved in the password`() {
        val data = NextcloudQrParser.parse("nc://login/user:u&password:a+b&server:https://s")
        assertEquals("a+b", String(data.password()))
        data.wipe()
    }

    @Test
    fun `missing delimiters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("nc://login/usuariosemdelemiter")
        }
    }

    @Test
    fun `missing required fields are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("nc://login/user:u&server:https://s")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("nc://login/user:u&password:p")
        }
    }

    @Test
    fun `wrong scheme and blank payload are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("https://example.com/x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("nc://login/")
        }
    }

    @Test
    fun `blank payload fields are not accepted`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            NextcloudQrParser.parse("nc://login/user:&password:p&server:https://s")
        }
        assertTrue(ex.message!!.contains("usuário"))
    }
}
