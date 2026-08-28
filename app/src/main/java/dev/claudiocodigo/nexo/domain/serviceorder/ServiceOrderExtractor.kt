package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ExtractedSummary(
    val externalId: String?,
    val technician: String?,
    val category: String?,
    val title: String,
    val rawSummary: String
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
                technician = null,
                category = null,
                title = "Atendimento sem título",
                rawSummary = raw
            )
        }

        // Split by common segment delimiters (dash, pipe) outside quotes
        val segments = raw.split(Regex("""\s+[-|–—]\s+""")).map { it.trim() }.filter { it.isNotEmpty() }

        var externalId: String? = null
        var remaining = segments.toMutableList()

        // Detect OS number in first segment or full string
        for (i in segments.indices) {
            val seg = segments[i]
            if (seg.equals("????", ignoreCase = true) || seg.equals("SEM OS", ignoreCase = true)) {
                remaining.removeAt(i)
                break
            }
            val match = OS_NUMBER_REGEX.find(seg)
            if (match != null) {
                externalId = match.groupValues[1]
                val cleaned = seg.replace(match.value, "").trim().removePrefix("-").removePrefix(":").trim()
                if (cleaned.isEmpty()) {
                    remaining.removeAt(i)
                } else {
                    remaining[i] = cleaned
                }
                break
            }
        }

        val title = if (remaining.isNotEmpty()) remaining.joinToString(" - ") else raw

        return ExtractedSummary(
            externalId = externalId,
            technician = null,
            category = null,
            title = title,
            rawSummary = raw
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
                val isOfficialIdLabel = label == "os" || label.contains("da os") ||
                    label.startsWith("nº") || label.startsWith("n°") || label.startsWith("no da os")

                when {
                    isOfficialIdLabel -> {
                        externalId = OS_NUMBER_REGEX.find(content)?.groupValues?.get(1)
                        currentSection = "header"
                        continue
                    }
                    label.startsWith("os") || label.startsWith("cliente") || label.startsWith("técnico") ||
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
                        val last = updates.last()
                        updates[updates.lastIndex] = last.copy(text = (last.text + "\n" + line).trim())
                    } else {
                        demandBuffer.appendLine(line)
                    }
                }
                else -> demandBuffer.appendLine(line)
            }
        }

        val originalDemand = demandBuffer.toString().trim().ifEmpty { raw }
        val preset = if (raw.contains("solicitação", ignoreCase = true) && !raw.contains("diagnóstico", ignoreCase = true)) {
            ServiceOrderPreset.SERVICO_SOLICITADO
        } else {
            ServiceOrderPreset.DIAGNOSTICO_CORRECAO
        }

        return ExtractedDescription(
            externalId = externalId,
            preset = preset,
            originalDemand = originalDemand,
            updates = updates,
            closureCause = causeBuffer.toString().trim().takeIf { it.isNotEmpty() },
            closureSolution = solutionBuffer.toString().trim().takeIf { it.isNotEmpty() },
            closurePending = pendingBuffer.toString().trim().takeIf { it.isNotEmpty() },
            rawDescription = raw
        )
    }

    private fun parseDateFromHeader(header: String): Long? {
        val match = Regex("""\b(\d{2}/\d{2}/\d{4})\b""").find(header) ?: return null
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).parse(match.groupValues[1])?.time
        } catch (_: Exception) {
            null
        }
    }
}
