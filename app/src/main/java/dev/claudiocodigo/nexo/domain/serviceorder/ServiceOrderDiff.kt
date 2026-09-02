package dev.claudiocodigo.nexo.domain.serviceorder

enum class ConflictField {
    EXTERNAL_ID,
    COMPANY,
    LOCATION,
    TECHNICIAN,
    CATEGORY,
    TITLE,
    ORIGINAL_DEMAND,
    CAUSE,
    SOLUTION,
    PENDING,
    OBSERVATIONS,
    CONTENT
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

data class RemoteChangeAnalysis(
    val differences: List<FieldDifference>,
    val hasUnmappedRemoteTextChange: Boolean
)

/**
 * Pure semantic difference computer and choice applicator for 412 conflicts.
 *
 * Rules:
 * - Compares structured fields rather than raw line-by-line diffs;
 * - Presents only fields changed from base to remote that still differ from the local draft;
 * - Reports meaningful raw text changes that the supported fields cannot represent;
 * - Applying choices returns a new immutable StructuredServiceOrder with updated base ETag.
 */
object ServiceOrderDiff {

    fun computeDifferences(
        localOrder: StructuredServiceOrder,
        remoteExternalId: String? = null,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String? = null,
        remoteSolution: String? = null,
        remotePending: String? = null,
        remoteClientName: String? = null,
        remoteUnitName: String? = null,
        remoteTechnician: String? = null,
        remoteCategory: String? = null
    ): List<FieldDifference> = analyzeRemoteChange(
        localOrder = localOrder,
        remoteExternalId = remoteExternalId,
        remoteDemand = remoteDemand,
        remoteTitle = remoteTitle,
        remoteCause = remoteCause,
        remoteSolution = remoteSolution,
        remotePending = remotePending,
        inspectRawText = false,
        remoteRawSummary = null,
        remoteRawDescription = null,
        remoteClientName = remoteClientName,
        remoteUnitName = remoteUnitName,
        remoteTechnician = remoteTechnician,
        remoteCategory = remoteCategory
    ).differences

    fun analyzeRemoteChange(
        localOrder: StructuredServiceOrder,
        remoteExternalId: String? = null,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String? = null,
        remoteSolution: String? = null,
        remotePending: String? = null,
        remoteRawSummary: String? = null,
        remoteRawDescription: String? = null,
        remoteClientName: String? = null,
        remoteUnitName: String? = null,
        remoteTechnician: String? = null,
        remoteCategory: String? = null
    ): RemoteChangeAnalysis = analyzeRemoteChange(
        localOrder = localOrder,
        remoteExternalId = remoteExternalId,
        remoteDemand = remoteDemand,
        remoteTitle = remoteTitle,
        remoteCause = remoteCause,
        remoteSolution = remoteSolution,
        remotePending = remotePending,
        inspectRawText = true,
        remoteRawSummary = remoteRawSummary,
        remoteRawDescription = remoteRawDescription,
        remoteClientName = remoteClientName,
        remoteUnitName = remoteUnitName,
        remoteTechnician = remoteTechnician,
        remoteCategory = remoteCategory
    )

    private fun analyzeRemoteChange(
        localOrder: StructuredServiceOrder,
        remoteExternalId: String?,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String?,
        remoteSolution: String?,
        remotePending: String?,
        inspectRawText: Boolean,
        remoteRawSummary: String?,
        remoteRawDescription: String?,
        remoteClientName: String?,
        remoteUnitName: String?,
        remoteTechnician: String?,
        remoteCategory: String?
    ): RemoteChangeAnalysis {
        val diffs = mutableListOf<FieldDifference>()
        val baseSummary = ServiceOrderExtractor.extractSummary(localOrder.baseSnapshot?.rawSummary)
        val baseDescription = ServiceOrderExtractor.extractDescription(localOrder.baseSnapshot?.rawDescription)
        val baseExternalId = baseSummary.externalId ?: baseDescription.externalId
        val remoteSummary = ServiceOrderExtractor.extractSummary(remoteRawSummary)
        val remoteDescription = ServiceOrderExtractor.extractDescription(remoteRawDescription)
        val effectiveExternalId = remoteExternalId ?: remoteSummary.externalId ?: remoteDescription.externalId
        val effectiveClient = remoteClientName ?: remoteSummary.clientName
        val effectiveUnit = remoteUnitName ?: remoteSummary.unitName
        val effectiveTechnician = remoteTechnician ?: remoteSummary.technician ?: remoteDescriptionTechnician(remoteRawDescription)
        val effectiveCategory = remoteCategory ?: remoteSummary.category

        val externalIdChangedRemotely = normalized(baseExternalId) != normalized(effectiveExternalId)
        val localNumberIsProvisional = normalized(localOrder.externalId).let {
            it == null || it.equals("????", true) || it.equals("SEM OS", true)
        }
        val legacyMissingOfficialId = localNumberIsProvisional && normalized(effectiveExternalId) != null
        val titleChangedRemotely = normalized(baseSummary.title) != normalized(remoteTitle)
        val demandChangedRemotely = normalized(baseDescription.originalDemand) != normalized(remoteDemand)
        val causeChangedRemotely = normalized(baseDescription.closureCause) != normalized(remoteCause)
        val solutionChangedRemotely = normalized(baseDescription.closureSolution) != normalized(remoteSolution)
        val pendingChangedRemotely = normalized(baseDescription.closurePending) != normalized(remotePending)
        val observationsChangedRemotely = normalized(baseDescription.observations) != normalized(remoteDescription.observations)
        val companyChangedRemotely = normalized(baseSummary.clientName) != normalized(effectiveClient)
        val locationChangedRemotely = normalized(baseSummary.unitName) != normalized(effectiveUnit)
        val technicianChangedRemotely = normalized(baseSummary.technician) != normalized(effectiveTechnician)
        val categoryChangedRemotely = normalized(baseSummary.category) != normalized(effectiveCategory)

        if ((externalIdChangedRemotely || legacyMissingOfficialId) &&
            normalized(localOrder.externalId) != normalized(effectiveExternalId)
        ) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.EXTERNAL_ID,
                    label = "Número oficial da OS",
                    baseValue = baseExternalId,
                    localValue = localOrder.externalId,
                    remoteValue = effectiveExternalId
                )
            )
        }

        if (titleChangedRemotely && normalized(localOrder.title) != normalized(remoteTitle)) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.TITLE,
                    label = "Título do Atendimento",
                    baseValue = baseSummary.title,
                    localValue = localOrder.title,
                    remoteValue = remoteTitle
                )
            )
        }

        fun add(field: ConflictField, label: String, base: String?, local: String?, remote: String?, changed: Boolean) {
            if (changed && normalized(local) != normalized(remote)) diffs += FieldDifference(field, label, base, local, remote)
        }
        add(ConflictField.COMPANY, "Empresa", baseSummary.clientName, localOrder.clientName, effectiveClient, companyChangedRemotely)
        add(ConflictField.LOCATION, "Local", baseSummary.unitName, localOrder.unitName, effectiveUnit, locationChangedRemotely)
        add(ConflictField.TECHNICIAN, "Técnico", baseSummary.technician, localOrder.technician, effectiveTechnician, technicianChangedRemotely)
        add(ConflictField.CATEGORY, "Categoria", baseSummary.category, localOrder.category, effectiveCategory, categoryChangedRemotely)
        add(ConflictField.OBSERVATIONS, "Observações", baseDescription.observations, localOrder.observations, remoteDescription.observations, observationsChangedRemotely)

        if (demandChangedRemotely && normalized(localOrder.originalDemand) != normalized(remoteDemand)) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.ORIGINAL_DEMAND,
                    label = "Demanda / Solicitação Original",
                    baseValue = baseDescription.originalDemand,
                    localValue = localOrder.originalDemand,
                    remoteValue = remoteDemand
                )
            )
        }

        if (causeChangedRemotely &&
            normalized(localOrder.closureCause) != normalized(remoteCause)
        ) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.CAUSE,
                    label = "Causa Identificada",
                    baseValue = baseDescription.closureCause,
                    localValue = localOrder.closureCause,
                    remoteValue = remoteCause
                )
            )
        }

        if (solutionChangedRemotely &&
            normalized(localOrder.closureSolution) != normalized(remoteSolution)
        ) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.SOLUTION,
                    label = "Solução / Ação Realizada",
                    baseValue = baseDescription.closureSolution,
                    localValue = localOrder.closureSolution,
                    remoteValue = remoteSolution
                )
            )
        }

        if (pendingChangedRemotely &&
            normalized(localOrder.closurePending) != normalized(remotePending)
        ) {
            diffs.add(
                FieldDifference(
                    field = ConflictField.PENDING,
                    label = "Pendências",
                    baseValue = baseDescription.closurePending,
                    localValue = localOrder.closurePending,
                    remoteValue = remotePending
                )
            )
        }

        val hasUnmappedRemoteTextChange = if (inspectRawText) {
            val summaryTextChanged = canonicalText(localOrder.baseSnapshot?.rawSummary) != canonicalText(remoteRawSummary)
            val descriptionTextChanged = canonicalText(localOrder.baseSnapshot?.rawDescription) != canonicalText(remoteRawDescription)
            val summaryChangeRepresented = normalized(baseSummary.externalId) != normalized(remoteSummary.externalId) ||
                normalized(baseSummary.clientName) != normalized(remoteSummary.clientName) ||
                normalized(baseSummary.unitName) != normalized(remoteSummary.unitName) ||
                normalized(baseSummary.technician) != normalized(remoteSummary.technician) ||
                normalized(baseSummary.category) != normalized(remoteSummary.category) ||
                normalized(baseSummary.title) != normalized(remoteSummary.title)
            val descriptionChangeRepresented = normalized(baseDescription.externalId) != normalized(remoteDescription.externalId) ||
                normalized(baseDescription.originalDemand) != normalized(remoteDescription.originalDemand) ||
                normalized(baseDescription.closureCause) != normalized(remoteDescription.closureCause) ||
                normalized(baseDescription.closureSolution) != normalized(remoteDescription.closureSolution) ||
                normalized(baseDescription.closurePending) != normalized(remoteDescription.closurePending) ||
                normalized(baseDescription.observations) != normalized(remoteDescription.observations)
            val unsupportedDescriptionChange = baseDescription.preset != remoteDescription.preset ||
                baseDescription.updates.map { normalized(it.text) } != remoteDescription.updates.map { normalized(it.text) }

            (summaryTextChanged && !summaryChangeRepresented) ||
                (descriptionTextChanged && (!descriptionChangeRepresented || unsupportedDescriptionChange))
        } else {
            false
        }

        return RemoteChangeAnalysis(
            differences = diffs,
            hasUnmappedRemoteTextChange = hasUnmappedRemoteTextChange
        )
    }

    private fun normalized(value: String?): String? = value?.trim()?.ifEmpty { null }

    private fun canonicalText(value: String?): String = value.orEmpty()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .trim()

    fun applyChoices(
        localOrder: StructuredServiceOrder,
        choices: Map<ConflictField, FieldChoice>,
        remoteExternalId: String? = null,
        remoteDemand: String,
        remoteTitle: String,
        remoteCause: String?,
        remoteSolution: String?,
        remotePending: String?,
        newEtag: String?,
        remoteRawSummary: String? = null,
        remoteRawDescription: String? = null,
        remoteRawIcs: String? = null,
        remoteClientName: String? = null,
        remoteUnitName: String? = null,
        remoteTechnician: String? = null,
        remoteCategory: String? = null
    ): StructuredServiceOrder {
        var updated = localOrder
        val remoteSummary = ServiceOrderExtractor.extractSummary(remoteRawSummary)
        val remoteDescriptionInfo = ServiceOrderExtractor.extractDescription(remoteRawDescription)
        val effectiveExternalId = remoteExternalId ?: remoteSummary.externalId ?: remoteDescriptionInfo.externalId

        choices.forEach { (field, choice) ->
            if (choice == FieldChoice.USE_REMOTE) {
                updated = when (field) {
                    ConflictField.EXTERNAL_ID -> updated.copy(externalId = effectiveExternalId)
                    ConflictField.COMPANY -> updated.copy(clientName = remoteClientName ?: remoteSummary.clientName ?: updated.clientName)
                    ConflictField.LOCATION -> updated.copy(unitName = remoteUnitName ?: remoteSummary.unitName ?: updated.unitName)
                    ConflictField.TECHNICIAN -> updated.copy(technician = remoteTechnician ?: remoteSummary.technician ?: remoteDescriptionInfo.technician)
                    ConflictField.CATEGORY -> updated.copy(category = remoteCategory ?: remoteSummary.category ?: updated.category)
                    ConflictField.TITLE -> updated.copy(title = remoteTitle)
                    ConflictField.ORIGINAL_DEMAND -> updated.copy(originalDemand = remoteDemand)
                    ConflictField.CAUSE -> updated.copy(closureCause = remoteCause)
                    ConflictField.SOLUTION -> updated.copy(closureSolution = remoteSolution)
                    ConflictField.PENDING -> updated.copy(closurePending = remotePending)
                    ConflictField.OBSERVATIONS -> updated.copy(observations = remoteDescriptionInfo.observations)
                    ConflictField.CONTENT -> updated
                }
            }
        }

        val existingSnapshot = updated.baseSnapshot
        val updatedSnapshot = existingSnapshot?.copy(
            etag = newEtag ?: existingSnapshot.etag,
            rawIcs = remoteRawIcs ?: existingSnapshot.rawIcs,
            rawSummary = remoteRawSummary ?: remoteTitle,
            rawDescription = remoteRawDescription ?: remoteDemand,
            capturedAt = System.currentTimeMillis()
        )

        return updated.copy(
            baseSnapshot = updatedSnapshot,
            publicationState = PublicationState.LOCAL_DRAFT
        )
    }

    fun isOnlyOfficialNumberChange(differences: List<FieldDifference>): Boolean =
        differences.isNotEmpty() && differences.all { it.field == ConflictField.EXTERNAL_ID }

    private fun remoteDescriptionTechnician(raw: String?): String? =
        ServiceOrderExtractor.extractDescription(raw).technician
}
