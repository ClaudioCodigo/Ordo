package dev.claudiocodigo.nexo.domain.model

import java.util.UUID

data class Equipment(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val model: String? = null,
    val serialNumber: String? = null,
    val type: EquipmentType,
    val clientId: UUID,
    val unitId: UUID,
    val locationDescription: String? = null
)

enum class EquipmentType {
    NOBREAK,
    BATERIA,
    OUTRO
}
