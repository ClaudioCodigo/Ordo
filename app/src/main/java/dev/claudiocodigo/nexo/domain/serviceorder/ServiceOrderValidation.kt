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

data class ScheduleValidation(
    val isValid: Boolean,
    val warning: String? = null,
    val error: String? = null
)

/**
 * Validation rules for structured service orders.
 *
 * Rules:
 * - Empty fields do NOT block local autosave, but DO block publication;
 * - Resolution requires non-blank cause and solution; Request requires a non-blank action;
 * - Divergence between execution date and event DTSTART generates an explicit warning.
 */
object ServiceOrderValidation {

    fun validateForPublication(order: StructuredServiceOrder): ValidationResult {
        if (order.title.isBlank()) return ValidationResult.Invalid("O título da OS é obrigatório.")
        if (order.clientName.isBlank()) return ValidationResult.Invalid("O cliente da OS é obrigatório.")
        if (order.unitName.isBlank()) return ValidationResult.Invalid("A unidade do cliente é obrigatória.")
        if (order.occurrenceKey == null && order.technician.isNullOrBlank()) return ValidationResult.Invalid("Informe o técnico da OS.")
        if (order.occurrenceKey == null && order.category.isNullOrBlank()) return ValidationResult.Invalid("Informe a categoria da OS.")
        if (order.originalDemand.isBlank()) return ValidationResult.Invalid("A demanda/solicitação inicial é obrigatória.")
        if (order.occurrenceKey == null) {
            val schedule = validateSchedule(order.scheduledStart, order.scheduledEnd, order.allDay)
            if (!schedule.isValid) return ValidationResult.Invalid(schedule.error!!)
        }
        return ValidationResult.Valid
    }

    fun validateForCompletion(order: StructuredServiceOrder): ValidationResult {
        val pubCheck = validateForPublication(order)
        if (pubCheck is ValidationResult.Invalid) return pubCheck
        if (order.conclusionState == ConclusionState.NAO_DEFINIDO) {
            return ValidationResult.Invalid("Informe o parecer técnico antes de finalizar a OS.")
        }

        if (order.occurrenceKey == null) {
            val schedule = validateSchedule(order.scheduledStart, order.scheduledEnd, order.allDay)
            if (!schedule.isValid) return ValidationResult.Invalid(schedule.error!!)
        }

        if (order.technicalOpinion == TechnicalOpinion.NOT_CONCLUDED || order.conclusionState == ConclusionState.NAO_CONCLUIDO) {
            return ValidationResult.Valid
        }

        if (order.normalizedFlow() == ServiceOrderFlow.RESOLUTION) {
            if (order.closureCause.isNullOrBlank()) {
                return ValidationResult.Invalid("A causa identificada é obrigatória para finalizar a OS.")
            }
        }
        if (order.closureSolution.isNullOrBlank()) {
            return ValidationResult.Invalid("A solução ou ação executada é obrigatória para finalizar a OS.")
        }
        return ValidationResult.Valid
    }

    fun validateForUpdate(order: StructuredServiceOrder): ValidationResult {
        if (order.title.isBlank()) return ValidationResult.Invalid("O título da OS é obrigatório.")
        if (order.clientName.isBlank()) return ValidationResult.Invalid("O cliente da OS é obrigatório.")
        if (order.unitName.isBlank()) return ValidationResult.Invalid("O local da OS é obrigatório.")
        if (order.updateDraft.isNullOrBlank()) return ValidationResult.Invalid("Informe o texto da atualização.")
        return ValidationResult.Valid
    }

    /** A period is mandatory for new events; outside-hours periods are warnings only. */
    fun validateSchedule(startMillis: Long?, endMillis: Long?, allDay: Boolean = false): ScheduleValidation {
        if (allDay) return ScheduleValidation(false, error = "Eventos de dia inteiro não são permitidos para uma OS.")
        if (startMillis == null || endMillis == null) return ScheduleValidation(false, error = "Informe o início e o término da OS.")
        if (endMillis <= startMillis) return ScheduleValidation(false, error = "O término deve ser posterior ao início.")
        val start = java.util.Calendar.getInstance().apply { timeInMillis = startMillis }
        val end = java.util.Calendar.getInstance().apply { timeInMillis = endMillis }
        val startMinutes = start.get(java.util.Calendar.HOUR_OF_DAY) * 60 + start.get(java.util.Calendar.MINUTE)
        val endMinutes = end.get(java.util.Calendar.HOUR_OF_DAY) * 60 + end.get(java.util.Calendar.MINUTE)
        val outside = startMinutes < 360 || endMinutes > 1140
        return ScheduleValidation(true, warning = if (outside) "O período está fora da faixa operacional normal (06:00–19:00)." else null)
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
