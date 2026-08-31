package dev.claudiocodigo.nexo.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Hoje : Route

    @Serializable
    data object Agenda : Route

    @Serializable
    data object Ferramentas : Route

    @Serializable
    data object Cadastros : Route

    @Serializable
    data object Mais : Route

    @Serializable
    data class DetalhesOS(val id: String) : Route

    @Serializable
    data class NovaOS(val entryId: String) : Route

    @Serializable
    data class EditorOS(val orderId: String) : Route

    @Serializable
    data class PreviewPublicacao(val orderId: String) : Route

    @Serializable
    data class RevisaoConflito(val orderId: String) : Route

    @Serializable
    data class ExtracaoResumo(val orderId: String) : Route

    @Serializable
    data object DiagnosticoBateria : Route

    @Serializable
    data class ListaCadastro(val tipo: String) : Route

    @Serializable
    data object ContaNextcloud : Route

    @Serializable
    data object CentralSincronizacao : Route

    @Serializable
    data object QrScanner : Route

    @Serializable
    data object DescobertaAgenda : Route

    @Serializable
    data class EventoRemoto(
        val accountId: String,
        val calendarHref: String,
        val href: String
    ) : Route
}
