package dev.claudiocodigo.nexo.data.caldav

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavHttpClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CalDavHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = CalDavHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/remote.php/dav").toString()

    @Test
    fun `propfind sends the PROPFIND method with depth header`() {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<multistatus/>"))

        val response = client.propFind(
            baseUrl(), "0",
            "<?xml version=\"1.0\"?><propfind/>"
        )

        assertEquals(207, response.code)
        response.close()

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("0", request.getHeader("Depth"))
    }

    @Test
    fun `report and get send their methods`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("text"))
        client.report(baseUrl(), "<calendar-multiget/>").close()
        assertEquals("REPORT", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(200).setBody("ics"))
        client.get(baseUrl()).close()
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `head and options are allowed`() {
        server.enqueue(MockResponse().setResponseCode(200))
        client.head(baseUrl()).close()
        assertEquals("HEAD", server.takeRequest().method)

        server.enqueue(MockResponse().setResponseCode(200))
        client.options(baseUrl()).close()
        assertEquals("OPTIONS", server.takeRequest().method)
    }

    @Test
    fun `a mutating request through the guarded client never reaches the server`() {
        // A client carrying the same guard interceptor must reject a PUT before
        // it is dispatched, leaving the server with zero recorded requests.
        val guarded = OkHttpClient.Builder()
            .addInterceptor(HttpMethodGuardInterceptor())
            .build()

        val put = Request.Builder()
            .url(server.url("/cal"))
            .put("".toRequestBody())
            .build()

        assertThrows(DisallowedHttpMethodException::class.java) {
            guarded.newCall(put).execute()
        }

        assertTrue(server.requestCount == 0)
    }

    @Test
    fun `redirect to another origin is rejected before credentials leave the device`() {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", other.url("/stolen").toString())
            )
            val authenticated = CalDavHttpClient()
                .withAuthorization("Basic dXNlcjpzZWNyZXQ=", server.url("/").toString())

            assertThrows(CalDavOriginException::class.java) {
                authenticated.get(server.url("/start").toString()).close()
            }
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }
}
