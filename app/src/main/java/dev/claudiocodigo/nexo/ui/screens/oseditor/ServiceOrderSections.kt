package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderFlow
import dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun StepperIndicator(
    activeSteps: List<EditorStep>,
    currentStep: EditorStep,
    onStepClick: (EditorStep) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentIndex = activeSteps.indexOf(currentStep).coerceAtLeast(0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        activeSteps.forEachIndexed { index, step ->
            val isSelected = step == currentStep
            val isDone = index < currentIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onStepClick(step) }
                    .padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isDone -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isDone) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Recolher" else "Expandir"
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun FlowSection(
    currentFlow: ServiceOrderFlow,
    allowUpdate: Boolean,
    onFlowSelect: (ServiceOrderFlow) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Fluxo do atendimento", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = currentFlow == ServiceOrderFlow.RESOLUTION, onClick = { onFlowSelect(ServiceOrderFlow.RESOLUTION) }, label = { Text("Resolução") }, modifier = Modifier.testTag("chip_flow_resolution"))
            FilterChip(selected = currentFlow == ServiceOrderFlow.REQUEST, onClick = { onFlowSelect(ServiceOrderFlow.REQUEST) }, label = { Text("Solicitação") }, modifier = Modifier.testTag("chip_flow_request"))
            if (allowUpdate) {
                FilterChip(selected = currentFlow == ServiceOrderFlow.UPDATE, onClick = { onFlowSelect(ServiceOrderFlow.UPDATE) }, label = { Text("Atualização") }, modifier = Modifier.testTag("chip_flow_update"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdentificationSection(
    externalId: String,
    title: String,
    clientName: String,
    unitName: String,
    technician: String,
    category: String,
    categorySuggestions: List<String> = emptyList(),
    recentCategory: String? = null,
    recentTechnician: String? = null,
    onExternalIdChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onUnitNameChange: (String) -> Unit,
    onTechnicianChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    var categoryDropdownOpen by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = externalId,
        onValueChange = onExternalIdChange,
        label = { Text("Número da OS Oficial (opcional)") },
        placeholder = { Text("Ex: 15428 (deixe vazio se provisória)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("field_external_id")
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Título do Atendimento *") },
        placeholder = { Text("Ex: REVISAR CÂMERAS") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("field_title")
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = clientName,
            onValueChange = onClientNameChange,
            label = { Text("Cliente / Empresa *") },
            placeholder = { Text("Ex: PIER") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("field_client_name")
        )
        OutlinedTextField(
            value = unitName,
            onValueChange = onUnitNameChange,
            label = { Text("Local / Unidade *") },
            placeholder = { Text("Ex: Armazém 5") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("field_unit_name")
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = technician,
            onValueChange = onTechnicianChange,
            label = { Text("Técnico") },
            placeholder = { Text("Ex: Claudio") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("field_technician")
        )
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = category,
                onValueChange = onCategoryChange,
                label = { Text("Categoria") },
                placeholder = { Text("Ex: CFTV") },
                singleLine = true,
                trailingIcon = if (categorySuggestions.isNotEmpty()) {
                    {
                        IconButton(onClick = { categoryDropdownOpen = !categoryDropdownOpen }) {
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Sugestões de Categoria")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().testTag("field_category")
            )
            DropdownMenu(
                expanded = categoryDropdownOpen,
                onDismissRequest = { categoryDropdownOpen = false }
            ) {
                categorySuggestions.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = {
                            onCategoryChange(cat)
                            categoryDropdownOpen = false
                        }
                    )
                }
            }
        }
    }

    // Quick chips for suggestions
    if (categorySuggestions.isNotEmpty() || recentTechnician != null || recentCategory != null) {
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            recentTechnician?.takeIf { it.isNotBlank() && it != technician }?.let { recTech ->
                AssistChip(
                    onClick = { onTechnicianChange(recTech) },
                    label = { Text("Técnico: $recTech", style = MaterialTheme.typography.labelSmall) }
                )
            }
            recentCategory?.takeIf { it.isNotBlank() && it != category }?.let { recCat ->
                AssistChip(
                    onClick = { onCategoryChange(recCat) },
                    label = { Text("Cat: $recCat", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
fun UpdatesSection(
    updates: List<ServiceOrderUpdate>,
    onAddUpdate: (String) -> Unit,
    onRemoveUpdate: (UUID) -> Unit
) {
    var newUpdateText by remember { mutableStateOf("") }
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    Column {
        if (updates.isNotEmpty()) {
            for (update in updates) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatter.format(Date(update.executionDate)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(text = update.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onRemoveUpdate(update.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remover atualização")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = newUpdateText,
            onValueChange = { newUpdateText = it },
            label = { Text("Nova atualização de campo...") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (newUpdateText.isNotBlank()) {
                    onAddUpdate(newUpdateText)
                    newUpdateText = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Adicionar Atualização")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompletionSection(
    preset: ServiceOrderPreset,
    closureCause: String,
    closureSolution: String,
    closurePending: String,
    conclusionState: ConclusionState,
    onConclusionStateChange: (ConclusionState) -> Unit,
    onCauseChange: (String) -> Unit,
    onSolutionChange: (String) -> Unit,
    onPendingChange: (String) -> Unit,
    observations: String = "",
    onObservationsChange: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Estado da Conclusão",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = conclusionState == ConclusionState.CONCLUIDO,
                onClick = { onConclusionStateChange(ConclusionState.CONCLUIDO) },
                label = { Text("Concluído") },
                leadingIcon = if (conclusionState == ConclusionState.CONCLUIDO) {
                    { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFF2E7D32)
                ),
                modifier = Modifier.testTag("chip_concluido")
            )

            FilterChip(
                selected = conclusionState == ConclusionState.NAO_CONCLUIDO,
                onClick = { onConclusionStateChange(ConclusionState.NAO_CONCLUIDO) },
                label = { Text("Não Concluído") },
                leadingIcon = if (conclusionState == ConclusionState.NAO_CONCLUIDO) {
                    { Icon(Icons.Rounded.ErrorOutline, contentDescription = null) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFC62828).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFFC62828)
                ),
                modifier = Modifier.testTag("chip_nao_concluido")
            )
        }

        if (conclusionState == ConclusionState.NAO_CONCLUIDO) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = true,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Atendimento não concluído. O parecer será enviado e aguardará validação externa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (preset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO) {
            OutlinedTextField(
                value = closureCause,
                onValueChange = onCauseChange,
                label = { Text("Causa Identificada *") },
                placeholder = { Text("Ex: N/A ou Desgaste natural das baterias.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("field_cause")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { onCauseChange("N/A") },
                    label = { Text("Preencher N/A") }
                )
            }
        }

        val solLabel = if (preset == ServiceOrderPreset.SERVICO_SOLICITADO) "Ação Executada *" else "Solução Aplicada *"
        OutlinedTextField(
            value = closureSolution,
            onValueChange = onSolutionChange,
            label = { Text(solLabel) },
            placeholder = { Text("Ex: Foi realizado a revisão do funcionamento e ângulo de todas as câmeras...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("field_solution")
        )

        OutlinedTextField(
            value = closurePending,
            onValueChange = onPendingChange,
            label = { Text("Pendências Finais") },
            placeholder = { Text("Deixe em branco ou digite 'Nenhuma'") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field_pending")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
                onClick = { onPendingChange("Nenhuma") },
                label = { Text("Preencher 'Nenhuma'") }
            )
        }

        OutlinedTextField(
            value = observations,
            onValueChange = onObservationsChange,
            label = { Text("Observações (opcional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("field_observations")
        )
    }
}

@Composable
fun TechnicalOpinionSection(
    opinion: TechnicalOpinion,
    observations: String,
    onOpinionChange: (TechnicalOpinion) -> Unit,
    onObservationsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Parecer técnico", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = opinion == TechnicalOpinion.CONCLUDED, onClick = { onOpinionChange(TechnicalOpinion.CONCLUDED) }, label = { Text("Concluído") }, modifier = Modifier.testTag("chip_opinion_concluded"))
            FilterChip(selected = opinion == TechnicalOpinion.NOT_CONCLUDED, onClick = { onOpinionChange(TechnicalOpinion.NOT_CONCLUDED) }, label = { Text("Não concluído") }, modifier = Modifier.testTag("chip_opinion_not_concluded"))
        }
        OutlinedTextField(value = observations, onValueChange = onObservationsChange, label = { Text("Observações (opcional)") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("field_observations"))
    }
}
