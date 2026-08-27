package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextcloudCalDavReadClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NextcloudCalDavReadClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = NextcloudCalDavReadClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun creds() = CalDavCredentials(server.url("/").toString(), "maria", "secret".toCharArray())

    private fun etagsXml() = """
        <D:multistatus xmlns:D="DAV:">
          <D:response><D:href>/cal/e1.ics</D:href>
            <D:propstat><D:prop><D:getetag>"1"</D:getetag></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
          <D:response><D:href>/cal/e2.ics</D:href>
            <D:propstat><D:prop><D:getetag>"2"</D:getetag></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun multigetXml() = """
        <D:multistatus xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
          <D:response><D:href>/cal/e1.ics</D:href>
            <D:propstat><D:prop><D:getetag>"1"</D:getetag><C:calendar-data>BEGIN:VCALENDAR&#10;VERSION:2.0&#10;BEGIN:VEVENT&#10;UID:e1&#10;END:VEVENT&#10;END:VCALENDAR</C:calendar-data></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun depthOneWithCollectionXml() = """
        <D:multistatus xmlns:D="DAV:">
          <D:response><D:href>/cal/</D:href>
            <D:propstat><D:prop><D:resourcetype/></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
          <D:response><D:href>/cal/e1.ics</D:href>
            <D:propstat><D:prop><D:getetag>"1"</D:getetag></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    private fun depthOneWithCollectionTokenXml() = """
        <D:multistatus xmlns:D="DAV:">
          <D:response><D:href>/cal/</D:href>
            <D:propstat><D:prop><D:resourcetype/><D:getetag>"collection"</D:getetag>
                <D:sync-token>collection-token</D:sync-token></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
          <D:response><D:href>/cal/e1.ics</D:href>
            <D:propstat><D:prop><D:getetag>"1"</D:getetag></D:prop>
              <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
          </D:response>
        </D:multistatus>
    """.trimIndent()

    @Test
    fun `lists href and etag pairs`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(etagsXml()))
        val result = client.listHrefAndEtags(server.url("/cal/").toString(), creds())
        assertEquals(2, result.size)
        assertEquals("/cal/e1.ics", result[0].href)
        assertEquals("\"1\"", result[0].etag)
    }

    @Test
    fun `fetches event resources via multiget`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(multigetXml()))
        val result = client.fetchEvents(server.url("/cal/").toString(), listOf("/cal/e1.ics"), creds())
        assertEquals(1, result.size)
        assertEquals("/cal/e1.ics", result[0].href)
        assertTrue(result[0].ics.contains("UID:e1"))
    }

    @Test
    fun `depth one listing excludes collection when collection href is relative`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(depthOneWithCollectionXml()))
        val result = client.listHrefAndEtagsResult(server.url("/cal/").toString(), creds())
        assertTrue(result.complete)
        assertEquals(1, result.resources.size)
        assertEquals(server.url("/cal/e1.ics").toString(), result.resources.single().href)
    }

    @Test
    fun `first depth listing reads sync token from collection propstat`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(depthOneWithCollectionTokenXml()))
        val result = client.listHrefAndEtagsResult(server.url("/cal/").toString(), creds())
        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()

        assertEquals("collection-token", result.syncToken)
        assertTrue(requestBody.contains("<d:getetag/>"))
        assertTrue(requestBody.contains("<d:sync-token/>"))
    }

    @Test
    fun `throws a CalDavHttpException on 401`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(401).setBody("unauth"))
            assertThrows(CalDavHttpException::class.java) {
                runBlocking { client.listHrefAndEtags(server.url("/cal/").toString(), creds()) }
            }
        }
    }

    @Test
    fun `read client never issues a mutating request`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody(etagsXml()))
        client.listHrefAndEtags(server.url("/cal/").toString(), creds())

        val mutating = setOf("PUT", "POST", "PATCH", "DELETE", "PROPPATCH", "MKCALENDAR", "MOVE", "COPY")
        val methods = (0 until server.requestCount).map { server.takeRequest().method }
        assertTrue(methods.none { it in mutating })
        assertTrue(methods.all { it == "PROPFIND" || it == "REPORT" || it == "GET" })
    }

    @Test
    fun `sync collection sends token and returns changed and deleted resources`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("""
            <D:multistatus xmlns:D="DAV:">
              <D:sync-token>next</D:sync-token>
              <D:response><D:href>/cal/e1.ics</D:href><D:propstat><D:prop><D:getetag>"2"</D:getetag></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat><D:status>HTTP/1.1 200 OK</D:status></D:response>
              <D:response><D:href>/cal/e2.ics</D:href><D:status>HTTP/1.1 404 Not Found</D:status></D:response>
            </D:multistatus>
        """.trimIndent()))
        val result = client.syncCollection(server.url("/cal/").toString(), "old", creds())
        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertTrue(request.body.readUtf8().contains("<d:sync-token>old</d:sync-token>"))
        assertEquals("next", result.newToken)
        assertEquals(listOf(server.url("/cal/e1.ics").toString()), result.changed.map { it.href })
        assertEquals(setOf(server.url("/cal/e2.ics").toString()), result.removed)
    }

    @Test
    fun `valid sync token error is distinct from unauthorized`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<D:error xmlns:D=\"DAV:\"><D:valid-sync-token/></D:error>"))
        assertThrows(dev.claudiocodigo.nexo.domain.caldav.InvalidSyncTokenException::class.java) {
            runBlocking { client.syncCollection(server.url("/cal/").toString(), "old", creds()) }
        }
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows(CalDavHttpException::class.java) {
            runBlocking { client.syncCollection(server.url("/cal/").toString(), "old", creds()) }
        }
    }

    @Test
    fun `cross origin href in delta is rejected`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("""
            <D:multistatus xmlns:D="DAV:"><D:sync-token>next</D:sync-token>
              <D:response><D:href>https://other.example/e.ics</D:href><D:status>HTTP/1.1 200 OK</D:status></D:response>
            </D:multistatus>
        """.trimIndent()))
        assertThrows(CalDavOriginException::class.java) {
            runBlocking { client.syncCollection(server.url("/cal/").toString(), "old", creds()) }
        }
    }
}
