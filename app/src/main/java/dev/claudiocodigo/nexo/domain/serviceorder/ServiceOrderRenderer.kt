package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deterministic renderer that produces standardized iCalendar DESCRIPTION text.
 *
 * Rules:
 * - Update projection: Identification + Original Demand + Chronological Updates + Current Pendencies;
 * - Completion projection: Identification + Original Demand + "Estado: Concluído" + Cause + Solution + Pendencies;
 * - Historical intermediate updates are omitted from the remote final completion text (preserved locally in Room);
 * - Execution date (dd/MM/yyyy) is required in the body; time of day is strictly omitted (belongs to DTSTART/DTEND).
 */
object ServiceOrderRenderer {

    private fun formatDate(epochMillis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(epochMillis))

    fun renderUpdate(order: StructuredServiceOrder, executionDate: Long = System.currentTimeMillis()): String = buildString {
        appendHeader(this, order)

        val demandLabel = if (order.preset == ServiceOrderPreset.SERVICO_SOLICITADO) "Solicitação" else "Demanda"
        appendLine("$demandLabel:")
        appendLine(order.originalDemand.ifBlank { "Não informada" }.trim())
        appendLine()

        if (order.updates.isNotEmpty()) {
            appendLine("Atualizações:")
            val sortedUpdates = order.updates.sortedWith(
                compareBy<ServiceOrderUpdate> { it.executionDate }
                    .thenBy { it.createdAt }
                    .thenBy { it.id }
            )
            for (update in sortedUpdates) {
                appendLine("[${formatDate(update.executionDate)}]: ${update.text.trim()}")
            }
            appendLine()
        }

        if (order.items.isNotEmpty()) {
            appendLine("Equipamentos / Itens:")
            for (item in order.items) {
                val details = listOfNotNull(item.brand, item.model, item.serialNumber?.let { "S/N: $it" })
                    .joinToString(" ")
                val desc = if (details.isNotEmpty()) "${item.action}: ${item.itemType} ($details)" else "${item.action}: ${item.itemType}"
                appendLine("- $desc")
            }
            appendLine()
        }

        val pending = order.closurePending?.trim()
        appendLine("Pendências:")
        appendLine(if (pending.isNullOrBlank()) "Nenhuma" else pending)
    }.trim()

    fun renderCompletion(order: StructuredServiceOrder, executionDate: Long = System.currentTimeMillis()): String = buildString {
        appendHeader(this, order)

        val demandLabel = if (order.preset == ServiceOrderPreset.SERVICO_SOLICITADO) "Solicitação" else "Demanda"
        appendLine("$demandLabel:")
        appendLine(order.originalDemand.ifBlank { "Não informada" }.trim())
        appendLine()

        appendLine("Estado: Concluído")
        appendLine("Data de Conclusão: ${formatDate(executionDate)}")
        appendLine()

        if (order.preset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO) {
            order.closureCause?.takeIf { it.isNotBlank() }?.let {
                appendLine("Causa:")
                appendLine(it.trim())
                appendLine()
            }
        }

        val solutionLabel = if (order.preset == ServiceOrderPreset.SERVICO_SOLICITADO) "Ação Realizada" else "Solução"
        appendLine("$solutionLabel:")
        appendLine(order.closureSolution?.takeIf { it.isNotBlank() }?.trim() ?: "Serviço concluído conforme solicitado.")
        appendLine()

        if (order.items.isNotEmpty()) {
            appendLine("Equipamentos / Itens:")
            for (item in order.items) {
                val details = listOfNotNull(item.brand, item.model, item.serialNumber?.let { "S/N: $it" })
                    .joinToString(" ")
                val desc = if (details.isNotEmpty()) "${item.action}: ${item.itemType} ($details)" else "${item.action}: ${item.itemType}"
                appendLine("- $desc")
            }
            appendLine()
        }

        val pending = order.closurePending?.trim()
        appendLine("Pendências:")
        appendLine(if (pending.isNullOrBlank()) "Nenhuma" else pending)
    }.trim()

    private fun appendHeader(builder: StringBuilder, order: StructuredServiceOrder) {
        val osNumber = order.externalId?.takeIf { it.isNotBlank() } ?: "SEM OS"
        builder.appendLine("OS: $osNumber")
        builder.appendLine("Cliente: ${order.clientName.ifBlank { "Não informado" }} - ${order.unitName.ifBlank { "Unidade não informada" }}")
        order.technician?.takeIf { it.isNotBlank() }?.let { builder.appendLine("Técnico: $it") }
        order.category?.takeIf { it.isNotBlank() }?.let { builder.appendLine("Categoria: $it") }
        builder.appendLine()
    }
}
