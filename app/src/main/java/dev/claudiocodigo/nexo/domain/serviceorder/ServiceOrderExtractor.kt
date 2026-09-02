package dev.claudiocodigo.nexo.domain.serviceorder

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

enum class SummaryFieldOrigin { EXPLICIT_LABEL, POSITIONAL, AMBIGUOUS }

data class ExtractedSummary(
    val externalId: String?,
    val clientName: String? = null,
    val unitName: String? = null,
    val technician: String? = null,
    val category: String? = null,
    val title: String,
    val rawSummary: String,
    val segments: List<String> = emptyList(),
    val fieldOrigins: Map<String, SummaryFieldOrigin> = emptyMap(),
    val confidence: Map<String, Float> = emptyMap(),
    val ambiguousSegments: List<String> = emptyList()
)

data class ExtractedDescription(
    val externalId: String?,
    val preset: ServiceOrderPreset,
    val originalDemand: String,
    val updates: List<ServiceOrderUpdate>,
    val closureCause: String?,
    val closureSolution: String?,
    val closurePending: String?,
    val rawDescription: String,
    val conclusionState: ConclusionState = ConclusionState.NAO_DEFINIDO,
    val isCompleted: Boolean = false,
    val clientName: String? = null,
    val unitName: String? = null,
    val technician: String? = null,
    val flow: ServiceOrderFlow = ServiceOrderFlow.RESOLUTION,
    val technicalOpinion: TechnicalOpinion = TechnicalOpinion.CONCLUDED,
    val observations: String? = null,
    val updateDraft: String? = null,
    val previousDescription: String? = null
)

/** Lossless, conservative extraction of remote SUMMARY and DESCRIPTION values. */
object ServiceOrderExtractor {
    private val OS_NUMBER_REGEX = Regex("""(?i)\b(?:OS\s*[:#-]?\s*|N[ºo°]\s*)?([0-9]{3,8})\b""")
    private val SUMMARY_SPLIT = Regex("""\s+[-|–—]\s+|\s*\|\s*""")

    fun extractSummary(rawSummary: String?): ExtractedSummary {
        val raw = rawSummary.orEmpty()
        if (raw.trim().isEmpty()) return ExtractedSummary(null, title = "Atendimento sem título", rawSummary = raw)
        val segments = raw.trim().split(SUMMARY_SPLIT).map(String::trim).filter(String::isNotEmpty)
        var externalId: String? = null
        var placeholder = false
        val remaining = mutableListOf<String>()
        var client: String? = null
        var location: String? = null
        var technician: String? = null
        var category: String? = null
        var title: String? = null
        val origins = mutableMapOf<String, SummaryFieldOrigin>()

        segments.forEach { segment ->
            val colon = segment.indexOf(':')
            if (colon > 0) {
                val label = segment.substring(0, colon).trim().lowercase(Locale.ROOT)
                val value = segment.substring(colon + 1).trim()
                when {
                    label in setOf("os", "nº", "no", "num") -> {
                        if (value == "????" || value.equals("SEM OS", true) || value.equals("SEM_OS", true)) placeholder = true
                        else externalId = OS_NUMBER_REGEX.find(value)?.groupValues?.get(1) ?: value
                        return@forEach
                    }
                    label in setOf("cli", "cliente", "empresa") -> { client = value; origins["clientName"] = SummaryFieldOrigin.EXPLICIT_LABEL; return@forEach }
                    label in setOf("unid", "unidade", "setor", "local", "sala") -> { location = value; origins["unitName"] = SummaryFieldOrigin.EXPLICIT_LABEL; return@forEach }
                    label in setOf("tec", "tecnico", "técnico", "resp", "responsavel", "responsável") -> { technician = value; origins["technician"] = SummaryFieldOrigin.EXPLICIT_LABEL; return@forEach }
                    label in setOf("cat", "categoria", "tipo") -> { category = value; origins["category"] = SummaryFieldOrigin.EXPLICIT_LABEL; return@forEach }
                    label in setOf("tit", "titulo", "título", "serv", "servico", "serviço", "assunto") -> { title = value; origins["title"] = SummaryFieldOrigin.EXPLICIT_LABEL; return@forEach }
                }
            }
            if (segment == "????" || segment.equals("SEM OS", true) || segment.equals("SEM_OS", true)) {
                placeholder = true
            } else {
                val match = OS_NUMBER_REGEX.find(segment)
                val isNumberSegment = match != null && (segment.startsWith("OS", true) || segment.startsWith("Nº", true) || segment.all(Char::isDigit))
                if (isNumberSegment && externalId == null) externalId = match!!.groupValues[1] else remaining += segment
            }
        }

        // Standard legacy: company - number - location - title; approved new: company - number/???? - technician - category - title - location.
        when {
            remaining.size >= 5 && (placeholder || externalId != null) -> {
                if (client == null) { client = remaining[0]; origins["clientName"] = SummaryFieldOrigin.POSITIONAL }
                if (technician == null) { technician = remaining[1]; origins["technician"] = SummaryFieldOrigin.POSITIONAL }
                if (category == null) { category = remaining[2]; origins["category"] = SummaryFieldOrigin.POSITIONAL }
                if (title == null) { title = remaining[3]; origins["title"] = SummaryFieldOrigin.POSITIONAL }
                if (location == null) { location = remaining[4]; origins["unitName"] = SummaryFieldOrigin.POSITIONAL }
            }
            remaining.size == 4 && (placeholder || externalId != null) -> {
                if (client == null) { client = remaining[0]; origins["clientName"] = SummaryFieldOrigin.POSITIONAL }
                if (category == null) { category = remaining[1]; origins["category"] = SummaryFieldOrigin.POSITIONAL }
                if (title == null) { title = remaining[2]; origins["title"] = SummaryFieldOrigin.POSITIONAL }
                if (location == null) { location = remaining[3]; origins["unitName"] = SummaryFieldOrigin.POSITIONAL }
            }
            remaining.size == 3 && (placeholder || externalId != null) -> {
                if (client == null) { client = remaining[0]; origins["clientName"] = SummaryFieldOrigin.POSITIONAL }
                if (location == null) { location = remaining[1]; origins["unitName"] = SummaryFieldOrigin.POSITIONAL }
                if (title == null) { title = remaining[2]; origins["title"] = SummaryFieldOrigin.POSITIONAL }
            }
            remaining.size == 2 && (placeholder || externalId != null) -> {
                if (client == null) { client = remaining[0]; origins["clientName"] = SummaryFieldOrigin.POSITIONAL }
                if (title == null) { title = remaining[1]; origins["title"] = SummaryFieldOrigin.POSITIONAL }
            }
            remaining.isNotEmpty() -> {
                if (title == null) title = remaining.last()
            }
        }
        val assigned = setOfNotNull(client, location, technician, category, title)
        val ambiguous = remaining.filterNot { it in assigned }
        val confidence = origins.mapValues { (_, origin) -> if (origin == SummaryFieldOrigin.EXPLICIT_LABEL) 1f else 0.6f }
        return ExtractedSummary(
            externalId = externalId,
            clientName = client,
            unitName = location,
            technician = technician,
            category = category,
            title = title?.ifBlank { "Atendimento sem título" } ?: "Atendimento sem título",
            rawSummary = raw,
            segments = segments,
            fieldOrigins = origins,
            confidence = confidence,
            ambiguousSegments = ambiguous
        )
    }

    fun extractDescription(rawDescription: String?): ExtractedDescription {
        val raw = rawDescription.orEmpty()
        if (raw.trim().isEmpty()) return ExtractedDescription(null, ServiceOrderPreset.DIAGNOSTICO_CORRECAO, "", emptyList(), null, null, null, raw)
        val historyMarker = when {
            raw.contains("----- Histórico Remoto Preservado -----") -> "----- Histórico Remoto Preservado -----"
            raw.contains("--- Histórico anterior ---") -> "--- Histórico anterior ---"
            else -> null
        }
        val markerIndex = historyMarker?.let { raw.indexOf(it) } ?: -1
        val currentText = if (markerIndex >= 0 && historyMarker != null) raw.substring(0, markerIndex) else raw
        val history = if (markerIndex >= 0 && historyMarker != null) raw.substring(markerIndex + historyMarker.length).trimStart('\r', '\n') else null
        val lines = currentText.lines().map(String::trim)
        var section: String? = null
        var externalId: String? = null
        var client: String? = null
        var location: String? = null
        var technician: String? = null
        var flow = ServiceOrderFlow.RESOLUTION
        var state = ConclusionState.NAO_DEFINIDO
        var opinion = TechnicalOpinion.CONCLUDED
        val demand = StringBuilder()
        val cause = StringBuilder()
        val solution = StringBuilder()
        val pending = StringBuilder()
        val observations = StringBuilder()
        val update = StringBuilder()
        val updates = mutableListOf<ServiceOrderUpdate>()
        var updateSequence = 1
        var sawStructuredSection = false

        for (line in lines) {
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon > 0) {
                val label = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                val content = line.substring(colon + 1).trim()
                when {
                    label == "os" || label.startsWith("nº da os") -> { externalId = OS_NUMBER_REGEX.find(content)?.groupValues?.get(1); section = "header"; continue }
                    label.startsWith("cliente") || label == "empresa" -> {
                        val parts = content.split(" - ", limit = 2); client = parts[0].trim(); if (parts.size > 1) location = parts[1].trim(); section = "header"; continue
                    }
                    label.startsWith("local") || label.startsWith("unidade") -> { location = content; section = "header"; continue }
                    label.startsWith("técnico") || label.startsWith("tecnico") -> { technician = content; section = "header"; continue }
                    label.startsWith("categoria") || label.startsWith("data") -> { section = "header"; continue }
                    label.startsWith("parecer técnico") || label.startsWith("parecer tecnico") -> { opinion = if (content.contains("não", true) || content.contains("nao", true)) TechnicalOpinion.NOT_CONCLUDED else TechnicalOpinion.CONCLUDED; state = if (opinion == TechnicalOpinion.NOT_CONCLUDED) ConclusionState.NAO_CONCLUIDO else ConclusionState.CONCLUIDO; section = "header"; continue }
                    label.startsWith("estado") -> {
                        state = when { content.contains("não conclu", true) || content.contains("nao conclu", true) -> ConclusionState.NAO_CONCLUIDO; content.contains("pendên", true) || content.contains("pendenc", true) -> ConclusionState.CONCLUIDO_COM_PENDENCIAS; content.contains("conclu", true) -> ConclusionState.CONCLUIDO; else -> ConclusionState.NAO_DEFINIDO }
                        opinion = if (state == ConclusionState.NAO_CONCLUIDO) TechnicalOpinion.NOT_CONCLUDED else TechnicalOpinion.CONCLUDED; section = "header"; continue
                    }
                    label.startsWith("demanda") || label.startsWith("problema") -> { sawStructuredSection = true; section = "demand"; if (content.isNotEmpty()) demand.appendLine(content); continue }
                    label.startsWith("solicitação") || label.startsWith("solicitacao") -> { sawStructuredSection = true; flow = ServiceOrderFlow.REQUEST; section = "demand"; if (content.isNotEmpty()) demand.appendLine(content); continue }
                    label.startsWith("causa") -> { sawStructuredSection = true; flow = ServiceOrderFlow.RESOLUTION; section = "cause"; if (content.isNotEmpty()) cause.appendLine(content); continue }
                    label.startsWith("solução") || label.startsWith("solucao") || label.startsWith("ação") || label.startsWith("acao") -> { sawStructuredSection = true; section = "solution"; if (content.isNotEmpty()) solution.appendLine(content); continue }
                    label.startsWith("pendência") || label.startsWith("pendencia") -> { sawStructuredSection = true; section = "pending"; if (content.isNotEmpty()) pending.appendLine(content); continue }
                    label.startsWith("observação") || label.startsWith("observacao") -> { sawStructuredSection = true; section = "observations"; if (content.isNotEmpty()) observations.appendLine(content); continue }
                    label.startsWith("atualização") || label.startsWith("atualizacao") || label == "update" -> { sawStructuredSection = true; flow = ServiceOrderFlow.UPDATE; section = "update"; if (content.isNotEmpty()) update.appendLine(content); updates += ServiceOrderUpdate(UUID.randomUUID(), updateSequence++, content, System.currentTimeMillis()); continue }
                }
            }
            when (section) {
                "demand" -> demand.appendLine(line)
                "cause" -> cause.appendLine(line)
                "solution" -> solution.appendLine(line)
                "pending" -> pending.appendLine(line)
                "observations" -> observations.appendLine(line)
                "update" -> { if (update.isNotEmpty()) update.appendLine(line); if (updates.isNotEmpty()) updates[updates.lastIndex] = updates.last().copy(text = update.toString().trim()) }
            }
        }
        val causeText = cause.toString().trim().takeIf { it.isNotEmpty() && !it.equals("N/A", true) }
        val solutionText = solution.toString().trim().takeIf(String::isNotEmpty)
        val pendingText = pending.toString().trim().takeIf(String::isNotEmpty)
        if (flow == ServiceOrderFlow.RESOLUTION && causeText == null && solutionText == null && updates.isNotEmpty()) flow = ServiceOrderFlow.UPDATE
        if (!sawStructuredSection && updates.isEmpty()) flow = ServiceOrderFlow.REQUEST
        val preset = if (flow == ServiceOrderFlow.REQUEST) ServiceOrderPreset.SERVICO_SOLICITADO else ServiceOrderPreset.DIAGNOSTICO_CORRECAO
        return ExtractedDescription(
            externalId, preset, demand.toString().trim().ifBlank { currentText.trim() }, updates,
            causeText, solutionText, pendingText, raw, state, state.isCompletion,
            client, location, technician, flow, opinion, observations.toString().trim().takeIf(String::isNotEmpty), update.toString().trim().takeIf(String::isNotEmpty), history
        )
    }

    @Suppress("unused")
    private fun parseDateFromHeader(label: String): Long? = runCatching {
        val value = Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b""").find(label)?.value ?: return null
        SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).parse(value)?.time
    }.getOrNull()
}
