package dev.claudiocodigo.nexo.data.caldav

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavDiscoveryClient
import dev.claudiocodigo.nexo.domain.caldav.DiscoveryResult
import dev.claudiocodigo.nexo.domain.caldav.FailureKind
import dev.claudiocodigo.nexo.domain.caldav.Principal
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import javax.inject.Inject
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production CalDAV discovery client for Nextcloud.
 *
 * Resolves `/.well-known/caldav`, `current-user-principal`, `calendar-home-set`
 * and lists the user's calendars using the read-only [CalDavHttpClient]. Only
 * read methods are used; the type system cannot express a remote write here.
 */
class NextcloudCalDavDiscoveryClient @Inject constructor() : CalDavDiscoveryClient {

    override suspend fun discover(credentials: CalDavCredentials): DiscoveryResult =
        withContext(Dispatchers.IO) {
            val password = credentials.appPassword()
            val authHeader = basicAuth(credentials.user, password)
            password.fill('\u0000')
            val http = CalDavHttpClient().withAuthorization(authHeader, credentials.server)

            try {
                val base = resolveCalDavRoot(http, credentials.server)
                val principal = propfindPrincipal(http, base)
                val homeSet = propfindHomeSet(http, principal) ?: return@withContext DiscoveryResult.Failure(
                    FailureKind.PARSE,
                    "Servidor não informou calendar-home-set"
                )
                val calendars = propfindCalendars(http, homeSet)
                DiscoveryResult.Success(
                    principal = Principal(principal, homeSet),
                    calendars = calendars
                )
            } catch (e: CalDavDiscoveryException) {
                DiscoveryResult.Failure(e.kind, e.message ?: "Erro na descoberta de calendários")
            } catch (e: CalDavOriginException) {
                DiscoveryResult.Failure(FailureKind.REDIRECT_INSECURE, e.message ?: "Origem CalDAV insegura")
            } catch (e: Exception) {
                DiscoveryResult.Failure(mapWsException(e), e.message ?: "Erro de rede")
            } finally {
                credentials.wipe()
            }
        }

    private fun resolveCalDavRoot(http: CalDavHttpClient, server: String): String {
        val wellKnown = server.trimEnd('/') + "/.well-known/caldav"
        http.get(wellKnown).use { response ->
            if (response.isSuccessful || response.code in 300..399) {
                val final = response.request.url
                val finalUrl = final.toString()
                if (final.encodedPath.endsWith("/")) {
                    return finalUrl
                }
            }
        }
        // Nextcloud fallback: the well-known endpoint is not always present in a
        // subdirectory install, so the conventional CalDAV root is used.
        return server.trimEnd('/') + "/remote.php/dav/"
    }

    private fun propfindPrincipal(http: CalDavHttpClient, base: String): String {
        http.propFind(base, "0", PROP_PRINCIPAL).use { response ->
            val body = response.body?.string() ?: throw CalDavDiscoveryException(FailureKind.NETWORK, "Resposta vazia da descoberta")
            when (response.code) {
                200, 207 -> {
                    val principal = CalDavXmlParser.parseDiscovery(body).principal
                        ?: throw CalDavDiscoveryException(FailureKind.PARSE, "Servidor não informou current-user-principal")
                    return checkedHref(base, principal)
                }
                401 -> throw CalDavDiscoveryException(FailureKind.UNAUTHORIZED, "Autenticação falhou")
                403 -> throw CalDavDiscoveryException(FailureKind.FORBIDDEN, "Acesso negado")
                404 -> throw CalDavDiscoveryException(FailureKind.NOT_FOUND, "Descoberta não encontrada")
                else -> throw CalDavDiscoveryException(FailureKind.UNKNOWN, "HTTP ${response.code} na descoberta")
            }
        }
    }

    private fun propfindHomeSet(http: CalDavHttpClient, principal: String): String? {
        http.propFind(principal, "0", PROP_HOME_SET).use { response ->
            if (response.code !in (200..207)) {
                throw CalDavDiscoveryException(
                    mapHttpFailure(response.code),
                    "HTTP ${response.code} na descoberta do home-set"
                )
            }
            val body = response.body?.string() ?: return null
            val homeSet = CalDavXmlParser.parseDiscovery(body).calendarHomeSet ?: return null
            return checkedHref(principal, homeSet)
        }
    }

    private fun propfindCalendars(http: CalDavHttpClient, homeSet: String): List<dev.claudiocodigo.nexo.domain.caldav.CalendarInfo> {
        http.propFind(homeSet, "1", PROP_CALENDARS).use { response ->
            if (response.code !in (200..207)) {
                throw CalDavDiscoveryException(
                    mapHttpFailure(response.code),
                    "HTTP ${response.code} ao listar calendários"
                )
            }
            val body = response.body?.string() ?: return emptyList()
            val calendars = CalDavXmlParser.parseMultistatus(body)
                .mapNotNull { CalDavXmlParser.toCalendarInfo(it, homeSet) }
            calendars.forEach { checkedHref(homeSet, it.href) }
            return calendars
        }
    }

    private fun checkedHref(base: String, href: String): String {
        try {
            val resolved = CalDavXmlParser.resolveHref(base, href)
            val baseUri = java.net.URI(base)
            val targetUri = java.net.URI(resolved)
            val baseHost = baseUri.host ?: throw CalDavOriginException("Origem CalDAV sem host")
            val targetHost = targetUri.host ?: throw CalDavOriginException("URL CalDAV sem host")
            val baseScheme = baseUri.scheme ?: throw CalDavOriginException("Origem CalDAV sem esquema")
            val targetScheme = targetUri.scheme ?: throw CalDavOriginException("URL CalDAV sem esquema")
            val basePort = if (baseUri.port != -1) baseUri.port else if (baseScheme.equals("https", true)) 443 else 80
            val targetPort = if (targetUri.port != -1) targetUri.port else if (targetScheme.equals("https", true)) 443 else 80
            if (!targetScheme.equals(baseScheme, true) ||
                !targetHost.equals(baseHost, true) ||
                targetPort != basePort
            ) throw CalDavOriginException("URL CalDAV fora da origem configurada")
            return resolved
        } catch (e: CalDavOriginException) {
            throw e
        } catch (e: Exception) {
            throw CalDavOriginException("URL CalDAV inválida")
        }
    }

    private fun mapHttpFailure(code: Int): FailureKind = when (code) {
        401 -> FailureKind.UNAUTHORIZED
        403 -> FailureKind.FORBIDDEN
        404 -> FailureKind.NOT_FOUND
        else -> FailureKind.UNKNOWN
    }

    private fun mapWsException(e: Exception): FailureKind = when (e) {
        is UnknownHostException, is SocketTimeoutException, is ConnectException -> FailureKind.NETWORK
        is SSLException -> FailureKind.TLS_INVALID
        else -> FailureKind.UNKNOWN
    }

    private fun basicAuth(user: String, password: CharArray): String {
        val bytes = "$user:${String(password)}".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(bytes)
    }

    private class CalDavDiscoveryException(
        val kind: FailureKind,
        message: String
    ) : Exception(message)

    companion object {
        private val PROP_PRINCIPAL = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><d:current-user-principal/></d:prop>
            </d:propfind>
        """.trimIndent()

        private val PROP_HOME_SET = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
              <d:prop><c:calendar-home-set/></d:prop>
            </d:propfind>
        """.trimIndent()

        private val PROP_CALENDARS = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:a="http://apple.com/ns/ical/">
              <d:prop>
                <d:resourcetype/>
                <d:displayname/>
                <c:calendar-description/>
                <a:color/>
                <c:supported-calendar-component-set/>
                <d:current-user-privilege-set/>
                <d:sync-token/>
              </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}
