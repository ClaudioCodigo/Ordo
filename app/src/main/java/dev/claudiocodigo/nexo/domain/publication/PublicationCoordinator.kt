package dev.claudiocodigo.nexo.domain.publication

interface PublicationCoordinator {
    suspend fun drainNext(): DrainOutcome
    suspend fun drainAll(): Int
}
