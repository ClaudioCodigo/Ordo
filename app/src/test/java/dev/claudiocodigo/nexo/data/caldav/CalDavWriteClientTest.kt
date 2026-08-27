package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.ConditionalCreate
import dev.claudiocodigo.nexo.domain.caldav.ConditionalUpdate
import dev.claudiocodigo.nexo.domain.caldav.WriteOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalDavWriteClientTest {

    private lateinit var server: MockWebServer
    private lateinit var writeClient: NextcloudCalDavWriteClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        writeClient = NextcloudCalDavWriteClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun creds() = CalDavCredentials(
        server = server.url("/").toString(),
        user = "maria",
        password = "secret".toCharArray()
    )

    @Test
    fun `create emits PUT with If-None-Match star and returns Created outcome`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"new-etag-1\""))

        val targetUrl = server.url("/cal/work/new-event.ics").toString()
        val request = ConditionalCreate(
            targetHref = targetUrl,
            uid = "new-event",
            icsPayload = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:new-event\nEND:VEVENT\nEND:VCALENDAR"
        )

        val outcome = writeClient.create(request, creds())

        assertTrue(outcome is WriteOutcome.Created)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("*", recorded.getHeader("If-None-Match"))
        assertEquals("\"new-etag-1\"", (outcome as WriteOutcome.Created).etag)
    }

    @Test
    fun `update emits PUT with If-Match etag and returns Updated outcome`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"updated-etag-2\""))

        val targetUrl = server.url("/cal/work/existing.ics").toString()
        val request = ConditionalUpdate(
            targetHref = targetUrl,
            uid = "existing",
            baseEtag = "\"base-etag-1\"",
            icsPayload = "BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\nUID:existing\nEND:VEVENT\nEND:VCALENDAR"
        )

        val outcome = writeClient.update(request, creds())

        assertTrue(outcome is WriteOutcome.Updated)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("\"base-etag-1\"", recorded.getHeader("If-Match"))
        assertEquals("\"updated-etag-2\"", (outcome as WriteOutcome.Updated).etag)
    }

    @Test
    fun `update on 412 Precondition Failed returns typed Conflict outcome`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(412).setBody("Precondition Failed"))

        val targetUrl = server.url("/cal/work/conflict.ics").toString()
        val request = ConditionalUpdate(
            targetHref = targetUrl,
            uid = "conflict",
            baseEtag = "\"stale-etag\"",
            icsPayload = "BEGIN:VCALENDAR\nEND:VCALENDAR"
        )

        val outcome = writeClient.update(request, creds())

        assertTrue(outcome is WriteOutcome.Conflict)
        val conflict = outcome as WriteOutcome.Conflict
        assertEquals(412, conflict.statusCode)
        assertEquals(targetUrl, conflict.href)
    }

    @Test
    fun `update on 403 Forbidden returns typed PermissionDenied outcome`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val targetUrl = server.url("/cal/work/readonly.ics").toString()
        val request = ConditionalUpdate(
            targetHref = targetUrl,
            uid = "readonly",
            baseEtag = "\"etag\"",
            icsPayload = "BEGIN:VCALENDAR\nEND:VCALENDAR"
        )

        val outcome = writeClient.update(request, creds())

        assertTrue(outcome is WriteOutcome.PermissionDenied)
        val denied = outcome as WriteOutcome.PermissionDenied
        assertEquals(403, denied.statusCode)
    }
}
