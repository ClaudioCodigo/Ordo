package dev.claudiocodigo.nexo.domain.caldav

import java.net.URI

/**
 * Normalizes and validates the Nextcloud server URL entered manually or via QR.
 *
 * Rules (AUT-03):
 * - a host without a scheme is assumed to be HTTPS;
 * - plain `http://` is rejected;
 * - embedded credentials in the URL are rejected;
 * - the host must not be blank and must look like a host, not a bare path.
 */
object ServerUrlNormalizer {

    sealed interface Result {
        data class Ok(val server: String, val host: String) : Result
        data class Error(val reason: String) : Result
    }

    fun normalize(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Error("Servidor não informado")

        val withScheme = if (!trimmed.contains("://")) "https://$trimmed" else trimmed

        val uri = runCatching { URI(withScheme) }.getOrNull()
            ?: return Result.Error("URL inválida")

        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") return Result.Error("Somente HTTPS é aceito")

        if (uri.host.isNullOrBlank()) return Result.Error("Host não informado")

        // Reject embedded userinfo (user@host or user:pass@host).
        if (uri.rawUserInfo != null) return Result.Error("Não use credenciais na URL")

        val host = uri.host
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val path = uri.rawPath?.takeUnless { it == "/" }?.trimEnd('/') ?: ""

        val normalized = if (path.isEmpty()) "https://$host$port" else "https://$host$port$path"
        return Result.Ok(server = normalized, host = host)
    }
}
