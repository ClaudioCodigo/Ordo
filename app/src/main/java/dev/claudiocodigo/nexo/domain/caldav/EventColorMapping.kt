package dev.claudiocodigo.nexo.domain.caldav

/**
 * Maps a raw `COLOR` value (RFC 7986 accepts a CSS3 color name or `#RRGGBB`)
 * to Nexo's semantics, per CAL-04 and Wave 7:
 *
 * - green/darkolivegreen/#008000/#228B22/#32CD32 -> [EventColor.VALIDADO]
 * - red/#B22222/#FF0000/#D32F2F -> [EventColor.REQUER_ATENCAO]
 * - anything else, neutral (#4682B4, #00679E), unmapped (#9370DB) or absent -> [EventColor.NAO_CLASSIFICADO]
 *
 * Precedence rule: If a color matches both sets, REQUER_ATENCAO takes precedence over VALIDADO.
 * Raw color string remains untouched and no remote mutation is emitted.
 */
object ColorClassifier {

    fun classify(
        raw: String?,
        customValidated: Set<String>? = null,
        customAttention: Set<String>? = null
    ): EventColor {
        val normalized = raw
            ?.trim()
            ?.lowercase()
            ?.removePrefix("#")
            ?.uppercase()
            ?: return EventColor.NAO_CLASSIFICADO

        val validatedSet = customValidated ?: InMemoryColorStatePreferences.DEFAULT_VALIDATED_COLORS
        val attentionSet = customAttention ?: InMemoryColorStatePreferences.DEFAULT_ATTENTION_COLORS

        // Red/Attention takes visual precedence
        return when {
            normalized in attentionSet -> EventColor.REQUER_ATENCAO
            normalized in validatedSet -> EventColor.VALIDADO
            else -> EventColor.NAO_CLASSIFICADO
        }
    }
}
