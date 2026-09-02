package dev.claudiocodigo.nexo.ui.screens.oseditor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Send
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.claudiocodigo.nexo.domain.publication.OutboxAction
import dev.claudiocodigo.nexo.domain.serviceorder.PublicationState
import java.util.UUID
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceOrderEditorScreen(
    orderId: UUID,
    onBack: () -> Unit,
    onNavigateToPreview: (UUID) -> Unit,
    viewModel: ServiceOrderEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showDiscardDialog by remember { mutableStateOf(false) }

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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar rascunho?") },
            text = { Text("Todas as alterações e informações desta ordem de serviço serão apagadas permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardDraft(onBack)
                    },
                    modifier = Modifier.testTag("btn_confirm_discard")
                ) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.testTag("screen_os_editor"),
        topBar = {
            TopAppBar(
                title = {
                    val currentStepNumber = state.activeSteps.indexOf(state.currentStep).coerceAtLeast(0) + 1
                    val totalSteps = state.activeSteps.size
                    Column {
                        Text(
                            text = if (state.isLinked) "Atendimento CalDAV" else "Nova OS Provisória",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = when (state.saveState) {
                                EditorSaveState.Idle -> "Passo $currentStepNumber de $totalSteps: ${state.currentStep.title}"
                                EditorSaveState.Saving -> "Salvando rascunho local..."
                                EditorSaveState.Saved -> "Salvo no aparelho • Passo $currentStepNumber/$totalSteps"
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
                },
                actions = {
                    if (state.publicationState == PublicationState.LOCAL_DRAFT) {
                        IconButton(
                            onClick = { showDiscardDialog = true },
                            modifier = Modifier.testTag("btn_discard_draft")
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Descartar Rascunho",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                val isFirstStep = state.activeSteps.firstOrNull() == state.currentStep
                val isLastStep = state.activeSteps.lastOrNull() == state.currentStep

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isFirstStep) {
                        OutlinedButton(
                            onClick = { viewModel.previousStep() },
                            modifier = Modifier
                                .weight(0.7f)
                                .height(50.dp)
                                .testTag("btn_step_prev")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Voltar")
                        }
                    }

                    if (!isLastStep) {
                        Button(
                            onClick = { viewModel.nextStep() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_step_next")
                        ) {
                            Text("Avançar")
                            Spacer(modifier = Modifier.size(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        val action = state.applicableRemoteAction
                        Button(
                            onClick = {
                                if (viewModel.validateForPublication()) {
                                    viewModel.saveBeforePublication(onNavigateToPreview)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_publish_preview")
                        ) {
                            val icon = when (action) {
                                OutboxAction.CREATE -> Icons.Rounded.CloudUpload
                                OutboxAction.UPDATE -> Icons.Rounded.Send
                                OutboxAction.FINALIZE -> Icons.Rounded.DoneAll
                            }
                            val label = when (action) {
                                OutboxAction.CREATE -> "Publicar OS"
                                OutboxAction.UPDATE -> "Enviar Atualização"
                                OutboxAction.FINALIZE -> "Finalizar OS"
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(label, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StepperIndicator(
                activeSteps = state.activeSteps,
                currentStep = state.currentStep,
                onStepClick = { viewModel.goToStep(it) }
            )

            val validationErr = state.validationError
            if (validationErr != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = validationErr,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AnimatedContent(
                targetState = state.currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step_content",
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) { step ->
                when (step) {
                    EditorStep.IDENTIFICACAO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "1. IDENTIFICAÇÃO DO ATENDIMENTO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            FlowSection(
                                currentFlow = state.flow,
                                allowUpdate = state.isLinked,
                                onFlowSelect = viewModel::onFlowChange
                            )

                            if (!state.isLinked) {
                                ScheduleSection(
                                    start = state.scheduledStart,
                                    end = state.scheduledEnd,
                                    onStartChange = viewModel::onScheduledStartChange,
                                    onEndChange = viewModel::onScheduledEndChange
                                )
                            }

                            IdentificationSection(
                                externalId = state.externalId,
                                title = state.title,
                                clientName = state.clientName,
                                unitName = state.unitName,
                                technician = state.technician,
                                category = state.category,
                                categorySuggestions = state.categorySuggestions,
                                recentCategory = state.recentCategorySuggestion,
                                recentTechnician = state.recentTechnicianSuggestion,
                                onExternalIdChange = viewModel::onExternalIdChange,
                                onTitleChange = viewModel::onTitleChange,
                                onClientNameChange = viewModel::onClientNameChange,
                                onUnitNameChange = viewModel::onUnitNameChange,
                                onTechnicianChange = viewModel::onTechnicianChange,
                                onCategoryChange = viewModel::onCategoryChange
                            )
                        }
                    }

                    EditorStep.DEMANDA -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "2. DEMANDA / SOLICITAÇÃO ORIGINAL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = state.originalDemand,
                                onValueChange = viewModel::onDemandChange,
                                label = { Text("Descrição detalhada da demanda ou solicitação *") },
                                placeholder = { Text("Ex: REVISAR TODAS AS CÂMERAS DO ARM 5") },
                                minLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("field_demand")
                            )
                        }
                    }

                    EditorStep.ATUALIZACAO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "2. ATUALIZAÇÕES DE CAMPO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = state.updateDraft,
                                onValueChange = viewModel::onUpdateDraftChange,
                                label = { Text("Texto da atualização *") },
                                placeholder = { Text("Ex: Equipamento testado e liberado.") },
                                minLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("field_update_draft")
                            )

                            Text(
                                text = "A atualização será publicada preservando o histórico do atendimento anterior.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            TechnicalOpinionSection(
                                opinion = state.technicalOpinion,
                                observations = state.observations,
                                onOpinionChange = viewModel::onTechnicalOpinionChange,
                                onObservationsChange = viewModel::onObservationsChange
                            )
                        }
                    }

                    EditorStep.CONCLUSAO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "3. FINALIZAÇÃO E CONCLUSÃO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            CompletionSection(
                                preset = state.preset,
                                closureCause = state.closureCause,
                                closureSolution = state.closureSolution,
                                closurePending = state.closurePending,
                                conclusionState = state.conclusionState,
                                onConclusionStateChange = { selected ->
                                    viewModel.onTechnicalOpinionChange(
                                        if (selected == dev.claudiocodigo.nexo.domain.serviceorder.ConclusionState.NAO_CONCLUIDO) dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion.NOT_CONCLUDED else dev.claudiocodigo.nexo.domain.serviceorder.TechnicalOpinion.CONCLUDED
                                    )
                                },
                                onCauseChange = viewModel::onClosureCauseChange,
                                onSolutionChange = viewModel::onClosureSolutionChange,
                                onPendingChange = viewModel::onClosurePendingChange,
                                observations = state.observations,
                                onObservationsChange = viewModel::onObservationsChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleSection(
    start: Long?,
    end: Long?,
    onStartChange: (Long?) -> Unit,
    onEndChange: (Long?) -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    fun display(value: Long?) = value?.let { formatter.format(Date(it)) }.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agendamento da OS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        DateTimePickerField(
            label = "Início *",
            valueMillis = start,
            onValueChange = onStartChange,
            testTag = "field_scheduled_start"
        )

        DateTimePickerField(
            label = "Término *",
            valueMillis = end,
            onValueChange = onEndChange,
            testTag = "field_scheduled_end"
        )
        
        Text("Faixa normal: 06:00–19:00. Fora dela o app apenas avisa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerField(
    label: String,
    valueMillis: Long?,
    onValueChange: (Long) -> Unit,
    testTag: String
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))
    val textValue = valueMillis?.let { formatter.format(Date(it)) } ?: "Selecionar Data e Hora"

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = valueMillis ?: System.currentTimeMillis()
    )

    val initialCalendar = remember(valueMillis) {
        java.util.Calendar.getInstance().apply {
            if (valueMillis != null) timeInMillis = valueMillis
        }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = initialCalendar.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = initialCalendar.get(java.util.Calendar.MINUTE),
        is24Hour = true
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = textValue,
            onValueChange = { },
            label = { Text(label) },
            singleLine = true,
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    showDatePicker = true
                }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text("Avançar para Horário")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        Dialog(
            onDismissRequest = { showTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .background(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        text = "Selecione o Horário",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TimePicker(state = timePickerState)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar")
                        }
                        TextButton(
                            onClick = {
                                val selectedDate = pendingDateMillis ?: datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                                val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                    timeInMillis = selectedDate
                                }
                                val localCal = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.YEAR, utcCal.get(java.util.Calendar.YEAR))
                                    set(java.util.Calendar.MONTH, utcCal.get(java.util.Calendar.MONTH))
                                    set(java.util.Calendar.DAY_OF_MONTH, utcCal.get(java.util.Calendar.DAY_OF_MONTH))
                                    set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(java.util.Calendar.MINUTE, timePickerState.minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }
                                onValueChange(localCal.timeInMillis)
                                showTimePicker = false
                            }
                        ) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
}
