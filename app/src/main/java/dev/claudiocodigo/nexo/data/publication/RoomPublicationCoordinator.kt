package dev.claudiocodigo.nexo.data.publication

import dev.claudiocodigo.nexo.data.ical.IcsParser
import dev.claudiocodigo.nexo.data.caldav.CalDavXmlParser
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
import dev.claudiocodigo.nexo.domain.repository.CalendarSetupRepository
import dev.claudiocodigo.nexo.domain.repository.ServiceOrderRepository
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteBaseSnapshot
import dev.claudiocodigo.nexo.domain.serviceorder.RemoteOccurrenceKey
import dev.claudiocodigo.nexo.domain.time.ClockProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RoomPublicationCoordinator @Inject constructor(
    private val publicationRepository: PublicationRepository,
    private val serviceOrderRepository: ServiceOrderRepository,
    private val calendarSetupRepository: CalendarSetupRepository,
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
                val selectedCalendar = calendarSetupRepository.observeSelectedCalendar().first()
                if (selectedCalendar == null) {
                    publicationRepository.markFailed(op.id, "Agenda de trabalho não selecionada", permanent = true, now)
                    return DrainOutcome.PermanentFailure(op.id, "Agenda de trabalho não selecionada")
                }
                val createTarget = resolveCollectionMember(
                    server = account.server,
                    calendarHref = selectedCalendar.href,
                    memberName = "${op.orderId}.ics"
                )
                writeClient.create(
                    ConditionalCreate(
                        targetHref = createTarget,
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
                    if (if (op.action == OutboxAction.UPDATE) it.updateDraftRevision == op.confirmedRevision else it.draftRevision == op.confirmedRevision) {
                        val selectedCalendar = calendarSetupRepository.observeSelectedCalendar().first()
                        val accountId = calendarSetupRepository.getActiveAccountId()
                        val parsed = IcsParser.parse(op.payloadIcs).events.firstOrNull()
                        serviceOrderRepository.saveStructuredOrder(
                            it.copy(
                                occurrenceKey = if (selectedCalendar != null && accountId != null) RemoteOccurrenceKey(accountId, selectedCalendar.href, writeOutcome.href) else it.occurrenceKey,
                                baseSnapshot = RemoteBaseSnapshot(etag = writeOutcome.etag, rawIcs = op.payloadIcs, rawSummary = parsed?.summary, rawDescription = parsed?.description, capturedAt = now),
                                publicationState = PublicationState.PUBLISHED,
                                updateDraft = null,
                                updateDraftRevision = 0L
                            )
                        )
                    }
                }
                DrainOutcome.Success(op.id, writeOutcome.etag)
            }
            is WriteOutcome.Updated -> {
                publicationRepository.markSent(op.id, writeOutcome.etag, now)
                order?.let {
                    if (if (op.action == OutboxAction.UPDATE) it.updateDraftRevision == op.confirmedRevision else it.draftRevision == op.confirmedRevision) {
                        val parsed = IcsParser.parse(op.payloadIcs).events.firstOrNull()
                        serviceOrderRepository.saveStructuredOrder(
                            it.copy(
                                baseSnapshot = (it.baseSnapshot ?: RemoteBaseSnapshot(etag = writeOutcome.etag, rawIcs = op.payloadIcs, rawSummary = parsed?.summary, rawDescription = parsed?.description, capturedAt = now)).copy(
                                    etag = writeOutcome.etag, rawIcs = op.payloadIcs, rawSummary = it.baseSnapshot?.rawSummary ?: parsed?.summary, rawDescription = parsed?.description, capturedAt = now
                                ),
                                publicationState = PublicationState.PUBLISHED,
                                updateDraft = null,
                                updateDraftRevision = 0L
                            )
                        )
                    }
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

    private fun resolveCollectionMember(server: String, calendarHref: String, memberName: String): String {
        val collection = CalDavXmlParser.resolveHref(server, calendarHref).trimEnd('/')
        return "$collection/$memberName"
    }
}
