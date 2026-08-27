package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

data class DateDivergenceCheck(
    val hasDivergence: Boolean,
    val textualDate: String?,
    val eventDate: String?,
    val message: String?
)

/**
 * Validation rules for structured service orders.
 *
 * Rules:
 * - Empty fields do NOT block local autosave, but DO block publication;
 * - Completion requires non-blank cause (for Diagnóstico) and non-blank solution;
 * - Divergence between execution date and event DTSTART generates an explicit warning.
 */
object ServiceOrderValidation {

    fun validateForPublication(order: StructuredServiceOrder): ValidationResult {
        if (order.title.isBlank()) return ValidationResult.Invalid("O título da OS é obrigatório.")
        if (order.clientName.isBlank()) return ValidationResult.Invalid("O cliente da OS é obrigatório.")
        if (order.unitName.isBlank()) return ValidationResult.Invalid("A unidade do cliente é obrigatória.")
        if (order.originalDemand.isBlank()) return ValidationResult.Invalid("A demanda/solicitação inicial é obrigatória.")
        return ValidationResult.Valid
    }

    fun validateForCompletion(order: StructuredServiceOrder): ValidationResult {
        val pubCheck = validateForPublication(order)
        if (pubCheck is ValidationResult.Invalid) return pubCheck

        if (order.preset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO) {
            if (order.closureCause.isNullOrBlank()) {
                return ValidationResult.Invalid("A causa identificada é obrigatória para finalizar a OS.")
            }
        }
        if (order.closureSolution.isNullOrBlank()) {
            return ValidationResult.Invalid("A solução ou ação executada é obrigatória para finalizar a OS.")
        }
        return ValidationResult.Valid
    }

    fun checkDateDivergence(executionDateMillis: Long, eventStartMillis: Long?): DateDivergenceCheck {
        if (eventStartMillis == null) return DateDivergenceCheck(false, null, null, null)

        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
        val textDate = formatter.format(Date(executionDateMillis))
        val eventDate = formatter.format(Date(eventStartMillis))

        val divergent = textDate != eventDate
        val message = if (divergent) {
            "A data informada ($textDate) difere da data agendada no calendário ($eventDate)."
        } else null

        return DateDivergenceCheck(
            hasDivergence = divergent,
            textualDate = textDate,
            eventDate = eventDate,
            message = message
        )
    }
}
