package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderItem
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderPreset
import dev.claudiocodigo.nexo.domain.serviceorder.ServiceOrderUpdate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
fun PresetSection(
    currentPreset: ServiceOrderPreset,
    onPresetSelect: (ServiceOrderPreset) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentPreset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO,
            onClick = { onPresetSelect(ServiceOrderPreset.DIAGNOSTICO_CORRECAO) },
            label = { Text("Diagnóstico / Correção") },
            leadingIcon = if (currentPreset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO) {
                { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
            } else null
        )
        FilterChip(
            selected = currentPreset == ServiceOrderPreset.SERVICO_SOLICITADO,
            onClick = { onPresetSelect(ServiceOrderPreset.SERVICO_SOLICITADO) },
            label = { Text("Serviço Solicitado") },
            leadingIcon = if (currentPreset == ServiceOrderPreset.SERVICO_SOLICITADO) {
                { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
            } else null
        )
    }
}

@Composable
fun IdentificationSection(
    externalId: String,
    title: String,
    clientName: String,
    unitName: String,
    technician: String,
    category: String,
    onExternalIdChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onUnitNameChange: (String) -> Unit,
    onTechnicianChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = externalId,
        onValueChange = onExternalIdChange,
        label = { Text("Número da OS Oficial (opcional)") },
        placeholder = { Text("Ex: 15428 (deixe vazio se provisória)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChange,
        label = { Text("Título do Atendimento *") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = clientName,
            onValueChange = onClientNameChange,
            label = { Text("Cliente / Empresa *") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = unitName,
            onValueChange = onUnitNameChange,
            label = { Text("Unidade / Local *") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = technician,
            onValueChange = onTechnicianChange,
            label = { Text("Técnico") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = category,
            onValueChange = onCategoryChange,
            label = { Text("Categoria") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
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

@Composable
fun CompletionSection(
    preset: ServiceOrderPreset,
    closureCause: String,
    closureSolution: String,
    closurePending: String,
    onCauseChange: (String) -> Unit,
    onSolutionChange: (String) -> Unit,
    onPendingChange: (String) -> Unit
) {
    Column {
        if (preset == ServiceOrderPreset.DIAGNOSTICO_CORRECAO) {
            OutlinedTextField(
                value = closureCause,
                onValueChange = onCauseChange,
                label = { Text("Causa Identificada *") },
                placeholder = { Text("Ex: Desgaste natural das baterias.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val solLabel = if (preset == ServiceOrderPreset.SERVICO_SOLICITADO) "Ação Executada *" else "Solução Aplicada *"
        OutlinedTextField(
            value = closureSolution,
            onValueChange = onSolutionChange,
            label = { Text(solLabel) },
            placeholder = { Text("Ex: Substituído banco de 4 baterias e testado inversor.") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = closurePending,
            onValueChange = onPendingChange,
            label = { Text("Pendências Finais") },
            placeholder = { Text("Deixe em branco ou digite 'Nenhuma'") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
