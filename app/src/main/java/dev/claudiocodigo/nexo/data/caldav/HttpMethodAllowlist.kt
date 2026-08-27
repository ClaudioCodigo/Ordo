package dev.claudiocodigo.nexo.data.caldav

/**
 * The only HTTP methods the Nexo CalDAV layer is allowed to use.
 *
 * Every other method (PUT, POST, PATCH, DELETE, PROPPATCH, MKCALENDAR, MOVE,
 * COPY, ...) is a remote write and must fail before it leaves the device.
 */
object HttpMethodAllowlist {

    /** Read-only methods allowed by the Fase 2 policy. */
    val ALLOWED: Set<String> = setOf("OPTIONS", "PROPFIND", "REPORT", "GET", "HEAD")

    /**
     * Throws [DisallowedHttpMethodException] when [method] is not allowed.
     *
     * This is invoked both by the typed client methods and by an OkHttp
     * interceptor (defense in depth), so a mutating request can never reach the
     * network.
     */
    fun ensureAllowed(method: String) {
        if (method.uppercase() !in ALLOWED) {
            throw DisallowedHttpMethodException(method)
        }
    }
}

/** Raised when a mutating HTTP method is attempted against the read-only client. */
class DisallowedHttpMethodException(method: String) :
    SecurityException("HTTP method '$method' is not allowed by the Nexo read-only CalDAV policy")

/**
 * OkHttp interceptor that re-validates every request method. It is defense in
 * depth: even if future code builds a mutating request through the raw client,
 * the interceptor stops it before any bytes leave the device.
 */
class HttpMethodGuardInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        HttpMethodAllowlist.ensureAllowed(chain.request().method)
        return chain.proceed(chain.request())
    }
}
