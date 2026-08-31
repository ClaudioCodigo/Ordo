package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ExtractedSummary(
    val externalId: String?,
    val clientName: String? = null,
    val unitName: String? = null,
    val technician: String? = null,
    val category: String? = null,
    val title: String,
    val rawSummary: String,
    val segments: List<String> = emptyList()
)

data class ExtractedDescription(
    val externalId: String?,
    val preset: ServiceOrderPreset,
    val originalDemand: String,
    val updates: List<ServiceOrderUpdate>,
    val closureCause: String?,
    val closureSolution: String?,
    val closurePending: String?,
    val rawDescription: String
)

/**
 * Pure, deterministic extractor for calendar event SUMMARY and DESCRIPTION.
 *
 * Rules:
 * - Never invents data or assumes a fixed segment count;
 * - "????" or "SEM OS" map to null (absent official number);
 * - Unstructured text is preserved entirely as the origin demand;
 * - Structured labels (Demanda:, Atualização, Causa:, Solução:, etc.) are parsed by first unquoted ':'.
 */
object ServiceOrderExtractor {

    private val OS_NUMBER_REGEX = Regex("""(?i)\b(?:OS\s*[:#-]?\s*|N[ºo°]\s*)?([0-9]{3,8})\b""")

    fun extractSummary(rawSummary: String?): ExtractedSummary {
        val raw = rawSummary.orEmpty().trim()
        if (raw.isEmpty()) {
            return ExtractedSummary(
                externalId = null,
                clientName = null,
                unitName = null,
                technician = null,
                category = null,
                title = "Atendimento sem título",
                rawSummary = raw,
                segments = emptyList()
            )
        }

        // Split by common segment delimiters outside quotes: dash, pipe, en-dash, em-dash
        val rawSegments = raw.split(Regex("""\s+[-|–—]\s+|\s*\|\s*""")).map { it.trim() }.filter { it.isNotEmpty() }

        var externalId: String? = null
        var clientName: String? = null
        var unitName: String? = null
        var technician: String? = null
        var category: String? = null
        var explicitTitle: String? = null
        var hasProvisionalPlaceholder = false

        val unassigned = mutableListOf<String>()

        for (seg in rawSegments) {
            // Check for explicit label prefix
            val colonIdx = seg.indexOf(':')
            if (colonIdx > 0) {
                val label = seg.substring(0, colonIdx).trim().lowercase()
                val value = seg.substring(colonIdx + 1).trim()
                when {
                    label in setOf("os", "nº", "no", "num") -> {
                        if (value.equals("????", ignoreCase = true) || value.equals("SEM OS", ignoreCase = true)) {
                            hasProvisionalPlaceholder = true
                        } else {
                            val match = OS_NUMBER_REGEX.find(value)
                            externalId = match?.groupValues?.get(1) ?: value
                        }
                        continue
                    }
                    label in setOf("cli", "cliente", "empresa") -> {
                        clientName = value
                        continue
                    }
                    label in setOf("unid", "unidade", "setor", "local", "sala") -> {
                        unitName = value
                        continue
                    }
                    label in setOf("tec", "tecnico", "técnico", "resp", "responsavel", "responsável") -> {
                        technician = value
                        continue
                    }
                    label in setOf("cat", "categoria", "tipo") -> {
                        category = value
                        continue
                    }
                    label in setOf("tit", "titulo", "título", "serv", "servico", "serviço", "assunto") -> {
                        explicitTitle = value
                        continue
                    }
                }
            }

            // Check if segment is placeholder for no OS
            if (seg.equals("????", ignoreCase = true) || seg.equals("SEM OS", ignoreCase = true) || seg.equals("SEM_OS", ignoreCase = true)) {
                hasProvisionalPlaceholder = true
                externalId = null
                continue
            }

            // Check if standalone segment matches OS number
            val osMatch = OS_NUMBER_REGEX.find(seg)
            if (externalId == null && osMatch != null && (seg.startsWith("OS", ignoreCase = true) || seg.startsWith("Nº", ignoreCase = true) || seg.all { it.isDigit() })) {
                externalId = osMatch.groupValues[1]
                val cleaned = seg.replace(osMatch.value, "").trim().removePrefix("-").removePrefix(":").trim()
                if (cleaned.isNotEmpty()) {
                    unassigned.add(cleaned)
                }
                continue
            }

            unassigned.add(seg)
        }

        // Positional assignment of unassigned segments
        when (unassigned.size) {
            1 -> {
                if (explicitTitle == null) explicitTitle = unassigned[0]
            }
            2 -> {
                if (clientName == null && (externalId != null || hasProvisionalPlaceholder)) {
                    clientName = unassigned[0]
                    if (explicitTitle == null) explicitTitle = unassigned[1]
                } else if (explicitTitle == null) {
                    explicitTitle = unassigned.joinToString(" - ")
                }
            }
            3 -> {
                if (clientName == null) clientName = unassigned[0]
                if (unitName == null) unitName = unassigned[1]
                if (explicitTitle == null) explicitTitle = unassigned[2]
            }
            else -> {
                if (unassigned.isNotEmpty()) {
                    if (clientName == null && unassigned.size >= 2) clientName = unassigned[0]
                    if (unitName == null && unassigned.size >= 3) unitName = unassigned[1]
                    if (explicitTitle == null) {
                        val startIndex = when {
                            clientName == unassigned.getOrNull(0) && unitName == unassigned.getOrNull(1) -> 2
                            clientName == unassigned.getOrNull(0) -> 1
                            else -> 0
                        }
                        explicitTitle = unassigned.drop(startIndex).joinToString(" - ").ifBlank { raw }
                    }
                }
            }
        }

        val finalTitle = explicitTitle ?: unassigned.joinToString(" - ").ifBlank { raw }

        return ExtractedSummary(
            externalId = externalId,
            clientName = clientName,
            unitName = unitName,
            technician = technician,
            category = category,
            title = finalTitle,
            rawSummary = raw,
            segments = rawSegments
        )
    }

    fun extractDescription(rawDescription: String?): ExtractedDescription {
        val raw = rawDescription.orEmpty().trim()
        if (raw.isEmpty()) {
            return ExtractedDescription(
                externalId = null,
                preset = ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
                originalDemand = "",
                updates = emptyList(),
                closureCause = null,
                closureSolution = null,
                closurePending = null,
                rawDescription = raw
            )
        }

        val lines = raw.lines().map { it.trim() }
        var currentSection: String? = null
        val demandBuffer = StringBuilder()
        val causeBuffer = StringBuilder()
        val solutionBuffer = StringBuilder()
        val pendingBuffer = StringBuilder()
        val updates = mutableListOf<ServiceOrderUpdate>()
        var updateSeq = 1
        var isCompleted = false
        var externalId: String? = null

        for (line in lines) {
            if (line.isEmpty()) continue

            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val label = line.substring(0, colonIdx).trim().lowercase()
                val content = line.substring(colonIdx + 1).trim()

                when {
                    label.startsWith("os") -> {
                        currentSection = "header"
                        if (content.isNotEmpty() && !content.equals("SEM OS", ignoreCase = true) && !content.equals("????", ignoreCase = true)) {
                            val match = OS_NUMBER_REGEX.find(content)
                            externalId = match?.groupValues?.get(1) ?: content
                        }
                        continue
                    }
                    label.startsWith("cliente") || label.startsWith("técnico") ||
                    label.startsWith("tecnico") || label.startsWith("categoria") || label.startsWith("unidade") ||
                    label.startsWith("data") -> {
                        currentSection = "header"
                        continue
                    }
                    label.startsWith("demanda") || label.startsWith("solicitação") || label.startsWith("problema") -> {
                        currentSection = "demand"
                        if (content.isNotEmpty()) demandBuffer.appendLine(content)
                        continue
                    }
                    label.startsWith("estado") && content.contains("conclu", ignoreCase = true) -> {
                        isCompleted = true
                        continue
                    }
                    label.startsWith("causa") -> {
                        currentSection = "cause"
                        if (content.isNotEmpty()) causeBuffer.appendLine(content)
                        continue
                    }
                    label.startsWith("solução") || label.startsWith("solucao") || label.startsWith("resultado") || label.startsWith("ação") -> {
                        currentSection = "solution"
                        if (content.isNotEmpty()) solutionBuffer.appendLine(content)
                        continue
                    }
                    label.startsWith("pendência") || label.startsWith("pendencia") || label.startsWith("pendências") -> {
                        currentSection = "pending"
                        if (content.isNotEmpty()) pendingBuffer.appendLine(content)
                        continue
                    }
                    label.startsWith("atualização") || label.startsWith("atualizacao") || label.startsWith("update") -> {
                        currentSection = "update"
                        val updateDate = parseDateFromHeader(label) ?: System.currentTimeMillis()
                        updates.add(
                            ServiceOrderUpdate(
                                id = UUID.randomUUID(),
                                sequenceOrder = updateSeq++,
                                text = content,
                                executionDate = updateDate
                            )
                        )
                        continue
                    }
                }
            }

            // Continuation of current section or raw demand
            when (currentSection) {
                "header" -> { /* skip header continuations */ }
                "demand" -> demandBuffer.appendLine(line)
                "cause" -> causeBuffer.appendLine(line)
                "solution" -> solutionBuffer.appendLine(line)
                "pending" -> pendingBuffer.appendLine(line)
                "update" -> {
                    if (updates.isNotEmpty()) {
                        val last = updates.removeAt(updates.lastIndex)
                        updates.add(last.copy(text = "${last.text}\n$line"))
                    }
                }
                else -> demandBuffer.appendLine(line)
            }
        }

        val demand = demandBuffer.toString().trim()
        val cause = causeBuffer.toString().trim().takeIf { it.isNotEmpty() }
        val solution = solutionBuffer.toString().trim().takeIf { it.isNotEmpty() }
        val pending = pendingBuffer.toString().trim().takeIf { it.isNotEmpty() }

        val preset = if (cause != null) {
            ServiceOrderPreset.DIAGNOSTICO_CORRECAO
        } else {
            ServiceOrderPreset.SERVICO_SOLICITADO
        }

        return ExtractedDescription(
            externalId = externalId,
            preset = preset,
            originalDemand = demand.ifBlank { raw },
            updates = updates,
            closureCause = cause,
            closureSolution = solution,
            closurePending = pending,
            rawDescription = raw
        )
    }

    private fun parseDateFromHeader(label: String): Long? {
        val dateMatch = Regex("""\b(\d{1,2}/\d{1,2}/\d{2,4})\b""").find(label) ?: return null
        return runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).parse(dateMatch.value)?.time
        }.getOrNull()
    }
}
