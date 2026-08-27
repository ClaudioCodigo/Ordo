package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavReadClient
import dev.claudiocodigo.nexo.domain.caldav.EventResource
import dev.claudiocodigo.nexo.domain.caldav.ResourceEtag
import dev.claudiocodigo.nexo.domain.caldav.ResourceListing
import dev.claudiocodigo.nexo.domain.caldav.SyncCollectionResult
import dev.claudiocodigo.nexo.domain.caldav.InvalidSyncTokenException
import dev.claudiocodigo.nexo.domain.caldav.SyncCollectionUnsupportedException
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Production read-only CalDAV client for a specific calendar collection. */
class NextcloudCalDavReadClient @Inject constructor() : CalDavReadClient {

    override suspend fun getSyncToken(calendarHref: String, credentials: CalDavCredentials): String? =
        withContext(Dispatchers.IO) {
            val http = authenticatedClient(credentials)
            http.propFind(calendarHref, "0", PROP_SYNC_TOKEN).use { response ->
                throwIfNotOk(response)
                val body = response.body?.string() ?: return@withContext null
                CalDavXmlParser.parseMultistatusResult(body).syncToken
            }
        }

    override suspend fun listHrefAndEtags(calendarHref: String, credentials: CalDavCredentials): List<ResourceEtag> =
        readListing(calendarHref, credentials, normalize = false).let {
            if (!it.complete) throw CalDavParseException(it.errorMessage ?: "Resposta CalDAV incompleta")
            it.resources
        }

    override suspend fun listHrefAndEtagsResult(calendarHref: String, credentials: CalDavCredentials): ResourceListing =
        readListing(calendarHref, credentials, normalize = true)

    private suspend fun readListing(
        calendarHref: String,
        credentials: CalDavCredentials,
        normalize: Boolean
    ): ResourceListing =
        withContext(Dispatchers.IO) {
            val http = authenticatedClient(credentials)
            http.propFind(calendarHref, "1", PROP_ETAGS).use { response ->
                throwIfNotOk(response)
                val body = response.body?.string()
                    ?: return@withContext ResourceListing(emptyList(), complete = false, errorMessage = "Resposta vazia")
                val parsed = runCatching { CalDavXmlParser.parseMultistatusResult(body) }.getOrElse {
                    return@withContext ResourceListing(emptyList(), complete = false, errorMessage = "Resposta XML inválida")
                }
                if (!parsed.wellFormedMultistatus) {
                    return@withContext ResourceListing(emptyList(), complete = false, errorMessage = "Resposta CalDAV inconclusiva")
                }
                val collectionHref = normalizedHref(calendarHref, calendarHref)
                val resources = parsed.entries
                    .filter { normalizedHref(calendarHref, it.href) != collectionHref }
                    .mapNotNull { entry ->
                        val etag = entry.properties[CalDavXmlParser.etagKey()]?.firstOrNull()
                        if (entry.href.isBlank()) null
                        else ResourceEtag(
                            href = if (normalize) normalizedHref(calendarHref, entry.href) else entry.href,
                            etag = etag
                        )
                    }
                val token = parsed.syncToken ?: parsed.entries.firstOrNull { normalizedHref(calendarHref, it.href) == collectionHref }
                    ?.properties?.get(CalDavXmlParser.syncTokenKey())?.firstOrNull()
                ResourceListing(resources, complete = true, syncToken = token)
            }
        }

    override suspend fun syncCollection(
        calendarHref: String,
        syncToken: String,
        credentials: CalDavCredentials
    ): SyncCollectionResult = withContext(Dispatchers.IO) {
        val http = authenticatedClient(credentials)
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token>${xmlEscape(syncToken)}</d:sync-token>
              <d:sync-level>1</d:sync-level>
              <d:prop><d:getetag/></d:prop>
            </d:sync-collection>
        """.trimIndent()
        http.report(calendarHref, body).use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401) throw CalDavHttpException(401, "Autenticação falhou")
            if (response.code == 403) {
                if (raw.contains("valid-sync-token", ignoreCase = true)) throw InvalidSyncTokenException()
                throw CalDavHttpException(403, "Acesso negado")
            }
            if (response.code in setOf(400, 404, 405, 501)) throw SyncCollectionUnsupportedException()
            if (response.code !in 200..207) throw CalDavHttpException(response.code, "HTTP ${response.code} na sincronização incremental")
            val parsed = runCatching { CalDavXmlParser.parseMultistatusResult(raw) }.getOrElse {
                throw CalDavParseException("Resposta XML inválida na sincronização incremental")
            }
            val token = parsed.syncToken
            if (!parsed.wellFormedMultistatus || token == null) {
                return@withContext SyncCollectionResult(token, emptyList(), emptySet(), false, "sync-token ausente ou resposta inconclusiva")
            }
            val changed = mutableListOf<ResourceEtag>()
            val removed = linkedSetOf<String>()
            parsed.entries.forEach { entry ->
                when (entry.statusCode) {
                    404 -> removed += normalizedHref(calendarHref, entry.href)
                    200, null -> {
                        val href = normalizedHref(calendarHref, entry.href)
                        if (href != normalizedHref(calendarHref, calendarHref)) {
                            changed += ResourceEtag(href, entry.properties[CalDavXmlParser.etagKey()]?.firstOrNull())
                        }
                    }
                    else -> throw CalDavParseException("Status HTTP inesperado na sincronização incremental")
                }
            }
            SyncCollectionResult(token, changed, removed, true)
        }
    }

    override suspend fun fetchEvents(
        calendarHref: String,
        hrefs: List<String>,
        credentials: CalDavCredentials
    ): List<EventResource> = withContext(Dispatchers.IO) {
        if (hrefs.isEmpty()) return@withContext emptyList()
        val http = authenticatedClient(credentials)
        val body = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?>""")
            append("""<c:calendar-multiget xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">""")
            append("""<d:prop><d:getetag/><c:calendar-data/></d:prop>""")
            hrefs.forEach { append("<d:href>${xmlEscape(it)}</d:href>") }
            append("</c:calendar-multiget>")
        }
        http.report(calendarHref, body).use { response ->
            throwIfNotOk(response)
            val body = response.body?.string() ?: return@withContext emptyList()
            CalDavXmlParser.parseMultistatus(body).mapNotNull { entry ->
                val ics = entry.properties[CalDavXmlParser.calendarDataKey()]?.firstOrNull()
                    ?: return@mapNotNull null
                val etag = entry.properties[CalDavXmlParser.etagKey()]?.firstOrNull()
                EventResource(href = entry.href, etag = etag, ics = ics)
            }
        }
    }

    private fun throwIfNotOk(response: okhttp3.Response) {
        if (response.code !in (200..207)) {
            response.close()
            throw CalDavHttpException(response.code, "HTTP ${response.code} na leitura CalDAV")
        }
    }

    private fun authenticatedClient(credentials: CalDavCredentials): CalDavHttpClient {
        val password = credentials.appPassword()
        val bytes = "${credentials.user}:${String(password)}".toByteArray(Charsets.UTF_8)
        password.fill('\u0000')
        return CalDavHttpClient().withAuthorization(
            "Basic " + Base64.getEncoder().encodeToString(bytes),
            credentials.server
        )
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun normalizedHref(calendarHref: String, href: String): String =
        CalDavXmlParser.resolveHref(calendarHref, href).also { resolved ->
            val base = java.net.URI(calendarHref)
            val target = java.net.URI(resolved)
            val basePort = if (base.port != -1) base.port else if (base.scheme.equals("https", true)) 443 else 80
            val targetPort = if (target.port != -1) target.port else if (target.scheme.equals("https", true)) 443 else 80
            if (base.host.isNullOrBlank() || target.host.isNullOrBlank() ||
                !base.scheme.equals(target.scheme, true) || !base.host.equals(target.host, true) || basePort != targetPort
            ) throw CalDavOriginException("URL CalDAV fora da origem configurada")
        }

    companion object {
        private val PROP_SYNC_TOKEN = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:sync-token/></d:prop></d:propfind>
        """.trimIndent()

        private val PROP_ETAGS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:"><d:prop><d:getetag/><d:sync-token/></d:prop></d:propfind>
        """.trimIndent()
    }
}
