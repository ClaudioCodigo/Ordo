package dev.claudiocodigo.nexo.domain.serviceorder

enum class ConflictField {
    TITLE,
    ORIGINAL_DEMAND,
    CAUSE,
    SOLUTION,
    PENDING
}

enum class FieldChoice {
    KEEP_LOCAL,
    USE_REMOTE
}

data class FieldDifference(
    val field: ConflictField,
    val label: String,
    val baseValue: String?,
    val localValue: String?,
    val remoteValue: String?
)

/**
 * Pure semantic difference computer and choice applicator for 412 conflicts.
 *
 * Rules:
 * - Compares structured fields rather than raw line-by-line diffs;
 * - Only fields that actually differ between local and remote are presented;
 * - Applying choices returns a new immutable StructuredServiceOrder with updated base ETag.
 */
object ServiceOrderDiff {

    fun computeDifferences(
        localOrder: StructuredServiceOrder,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String? = null,
        remoteSolution: String? = null,
        remotePending: String? = null
    ): List<FieldDifference> {
        val diffs = mutableListOf<FieldDifference>()

        if (localOrder.title.trim() != remoteTitle.trim()) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.TITLE,
                    label = "Título do Atendimento",
                    baseValue = localOrder.baseSnapshot?.rawSummary,
                    localValue = localOrder.title,
                    remoteValue = remoteTitle
                )
            )
        }

        if (localOrder.originalDemand.trim() != remoteDemand.trim()) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.ORIGINAL_DEMAND,
                    label = "Demanda / Solicitação Original",
                    baseValue = localOrder.baseSnapshot?.rawDescription,
                    localValue = localOrder.originalDemand,
                    remoteValue = remoteDemand
                )
            )
        }

        if (remoteCause != null && localOrder.closureCause?.trim() != remoteCause.trim()) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.CAUSE,
                    label = "Causa Identificada",
                    baseValue = null,
                    localValue = localOrder.closureCause,
                    remoteValue = remoteCause
                )
            )
        }

        if (remoteSolution != null && localOrder.closureSolution?.trim() != remoteSolution.trim()) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.SOLUTION,
                    label = "Solução / Ação Realizada",
                    baseValue = null,
                    localValue = localOrder.closureSolution,
                    remoteValue = remoteSolution
                )
            )
        }

        if (remotePending != null && localOrder.closurePending?.trim() != remotePending.trim()) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.PENDING,
                    label = "Pendências",
                    baseValue = null,
                    localValue = localOrder.closurePending,
                    remoteValue = remotePending
                )
            )
        }

        return diffs
    }

    fun applyChoices(
        localOrder: StructuredServiceOrder,
        choices: Map<ConflictField, FieldChoice>,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String?,
        remoteSolution: String?,
        remotePending: String?,
        newEtag: String?
    ): StructuredServiceOrder {
        var updated = localOrder

        choices.forEach { (field, choice) ->
            if (choice == FieldChoice.USE_REMOTE) {
                updated = when (field) {
                    ConflictField.TITLE -> updated.copy(title = remoteTitle)
                    ConflictField.ORIGINAL_DEMAND -> updated.copy(originalDemand = remoteDemand)
                    ConflictField.CAUSE -> updated.copy(closureCause = remoteCause)
                    ConflictField.SOLUTION -> updated.copy(closureSolution = remoteSolution)
                    ConflictField.PENDING -> updated.copy(closurePending = remotePending)
                }
            }
        }

        val updatedSnapshot = updated.baseSnapshot?.copy(
            etag = newEtag ?: updated.baseSnapshot.etag,
            rawSummary = remoteTitle,
            rawDescription = remoteDemand,
            capturedAt = System.currentTimeMillis()
        )

        return updated.copy(
            baseSnapshot = updatedSnapshot,
            publicationState = PublicationState.LOCAL_DRAFT
        )
    }
}
