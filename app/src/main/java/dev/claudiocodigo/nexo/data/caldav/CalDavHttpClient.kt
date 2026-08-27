package dev.claudiocodigo.nexo.data.caldav

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import java.net.URI

/**
 * Thin OkHttp wrapper that exposes only the WebDAV read methods allowed by the
 * Fase 2 policy: OPTIONS, PROPFIND, REPORT, GET and HEAD.
 *
 * There is no generic `execute(method = ...)` entry point, and an OkHttp
 * interceptor re-validates every request as defense in depth. A future caller
 * that tries to send a mutating method through the underlying client is
 * rejected before any bytes leave the device.
 *
 * An optional `Authorization` header (HTTP Basic) is attached only after the
 * request origin has been checked. Redirects are followed manually and only
 * when the target remains the same HTTPS origin.
 */
class CalDavHttpClient private constructor(
    private val client: OkHttpClient,
    private val authorization: String?,
    private val expectedOrigin: Origin?
) {
    constructor() : this(defaultClient(), null, null)

    constructor(authorization: String) : this(defaultClient(), authorization, null)

    /** Returns a client that attaches [authorization] to each read request. */
    fun withAuthorization(authorization: String, expectedOrigin: String? = null): CalDavHttpClient =
        CalDavHttpClient(client, authorization, expectedOrigin?.let(::originOf))

    /** Sends a PROPFIND with [depth] and an XML body. */
    fun propFind(url: String, depth: String, body: String): Response =
        execute(
            Request.Builder()
                .url(url)
                .method("PROPFIND", body.toRequestBody(XML_MEDIA_TYPE))
                .header("Depth", depth)
                .build()
        )

    /** Sends a REPORT (calendar-multiget / sync-collection) with an XML body. */
    fun report(url: String, body: String): Response =
        execute(
            Request.Builder()
                .url(url)
                .method("REPORT", body.toRequestBody(XML_MEDIA_TYPE))
                .build()
        )

    /** Sends a GET (used to fetch a calendaring resource). */
    fun get(url: String): Response =
        execute(Request.Builder().url(url).get().build())

    /** Sends a HEAD. */
    fun head(url: String): Response =
        execute(Request.Builder().url(url).head().build())

    /** Sends OPTIONS (used to probe server capabilities). */
    fun options(url: String): Response =
        execute(Request.Builder().url(url).method("OPTIONS", null).build())

    private fun execute(request: Request): Response {
        var next = request
        repeat(MAX_REDIRECTS + 1) { hop ->
            val built = authorize(next)
            expectedOrigin?.let { expected ->
                val actual = Origin(next.url.scheme, next.url.host, next.url.port)
                if (actual != expected) {
                    throw CalDavOriginException("URL CalDAV fora da origem configurada")
                }
            }
            HttpMethodAllowlist.ensureAllowed(built.method)
            val response = client.newCall(built).execute()
            if (response.code !in 300..399 || expectedOrigin == null) return response

            val location = response.header("Location")
            val target = location?.let { runCatching { next.url.resolve(it) }.getOrNull() }
            val targetOrigin = target?.let { Origin(it.scheme, it.host, it.port) }
            if (target == null || targetOrigin != expectedOrigin || target.scheme != "https") {
                response.close()
                throw CalDavOriginException("Redirecionamento CalDAV inseguro")
            }
            if (hop == MAX_REDIRECTS) {
                response.close()
                throw CalDavOriginException("Redirecionamento CalDAV excedeu o limite")
            }
            response.close()
            next = next.newBuilder().url(target).build()
        }
        error("unreachable")
    }

    private fun authorize(request: Request): Request {
        val header = authorization ?: return request
        return request.newBuilder().header("Authorization", header).build()
    }

    companion object {
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private const val MAX_REDIRECTS = 5

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpMethodGuardInterceptor())
            .build()

        private fun originOf(value: String): Origin {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase() ?: throw CalDavOriginException("Origem inválida")
            val host = uri.host?.lowercase() ?: throw CalDavOriginException("Origem inválida")
            val port = when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                scheme == "http" -> 80
                else -> -1
            }
            return Origin(scheme, host, port)
        }
    }

    private data class Origin(val scheme: String, val host: String, val port: Int)
}

/** Raised before credentials can be sent to an unexpected origin. */
class CalDavOriginException(message: String) : Exception(message)
