package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceOrderEditorScreen(
    orderId: UUID,
    onBack: () -> Unit,
    onNavigateToPreview: (UUID) -> Unit,
    onNavigateToSummaryExtraction: ((UUID) -> Unit)? = null,
    viewModel: ServiceOrderEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(orderId) {
        viewModel.loadOrder(orderId)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flushNow()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("screen_os_editor"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.isLinked) "Atendimento CalDAV" else "Nova OS Provisória",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (state.saveState) {
                                EditorSaveState.Idle -> ""
                                EditorSaveState.Saving -> "Salvando rascunho local..."
                                EditorSaveState.Saved -> "Salvo no aparelho"
                                is EditorSaveState.Error -> "Erro ao salvar rascunho"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.flushNow()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val action = state.applicableRemoteAction
                    Button(
                        onClick = {
                            if (viewModel.validateForPublication()) {
                                viewModel.saveBeforePublication(onNavigateToPreview)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_publish_preview")
                    ) {
                        val icon = when (action) {
                            OutboxAction.CREATE -> Icons.Rounded.CloudUpload
                            OutboxAction.UPDATE -> Icons.Rounded.Send
                            OutboxAction.FINALIZE -> Icons.Rounded.DoneAll
                        }
                        val label = when (action) {
                            OutboxAction.CREATE -> "Publicar no Calendário"
                            OutboxAction.UPDATE -> "Enviar Atualização"
                            OutboxAction.FINALIZE -> "Finalizar OS no Calendário"
                        }
                        Icon(icon, contentDescription = null)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(label)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            val validationErr = state.validationError
            if (validationErr != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = validationErr,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Text(
                text = "Preset Operacional",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            PresetSection(
                currentPreset = state.preset,
                onPresetSelect = viewModel::onPresetChange
            )
            Spacer(modifier = Modifier.height(10.dp))

            SectionContainer(title = "1. Identificação do Atendimento") {
                if (onNavigateToSummaryExtraction != null) {
                    OutlinedButton(
                        onClick = { onNavigateToSummaryExtraction(orderId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("btn_open_summary_assistant")
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Assistente de Extração do Resumo", style = MaterialTheme.typography.bodySmall)
                    }
                }
                IdentificationSection(
                    externalId = state.externalId,
                    title = state.title,
                    clientName = state.clientName,
                    unitName = state.unitName,
                    technician = state.technician,
                    category = state.category,
                    onExternalIdChange = viewModel::onExternalIdChange,
                    onTitleChange = viewModel::onTitleChange,
                    onClientNameChange = viewModel::onClientNameChange,
                    onUnitNameChange = viewModel::onUnitNameChange,
                    onTechnicianChange = viewModel::onTechnicianChange,
                    onCategoryChange = viewModel::onCategoryChange
                )
            }

            SectionContainer(title = "2. Demanda / Solicitação Original") {
                OutlinedTextField(
                    value = state.originalDemand,
                    onValueChange = viewModel::onDemandChange,
                    label = { Text("Descrição da demanda inicial *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SectionContainer(title = "3. Atualizações de Campo") {
                UpdatesSection(
                    updates = state.updates,
                    onAddUpdate = { text -> viewModel.addUpdate(text) },
                    onRemoveUpdate = viewModel::removeUpdate
                )
            }

            SectionContainer(title = "4. Finalização e Conclusão") {
                CompletionSection(
                    preset = state.preset,
                    closureCause = state.closureCause,
                    closureSolution = state.closureSolution,
                    closurePending = state.closurePending,
                    onCauseChange = {
                        viewModel.onClosureCauseChange(it)
                        viewModel.onStatusChange(ServiceOrderStatus.CONCLUIDA)
                    },
                    onSolutionChange = {
                        viewModel.onClosureSolutionChange(it)
                        viewModel.onStatusChange(ServiceOrderStatus.CONCLUIDA)
                    },
                    onPendingChange = viewModel::onClosurePendingChange
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}
