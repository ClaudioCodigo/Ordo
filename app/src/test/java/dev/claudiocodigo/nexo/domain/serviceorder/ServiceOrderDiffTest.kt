package dev.claudiocodigo.nexo.domain.serviceorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServiceOrderDiffTest {

    @Test
    fun computeDifferences_detectsOnlyFieldsThatDiffer() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            title = "Manutenção Antiga",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Nobreak desligando sozinho",
            baseSnapshot = RemoteBaseSnapshot(etag = "\"etag-1\"", rawIcs = "", rawSummary = "Manutenção Antiga", rawDescription = "Nobreak desligando sozinho")
        )

        val diffs = ServiceOrderDiff.computeDifferences(
            localOrder = order,
            remoteDemand = "Nobreak desligando e fumaçando",
            remoteTitle = "Manutenção Antiga",
            remoteCause = null,
            remoteSolution = null,
            remotePending = null
        )

        assertEquals(1, diffs.size)
        assertEquals(ConflictField.ORIGINAL_DEMAND, diffs[0].field)
        assertEquals("Nobreak desligando sozinho", diffs[0].localValue)
        assertEquals("Nobreak desligando e fumaçando", diffs[0].remoteValue)
    }

    @Test
    fun applyChoices_updatesSelectedFieldsAndUpdatesBaseEtag() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            title = "Meu Título Local",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Minha Demanda Local",
            baseSnapshot = RemoteBaseSnapshot(etag = "\"etag-old\"", rawIcs = "", rawSummary = "", rawDescription = "")
        )

        val choices = mapOf(
            ConflictField.TITLE to FieldChoice.KEEP_LOCAL,
            ConflictField.ORIGINAL_DEMAND to FieldChoice.USE_REMOTE
        )

        val resolved = ServiceOrderDiff.applyChoices(
            localOrder = order,
            choices = choices,
            remoteDemand = "Demanda Atualizada no Servidor",
            remoteTitle = "Título no Servidor",
            remoteCause = null,
            remoteSolution = null,
            remotePending = null,
            newEtag = "\"etag-renewed\""
        )

        assertEquals("Meu Título Local", resolved.title)
        assertEquals("Demanda Atualizada no Servidor", resolved.originalDemand)
        assertEquals("\"etag-renewed\"", resolved.baseSnapshot?.etag)
        assertEquals(PublicationState.LOCAL_DRAFT, resolved.publicationState)
    }

    @Test
    fun officialNumberDifference_requiresExplicitChoice() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            externalId = null,
            title = "Atendimento",
            clientName = "Cliente",
            unitName = "Unidade",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"old\"",
                rawIcs = "old",
                rawSummary = "PIER - ???? - Atendimento",
                rawDescription = "Demanda"
            )
        )
        val differences = ServiceOrderDiff.computeDifferences(
            localOrder = order,
            remoteExternalId = "15455",
            remoteDemand = order.originalDemand,
            remoteTitle = order.title
        )

        assertEquals(ConflictField.EXTERNAL_ID, differences.first().field)

        val accepted = ServiceOrderDiff.applyChoices(
            localOrder = order,
            choices = mapOf(ConflictField.EXTERNAL_ID to FieldChoice.USE_REMOTE),
            remoteExternalId = "15455",
            remoteDemand = order.originalDemand,
            remoteTitle = order.title,
            remoteCause = null,
            remoteSolution = null,
            remotePending = null,
            newEtag = "\"new\"",
            remoteRawSummary = "PIER - 15455 - Atendimento",
            remoteRawDescription = "Demanda",
            remoteRawIcs = "new"
        )

        assertEquals("15455", accepted.externalId)
        assertEquals("PIER - 15455 - Atendimento", accepted.baseSnapshot?.rawSummary)
        assertEquals("new", accepted.baseSnapshot?.rawIcs)
    }

    @Test
    fun computeDifferences_ignoresLocalOnlyChangesWhenRemoteStillMatchesBase() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            title = "Meu título local",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Minha demanda local",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"old\"",
                rawIcs = "old",
                rawSummary = "Título do calendário",
                rawDescription = "Demanda do calendário"
            )
        )

        val differences = ServiceOrderDiff.computeDifferences(
            localOrder = order,
            remoteDemand = "Demanda do calendário",
            remoteTitle = "Título do calendário"
        )

        assertTrue(differences.isEmpty())
    }

    @Test
    fun computeDifferences_ignoresRemoteChangeAlreadyReflectedLocally() {
        val order = StructuredServiceOrder(
            id = UUID.randomUUID(),
            title = "Título conciliado",
            clientName = "Cliente",
            unitName = "Unidade",
            originalDemand = "Demanda conciliada",
            baseSnapshot = RemoteBaseSnapshot(
                etag = "\"old\"",
                rawIcs = "old",
                rawSummary = "Título antigo",
                rawDescription = "Demanda antiga"
            )
        )

        val differences = ServiceOrderDiff.computeDifferences(
            localOrder = order,
            remoteDemand = "Demanda conciliada",
            remoteTitle = "Título conciliado"
        )

        assertTrue(differences.isEmpty())
    }
}
