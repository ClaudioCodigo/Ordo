package dev.claudiocodigo.nexo.domain.caldav

/**
 * Parsed Nextcloud login QR payload.
 *
 * The application password is held as a [CharArray] so callers can wipe it
 * from memory once imported. It must never be persisted, logged or shown again.
 */
class NextcloudQrData(
    val server: String,
    val user: String,
    password: String
) {
    private val held = password.toCharArray()

    /** A copy of the application password; callers must not persist or log it. */
    fun password(): CharArray = held.copyOf()

    fun wipe() {
        held.fill('\u0000')
    }
}

/**
 * Parses the Nexo Nextcloud login QR payload.
 *
 * Observed format: `nc://login/user:<login>&password:<appPassword>&server:<server>`
 *
 * The parser is intentionally order-independent, supports percent-encoding and
 * rejects malformed or missing pieces. It does NOT validate the server URL
 * (that is [ServerUrlNormalizer]'s job) and never logs the password.
 */
object NextcloudQrParser {

    private const val SCHEME = "nc://"

    /** @throws IllegalArgumentException when the payload is not a valid Nexo login QR. */
    fun parse(uri: String): NextcloudQrData {
        val trimmed = uri.trim()
        require(trimmed.startsWith(SCHEME, ignoreCase = true)) {
            "Formato de QR não reconhecido (esperado nc://login/...)"
        }
        val afterScheme = trimmed.substring(SCHEME.length)

        val slash = afterScheme.indexOf('/')
        require(slash > 0) { "QR sem payload" }

        // The authority after `nc://` reads `login`; treat any non-empty value
        // leniently but require a payload path.
        val path = afterScheme.substring(slash + 1)
        require(path.isNotBlank()) { "QR sem credenciais" }

        val pairs = linkedMapOf<String, String>()
        for (segment in path.split('&')) {
            require(segment.isNotBlank()) { "Segmento vazio no QR" }
            val colon = segment.indexOf(':')
            require(colon > 0) { "Segmento sem ':' no QR" }
            val key = segment.substring(0, colon)
            val value = segment.substring(colon + 1)
            require(key.isNotBlank()) { "Chave vazia no QR" }
            pairs[key] = decodePercent(value)
        }

        val user = pairs["user"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("QR sem usuário")
        val password = pairs["password"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("QR sem senha de aplicativo")
        val server = pairs["server"]?.takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("QR sem servidor")

        return NextcloudQrData(server = server, user = user, password = password)
    }

    /** Decodes `%XX` escapes without treating `+` as a space (passwords may contain `+`). */
    private fun decodePercent(value: String): String {
        if (value.indexOf('%') < 0) return value
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 3 <= value.length) {
                val hex = value.substring(i + 1, i + 3)
                val byte = hex.toIntOrNull(16)
                if (byte != null) {
                    bytes.write(byte)
                    i += 3
                    continue
                }
            }
            bytes.write(c.code)
            i++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }
}
