package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deterministic renderer for the DESCRIPTION and SUMMARY sent to CalDAV. */
object ServiceOrderRenderer {
    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(epochMillis))

    fun renderUpdate(
        order: StructuredServiceOrder,
        executionDate: Long = System.currentTimeMillis(),
        previousDescription: String? = order.baseSnapshot?.rawDescription
    ): String {
        if (order.normalizedFlow() == ServiceOrderFlow.UPDATE) {
            val updateText = order.updateDraft?.trim()
                ?: order.updates.maxByOrNull { it.sequenceOrder }?.text?.trim().orEmpty()
            return buildString {
                appendHeader(this, order)
                appendLine("Atualização:")
                appendLine(updateText)
                if (!order.observations.isNullOrBlank()) {
                    appendLine()
                    appendObservations(this, order.observations)
                }
                if (!previousDescription.isNullOrBlank()) {
                    appendLine()
                    appendLine("----- Histórico Remoto Preservado -----")
                    appendLine()
                    append(previousDescription)
                }
            }.trimEnd()
        }

        // Compatibility with old drafts that stored a list of field updates.
        return buildString {
            appendHeader(this, order)
            val demandLabel = if (order.normalizedFlow() == ServiceOrderFlow.REQUEST) "Solicitação" else "Demanda"
            appendLine("$demandLabel:")
            appendLine(order.originalDemand.ifBlank { "Não informada" }.trim())
            appendLine()
            if (order.conclusionState == ConclusionState.NAO_CONCLUIDO) {
                appendLine("Estado: Não concluído")
                appendLine()
            }
            if (order.updates.isNotEmpty()) {
                appendLine("Atualizações:")
                order.updates.sortedWith(compareBy<ServiceOrderUpdate> { it.executionDate }.thenBy { it.createdAt }.thenBy { it.id })
                    .forEach { appendLine("[${formatDate(it.executionDate)}]: ${it.text.trim()}") }
                appendLine()
            }
            appendLine("Pendências:")
            appendLine(order.closurePending?.trim().takeUnless { it.isNullOrBlank() } ?: "Nenhuma")
            if (!order.observations.isNullOrBlank()) {
                appendLine()
                appendObservations(this, order.observations)
            }
        }.trim()
    }

    fun renderCompletion(order: StructuredServiceOrder, executionDate: Long = System.currentTimeMillis()): String =
        buildString {
            appendHeader(this, order)
            appendLine("Estado: ${technicalState(order)}")
            appendLine("Data de Conclusão: ${formatDate(executionDate)}")
            appendLine()
            val request = order.normalizedFlow() == ServiceOrderFlow.REQUEST
            appendLine(if (request) "Solicitação:" else "Demanda:")
            appendLine(order.originalDemand.trim())
            appendLine()
            if (!request) {
                appendLine("Causa:")
                appendLine(order.closureCause?.trim().takeUnless { it.isNullOrBlank() } ?: "N/A")
                appendLine()
            }
            appendLine(if (request) "Ação Realizada:" else "Solução:")
            appendLine(order.closureSolution?.trim().orEmpty())
            appendLine()
            appendLine("Pendências:")
            appendLine(order.closurePending?.trim().takeUnless { it.isNullOrBlank() } ?: "Nenhuma")
            if (!order.observations.isNullOrBlank()) {
                appendLine()
                appendObservations(this, order.observations)
            }
        }.trim()

    /** New provisional events contain company, placeholder, technician, category, title and location. */
    fun renderSummary(order: StructuredServiceOrder): String {
        val company = order.clientName.trim().ifBlank { "EMPRESA" }
        val number = order.externalId?.trim()?.ifBlank { null }
        val title = order.title.trim().ifBlank { "ATENDIMENTO" }
        val location = order.unitName.trim().ifBlank { "LOCAL" }
        if (number == null) {
            return listOf(
                company, "????",
                order.technician?.trim().takeUnless { it.isNullOrBlank() } ?: "TÉCNICO",
                order.category?.trim().takeUnless { it.isNullOrBlank() } ?: "CATEGORIA",
                title, location
            ).joinToString(" - ")
        }
        return listOfNotNull(company, number, order.category?.trim().takeUnless { it.isNullOrBlank() }, title, location)
            .joinToString(" - ")
    }

    private fun technicalState(order: StructuredServiceOrder): String = when {
        order.conclusionState == ConclusionState.CONCLUIDO_COM_PENDENCIAS -> "Concluído com pendências"
        order.technicalOpinion == TechnicalOpinion.NOT_CONCLUDED || order.conclusionState == ConclusionState.NAO_CONCLUIDO -> "Não concluído"
        else -> "Concluído"
    }

    private fun appendObservations(builder: StringBuilder, observations: String?) {
        if (!observations.isNullOrBlank()) {
            builder.appendLine("Observações:")
            builder.appendLine(observations.trim())
        }
    }

    private fun appendHeader(builder: StringBuilder, order: StructuredServiceOrder) {
        builder.appendLine("OS: ${order.externalId?.trim().takeUnless { it.isNullOrBlank() } ?: "????"}")
        builder.appendLine("Cliente: ${order.clientName.trim()}")
        builder.appendLine("Local: ${order.unitName.trim()}")
        builder.appendLine("Técnico: ${order.technician?.trim().orEmpty()}")
        builder.appendLine()
    }
}
