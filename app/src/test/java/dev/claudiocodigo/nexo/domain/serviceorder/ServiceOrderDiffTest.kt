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
}
