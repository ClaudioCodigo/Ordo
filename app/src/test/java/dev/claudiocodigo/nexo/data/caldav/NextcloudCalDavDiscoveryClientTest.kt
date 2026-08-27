package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.DiscoveryResult
import dev.claudiocodigo.nexo.domain.caldav.FailureKind
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextcloudCalDavDiscoveryClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NextcloudCalDavDiscoveryClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NextcloudCalDavDiscoveryClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serverBase(): String = server.url("/").toString()

    private fun credentials() = CalDavCredentials(serverBase(), "maria", "secret".toCharArray())

    private fun principalXml() = """
        <D:multistatus xmlns:D="DAV:">
          <D:response>
            <D:href>/remote.php/dav/</D:href>
            <D:propstat><D:prop>
              <D:current-user-principal><D:href>/remote.php/dav/principals/users/maria/</D:href></D:current-user-principal>
            </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun homeSetXml() = """
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
          <D:response>
            <D:href>/remote.php/dav/principals/users/maria/</D:href>
            <D:propstat><D:prop>
              <C:calendar-home-set><D:href>/remote.php/dav/calendars/maria/</D:href></C:calendar-home-set>
            </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun calendarsXml() = """
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:A="http://apple.com/ns/ical/">
          <D:response>
            <D:href>/remote.php/dav/calendars/maria/trabalho/</D:href>
            <D:propstat><D:prop>
              <D:resourcetype><C:calendar/></D:resourcetype>
              <D:displayname>Trabalho</D:displayname>
              <A:color>#B22222</A:color>
              <C:supported-calendar-component-set><C:comp name="VEVENT"/></C:supported-calendar-component-set>
              <D:current-user-privilege-set><D:privilege><D:write/></D:privilege></D:current-user-privilege-set>
            </D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `discovers calendars and never issues a mutating request`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<ok/>"))
        server.enqueue(MockResponse().setResponseCode(207).setBody(principalXml()))
        server.enqueue(MockResponse().setResponseCode(207).setBody(homeSetXml()))
        server.enqueue(MockResponse().setResponseCode(207).setBody(calendarsXml()))

        val result = client.discover(credentials())

        assertTrue(result is DiscoveryResult.Success)
        result as DiscoveryResult.Success
        assertEquals(1, result.calendars.size)
        assertEquals("Trabalho", result.calendars[0].displayName)
        assertEquals(true, result.calendars[0].hasWritePrivilege)

        // Every request must be a read method (PROPFIND/GET); zero writes.
        val mutating = setOf("PUT", "POST", "PATCH", "DELETE", "PROPPATCH", "MKCALENDAR", "MOVE", "COPY")
        val methods = (0 until server.requestCount).map { server.takeRequest().method }
        assertTrue("Unexpected mutating methods: $methods", methods.none { it in mutating })
    }

    @Test
    fun `maps 401 to an authorization failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<ok/>"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        val result = client.discover(credentials())

        assertTrue(result is DiscoveryResult.Failure)
        assertEquals(FailureKind.UNAUTHORIZED, (result as DiscoveryResult.Failure).kind)
    }

    @Test
    fun `a non-calendar collection is filtered out`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<ok/>"))
        server.enqueue(MockResponse().setResponseCode(207).setBody(principalXml()))
        server.enqueue(MockResponse().setResponseCode(207).setBody(homeSetXml()))
        val onlyTodo = calendarsXml().replace("<C:comp name=\"VEVENT\"/>", "<C:comp name=\"VTODO\"/>")
        server.enqueue(MockResponse().setResponseCode(207).setBody(onlyTodo))

        val result = client.discover(credentials()) as DiscoveryResult.Success
        assertEquals(0, result.calendars.size)
    }
}
