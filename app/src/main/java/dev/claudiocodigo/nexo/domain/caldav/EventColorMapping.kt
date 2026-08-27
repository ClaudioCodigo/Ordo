package dev.claudiocodigo.nexo.domain.caldav

/**
 * Maps a raw `COLOR` value (RFC 7986 accepts a CSS3 color name or `#RRGGBB`)
 * to Nexo's semantics, per CAL-04:
 *
 * - green `#008000` -> [EventColor.VALIDADO]
 * - red   `#B22222` -> [EventColor.REQUER_ATENCAO]
 * - anything else (or absent) -> [EventColor.NAO_CLASSIFICADO]
 *
 * The app never flips a color to green on its own; it only interprets the
 * signal the technical control put on the server. This is deliberately
 * conservative: only the two documented signals are classified.
 */
object ColorClassifier {

    fun classify(raw: String?): EventColor {
        val normalized = raw
            ?.trim()
            ?.lowercase()
            ?.removePrefix("#")
            ?.uppercase()
            ?: return EventColor.NAO_CLASSIFICADO

        return when {
            normalized in GREEN_VARIANTS -> EventColor.VALIDADO
            normalized in RED_VARIANTS -> EventColor.REQUER_ATENCAO
            else -> EventColor.NAO_CLASSIFICADO
        }
    }
}

private val GREEN_VARIANTS = setOf("008000", "GREEN", "00FF00", "00A000")
private val RED_VARIANTS = setOf("B22222", "RED", "FF0000", "D32F2F")
