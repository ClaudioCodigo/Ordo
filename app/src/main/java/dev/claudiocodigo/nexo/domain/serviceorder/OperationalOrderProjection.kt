package dev.claudiocodigo.nexo.domain.serviceorder

import dev.claudiocodigo.nexo.domain.caldav.ColorClassifier
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxOperation
import dev.claudiocodigo.nexo.domain.publication.OutboxStatus
import java.util.UUID

enum class OperationalStatus {
    REQUER_ATENCAO,                 // Vermelho no Nextcloud (Controle Técnico retornou com pendência)
    CONFLITO_PUBLICACAO,            // Erro 412 na outbox
    ENVIANDO_PUBLICACAO,            // Outbox SENDING
    AGUARDANDO_CONEXAO,             // Outbox PENDING
    FALHA_PUBLICACAO,               // Outbox PERMANENT_FAILURE
    VALIDADO_EXTERNAMENTE,          // Verde no Nextcloud (Validado pela equipe técnica)
    AGUARDANDO_VALIDACAO_EXTERNA,   // Concluído localmente no Nexo, mas ainda sem verde no servidor
    EM_ANDAMENTO,                   // OS ou evento em andamento
    PENDENTE                        // Não iniciado / agendado
}

enum class CardNavigationTarget {
    EVENTO_REMOTO,
    EDITOR_OS,
    REVISAO_CONFLITO
}

data class OperationalOrderCard(
    val cardId: String,
    val localOrderId: UUID?,
    val remoteAccountId: String?,
    val remoteCalendarHref: String?,
    val remoteEventHref: String?,
    val externalId: String?,
    val title: String,
    val clientName: String,
    val unitName: String?,
    val startMillis: Long?,
    val endMillis: Long?,
    val status: OperationalStatus,
    val rawColor: String?,
    val navigationTarget: CardNavigationTarget,
    val isLinked: Boolean,
    val officialNumberAssigned: Boolean = false
)

object OperationalOrderProjection {

    fun project(
        remoteEvents: List<RemoteEvent>,
        structuredOrders: List<StructuredServiceOrder>,
        outboxOperations: List<OutboxOperation>,
        customValidatedColors: Set<String>? = null,
        customAttentionColors: Set<String>? = null
    ): List<OperationalOrderCard> {
        val outboxByOrder = outboxOperations.groupBy { it.orderId }
        val ordersByKey = structuredOrders.filter { it.occurrenceKey != null }.associateBy { it.occurrenceKey!! }
        val usedOrderIds = mutableSetOf<UUID>()
        val cards = mutableListOf<OperationalOrderCard>()

        // 1. Process Remote Events (either linked or standalone)
        for (event in remoteEvents) {
            val key = RemoteOccurrenceKey(event.accountId, event.calendarHref, event.href, null)
            val linkedOrder = ordersByKey[key]
            if (linkedOrder != null) {
                usedOrderIds.add(linkedOrder.id)
            }

            val latestOutbox = linkedOrder?.let { outboxByOrder[it.id]?.lastOrNull() }
            val color = ColorClassifier.classify(event.rawEventColor, customValidatedColors, customAttentionColors)

            val status = determineStatus(
                color = color,
                outboxStatus = latestOutbox?.status,
                localStatus = linkedOrder?.status,
                isCompletedLocal = linkedOrder?.status == ServiceOrderStatus.CONCLUIDA,
                conclusionState = linkedOrder?.conclusionState ?: ConclusionState.NAO_DEFINIDO
            )

            val navTarget = when (status) {
                OperationalStatus.CONFLITO_PUBLICACAO -> CardNavigationTarget.REVISAO_CONFLITO
                else -> CardNavigationTarget.EVENTO_REMOTO
            }

            cards.add(
                OperationalOrderCard(
                    cardId = "remote_${event.accountId}_${event.calendarHref}_${event.href}",
                    localOrderId = linkedOrder?.id,
                    remoteAccountId = event.accountId,
                    remoteCalendarHref = event.calendarHref,
                    remoteEventHref = event.href,
                    externalId = linkedOrder?.externalId,
                    title = linkedOrder?.title?.takeIf { it.isNotBlank() } ?: event.summary?.ifBlank { "Evento sem título" } ?: "Evento sem título",
                    clientName = linkedOrder?.clientName?.takeIf { it.isNotBlank() } ?: "Cliente",
                    unitName = linkedOrder?.unitName,
                    startMillis = event.start,
                    endMillis = event.end,
                    status = status,
                    rawColor = event.rawEventColor,
                    navigationTarget = navTarget,
                    isLinked = linkedOrder != null,
                    officialNumberAssigned = linkedOrder?.officialNumberJustAssigned == true
                )
            )
        }

        // 2. Stable Oldest-First Sort with Concluded items placed at the end
        return cards.sortedWith(
            compareBy<OperationalOrderCard> { isCardConcluded(it) }
                .thenBy { it.startMillis ?: Long.MAX_VALUE }
                .thenBy { it.endMillis ?: Long.MAX_VALUE }
                .thenBy { it.cardId }
        )
    }

    fun isCardConcluded(card: OperationalOrderCard): Boolean =
        card.status == OperationalStatus.VALIDADO_EXTERNAMENTE ||
            card.status == OperationalStatus.AGUARDANDO_VALIDACAO_EXTERNA

    private fun determineStatus(
        color: EventColor,
        outboxStatus: OutboxStatus?,
        localStatus: ServiceOrderStatus?,
        isCompletedLocal: Boolean,
        conclusionState: ConclusionState = ConclusionState.NAO_DEFINIDO
    ): OperationalStatus {
        // Precedence:
        // 1. Red (Requer Atenção) wins above all else
        if (color == EventColor.REQUER_ATENCAO) return OperationalStatus.REQUER_ATENCAO

        // 2. Outbox conflict (412)
        if (outboxStatus == OutboxStatus.CONFLICT) return OperationalStatus.CONFLITO_PUBLICACAO

        // 3. Outbox in-flight states
        if (outboxStatus == OutboxStatus.SENDING) return OperationalStatus.ENVIANDO_PUBLICACAO
        if (outboxStatus == OutboxStatus.PENDING) return OperationalStatus.AGUARDANDO_CONEXAO
        if (outboxStatus == OutboxStatus.PERMANENT_FAILURE) return OperationalStatus.FALHA_PUBLICACAO

        // 4. External validated (Green)
        if (color == EventColor.VALIDADO) return OperationalStatus.VALIDADO_EXTERNAMENTE

        // 5. Internal completion without external green
        if (conclusionState == ConclusionState.NAO_CONCLUIDO) {
            return if (localStatus == ServiceOrderStatus.PENDENTE) OperationalStatus.PENDENTE else OperationalStatus.EM_ANDAMENTO
        }
        if (isCompletedLocal || localStatus == ServiceOrderStatus.CONCLUIDA || conclusionState.isCompletion) {
            return OperationalStatus.AGUARDANDO_VALIDACAO_EXTERNA
        }

        // 6. Normal progress
        if (localStatus == ServiceOrderStatus.EM_ANDAMENTO) return OperationalStatus.EM_ANDAMENTO

        return OperationalStatus.PENDENTE
    }
}
