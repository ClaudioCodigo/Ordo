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
    data object DiagnosticoBateria : Route

    @Serializable
    data class ListaCadastro(val tipo: String) : Route
}
