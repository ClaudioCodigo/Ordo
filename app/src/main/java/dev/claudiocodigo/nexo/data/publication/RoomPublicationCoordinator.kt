package dev.claudiocodigo.nexo.data.publication

import dev.claudiocodigo.nexo.domain.caldav.CalDavCredentials
import dev.claudiocodigo.nexo.domain.caldav.CalDavWriteClient
import dev.claudiocodigo.nexo.domain.caldav.ConditionalCreate
import dev.claudiocodigo.nexo.domain.caldav.ConditionalUpdate
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import dev.claudiocodigo.nexo.domain.caldav.WriteOutcome
import dev.claudiocodigo.nexo.domain.publication.DrainOutcome
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.publication.PublicationCoordinator
import dev.claudiocodigo.nexo.domain.publication.PublicationRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class RoomPublicationCoordinator @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val serviceOrderRepository: ServiceOrderRepository,
    private val writeClient: CalDavWriteClient,
    private val credentialStore: CredentialStore,
    private val clock: ClockProvider
) : PublicationCoordinator {

    private val mutex = Mutex()

    override suspend fun drainNext(): DrainOutcome = mutex.withLock {
        val now = clock.nowMillis()
        val op = publicationRepository.claimNextEligible(now) ?: return DrainOutcome.QueueEmpty

        val account = credentialStore.readAccount()
        val password = credentialStore.readAppPassword()
        if (account == null || password == null) {
            publicationRepository.markFailed(op.id, "Conta não configurada para publicação", permanent = true, now)
            return DrainOutcome.PermanentFailure(op.id, "Conta não configurada")
        }

        val credentials = CalDavCredentials(account.server, account.user, password)
        val order = serviceOrderRepository.getStructuredOrderById(op.orderId)
        val targetHref = order?.occurrenceKey?.eventHref ?: ""
        val uid = order?.occurrenceKey?.eventHref?.substringAfterLast('/')?.removeSuffix(".ics") ?: op.orderId.toString()

        val writeOutcome = when (op.action) {
            OutboxAction.CREATE -> {
                writeClient.create(
                    ConditionalCreate(
                        targetHref = targetHref.ifBlank { "${account.server}/remote.php/dav/calendars/${account.user}/trabalho/${op.orderId}.ics" },
                        uid = uid,
                        icsPayload = op.payloadIcs
                    ),
                    credentials
                )
            }
            OutboxAction.UPDATE, OutboxAction.FINALIZE -> {
                val etag = op.ifMatchEtag ?: order?.baseSnapshot?.etag.orEmpty()
                if (etag.isBlank()) {
                    publicationRepository.markFailed(op.id, "ETag base ausente para atualização condicional", permanent = true, now)
                    return DrainOutcome.PermanentFailure(op.id, "ETag base ausente")
                }
                writeClient.update(
                    ConditionalUpdate(
                        targetHref = targetHref,
                        uid = uid,
                        baseEtag = etag,
                        icsPayload = op.payloadIcs
                    ),
                    credentials
                )
            }
        }

        return when (writeOutcome) {
            is WriteOutcome.Created -> {
                publicationRepository.markSent(op.id, writeOutcome.etag, now)
                order?.let {
                    serviceOrderRepository.saveStructuredOrder(it.copy(publicationState = PublicationState.PUBLISHED))
                }
                DrainOutcome.Success(op.id, writeOutcome.etag)
            }
            is WriteOutcome.Updated -> {
                publicationRepository.markSent(op.id, writeOutcome.etag, now)
                order?.let {
                    serviceOrderRepository.saveStructuredOrder(it.copy(publicationState = PublicationState.PUBLISHED))
                }
                DrainOutcome.Success(op.id, writeOutcome.etag)
            }
            is WriteOutcome.Conflict -> {
                publicationRepository.markConflict(op.id, writeOutcome.message, now)
                order?.let {
                    serviceOrderRepository.saveStructuredOrder(it.copy(publicationState = PublicationState.CONFLICT))
                }
                DrainOutcome.Conflict(op.id, writeOutcome.message)
            }
            is WriteOutcome.TransientFailure -> {
                publicationRepository.markFailed(op.id, writeOutcome.message, permanent = false, now)
                DrainOutcome.TransientFailure(op.id, writeOutcome.message)
            }
            is WriteOutcome.PermissionDenied -> {
                publicationRepository.markFailed(op.id, writeOutcome.message, permanent = true, now)
                DrainOutcome.PermanentFailure(op.id, writeOutcome.message)
            }
            is WriteOutcome.PermanentFailure -> {
                publicationRepository.markFailed(op.id, writeOutcome.message, permanent = true, now)
                DrainOutcome.PermanentFailure(op.id, writeOutcome.message)
            }
        }
    }

    override suspend fun drainAll(): Int {
        var processed = 0
        while (true) {
            val outcome = drainNext()
            if (outcome is DrainOutcome.QueueEmpty || outcome is DrainOutcome.TransientFailure) break
            processed++
        }
        return processed
    }
}
