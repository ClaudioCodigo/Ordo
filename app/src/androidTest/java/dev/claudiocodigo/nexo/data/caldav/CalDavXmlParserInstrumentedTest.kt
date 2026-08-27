package dev.claudiocodigo.nexo.data.caldav

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalDavXmlParserInstrumentedTest {

    @Test
    fun parsesNamespaceAwareCalDavResponseOnAndroid() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:response>
                <d:href>/remote.php/dav/calendars/maria/trabalho/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><c:calendar/></d:resourcetype>
                    <d:displayname>Trabalho</d:displayname>
                    <c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val entry = CalDavXmlParser.parseMultistatus(xml).single()
        assertTrue(entry.isCalendar)
        assertEquals("Trabalho", entry.displayName)
        assertTrue("VEVENT" in entry.calendarComponents)
    }

    @Test
    fun rejectsDoctypeBeforeAndroidXmlProviderCanResolveEntities() {
        val xml = """
            <!DOCTYPE multistatus [<!ENTITY secret SYSTEM "file:///proc/self/maps">]>
            <d:multistatus xmlns:d="DAV:"><d:response><d:href>&secret;</d:href></d:response></d:multistatus>
        """.trimIndent()

        assertThrows(Exception::class.java) { CalDavXmlParser.parseMultistatus(xml) }
    }
}
