package dev.claudiocodigo.nexo.data.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CalDavXmlParserTest {

    private val base = "https://cloud.example.com/remote.php/dav/"

    @Test
    fun `parses a minimal namespace aware multistatus without optional JAXP features`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:response>
                <D:href>/calendars/maria/trabalho/</D:href>
                <D:propstat>
                  <D:prop><D:resourcetype><C:calendar/></D:resourcetype></D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val entry = CalDavXmlParser.parseMultistatus(xml).single()
        assertEquals("/calendars/maria/trabalho/", entry.href)
        assertTrue(entry.isCalendar)
    }

    @Test
    fun `parses a calendar entry resolving href and mapping props`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">
              <d:response>
                <d:href>/remote.php/dav/calendars/maria/trabalho/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><c:calendar/></d:resourcetype>
                    <d:displayname>Trabalho</d:displayname>
                    <c:calendar-description>Agenda de trabalho</c:calendar-description>
                    <a:color>#B22222</a:color>
                    <c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set>
                    <d:current-user-privilege-set><d:privilege><d:write/></d:privilege><d:privilege><d:read/></d:privilege></d:current-user-privilege-set>
                    <d:sync-token>tok-123</d:sync-token>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entries = CalDavXmlParser.parseMultistatus(xml)
        assertEquals(1, entries.size)
        val entry = entries[0]

        assertTrue(entry.isCalendar)
        assertEquals("Trabalho", entry.displayName)
        assertEquals("Agenda de trabalho", entry.description)
        assertEquals("#B22222", entry.color)
        assertTrue("VEVENT" in entry.calendarComponents)
        assertTrue("write" in entry.privileges)
        assertEquals("tok-123", entry.syncToken)

        val info = CalDavXmlParser.toCalendarInfo(entry, base)!!
        assertEquals("https://cloud.example.com/remote.php/dav/calendars/maria/trabalho/", info.href)
        assertEquals("#B22222", info.color)
        assertEquals(true, info.hasWritePrivilege)
        assertEquals("tok-123", info.syncToken)
    }

    @Test
    fun `filters out a calendar that does not support VEVENT`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:response>
                <d:href>/cal/tasks/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><c:calendar/></d:resourcetype>
                    <c:supported-calendar-component-set><c:comp name="VTODO"/></c:supported-calendar-component-set>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val info = CalDavXmlParser.toCalendarInfo(CalDavXmlParser.parseMultistatus(xml)[0], base)
        assertNull(info)
    }

    @Test
    fun `parses discovery principal and home-set from a multistatus`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:response>
                <D:href>/remote.php/dav/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:current-user-principal><D:href>/remote.php/dav/principals/users/maria/</D:href></D:current-user-principal>
                    <C:calendar-home-set><D:href>/remote.php/dav/calendars/maria/</D:href></C:calendar-home-set>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val discovery = CalDavXmlParser.parseDiscovery(xml)
        assertEquals("/remote.php/dav/principals/users/maria/", discovery.principal)
        assertEquals("/remote.php/dav/calendars/maria/", discovery.calendarHomeSet)
    }

    @Test
    fun `resolves a relative href to the base origin`() {
        assertEquals(
            "https://cloud.example.com/remote.php/dav/calendars/x/",
            CalDavXmlParser.resolveHref("https://cloud.example.com/remote.php/dav/", "/remote.php/dav/calendars/x/")
        )
    }

    @Test
    fun `ignores properties reported with HTTP 404`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:response><d:href>/cal/x.ics</d:href>
                <d:propstat><d:prop><d:getetag>bad</d:getetag><d:displayname>bad</d:displayname></d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
                <d:propstat><d:prop><d:getetag>good</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val entry = CalDavXmlParser.parseMultistatus(xml).single()
        assertEquals("good", entry.properties[CalDavXmlParser.etagKey()]?.single())
        assertNull(entry.displayName)
    }

    @Test
    fun `rejects doctype and external entities`() {
        val xml = """
            <!DOCTYPE multistatus [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <d:multistatus xmlns:d="DAV:"><d:response><d:href>&secret;</d:href></d:response></d:multistatus>
        """.trimIndent()
        assertThrows(Exception::class.java) { CalDavXmlParser.parseMultistatus(xml) }
    }

    @Test
    fun `captures root sync token and direct response statuses`() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:sync-token>next-token</d:sync-token>
              <d:response><d:href>/cal/changed.ics</d:href>
                <d:propstat><d:prop><d:getetag>"2"</d:getetag></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                <d:status>HTTP/1.1 200 OK</d:status>
              </d:response>
              <d:response><d:href>/cal/deleted.ics</d:href>
                <d:status>HTTP/1.1 404 Not Found</d:status>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val parsed = CalDavXmlParser.parseMultistatusResult(xml)
        assertTrue(parsed.wellFormedMultistatus)
        assertEquals("next-token", parsed.syncToken)
        assertEquals(listOf(200, 404), parsed.entries.map { it.statusCode })
    }
}
