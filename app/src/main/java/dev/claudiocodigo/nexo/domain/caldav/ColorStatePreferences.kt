package dev.claudiocodigo.nexo.domain.caldav

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ColorStatePreferences {
    val validatedColors: Flow<Set<String>>
    val attentionColors: Flow<Set<String>>
    suspend fun addValidatedColor(colorHexOrName: String)
    suspend fun addAttentionColor(colorHexOrName: String)
    suspend fun removeColor(colorHexOrName: String)
}

class InMemoryColorStatePreferences(
    initialValidated: Set<String> = DEFAULT_VALIDATED_COLORS,
    initialAttention: Set<String> = DEFAULT_ATTENTION_COLORS
) : ColorStatePreferences {
    private var validated = initialValidated.toMutableSet()
    private var attention = initialAttention.toMutableSet()

    override val validatedColors: Flow<Set<String>> = flowOf(validated)
    override val attentionColors: Flow<Set<String>> = flowOf(attention)

    override suspend fun addValidatedColor(colorHexOrName: String) {
        validated.add(colorHexOrName.trim().uppercase())
    }

    override suspend fun addAttentionColor(colorHexOrName: String) {
        attention.add(colorHexOrName.trim().uppercase())
    }

    override suspend fun removeColor(colorHexOrName: String) {
        val norm = colorHexOrName.trim().uppercase()
        validated.remove(norm)
        attention.remove(norm)
    }

    companion object {
        val DEFAULT_VALIDATED_COLORS = setOf("008000", "228B22", "32CD32", "GREEN", "DARKOLIVEGREEN", "00FF00", "00A000")
        val DEFAULT_ATTENTION_COLORS = setOf("B22222", "RED", "FF0000", "D32F2F")
    }
}
