package dev.claudiocodigo.nexo.data.caldav

/** Raised when a CalDAV read request returns an unexpected HTTP status. */
class CalDavHttpException(val statusCode: Int, message: String) : Exception(message)

/** Raised when a CalDAV response cannot be trusted as a complete listing. */
class CalDavParseException(message: String) : Exception(message)
