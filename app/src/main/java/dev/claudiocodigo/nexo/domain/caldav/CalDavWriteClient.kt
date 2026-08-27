package dev.claudiocodigo.nexo.domain.caldav

/**
 * Request to create a new provisional calendar event on the CalDAV server.
 *
 * Emits HTTP PUT with 'If-None-Match: *' to guarantee that an existing resource
 * is never overwritten.
 */
data class ConditionalCreate(
    val targetHref: String,
    val uid: String,
    val icsPayload: String
) {
    init {
        require(targetHref.isNotBlank()) { "targetHref não pode ser vazio" }
        require(uid.isNotBlank()) { "uid não pode ser vazio" }
        require(icsPayload.isNotBlank()) { "icsPayload não pode ser vazio" }
    }
}

/**
 * Request to update an existing calendar event on the CalDAV server.
 *
 * Emits HTTP PUT with 'If-Match: "<baseEtag>"'. If the remote resource was modified
 * concurrently, the server returns HTTP 412 (Precondition Failed).
 */
data class ConditionalUpdate(
    val targetHref: String,
    val uid: String,
    val baseEtag: String,
    val icsPayload: String
) {
    init {
        require(targetHref.isNotBlank()) { "targetHref não pode ser vazio" }
        require(uid.isNotBlank()) { "uid não pode ser vazio" }
        require(baseEtag.isNotBlank()) { "baseEtag não pode ser vazio (If-Match obrigatório)" }
        require(icsPayload.isNotBlank()) { "icsPayload não pode ser vazio" }
    }
}

/**
 * Typed outcomes of a conditional write attempt against the CalDAV server.
 */
sealed interface WriteOutcome {
    data class Created(val href: String, val etag: String?) : WriteOutcome
    data class Updated(val href: String, val etag: String?) : WriteOutcome
    data class Conflict(val href: String, val statusCode: Int = 412, val message: String = "Conflito: recurso modificado no servidor") : WriteOutcome
    data class PermissionDenied(val href: String, val statusCode: Int, val message: String) : WriteOutcome
    data class TransientFailure(val href: String, val message: String, val cause: Throwable? = null) : WriteOutcome
    data class PermanentFailure(val href: String, val statusCode: Int, val message: String) : WriteOutcome
}

/**
 * Narrow, conditional CalDAV writer interface.
 *
 * Exposes strictly conditional create and update operations. It does not expose
 * generic HTTP methods, DELETE, color mutation or unconditional writes.
 */
interface CalDavWriteClient {
    suspend fun create(request: ConditionalCreate, credentials: CalDavCredentials): WriteOutcome
    suspend fun update(request: ConditionalUpdate, credentials: CalDavCredentials): WriteOutcome
}
