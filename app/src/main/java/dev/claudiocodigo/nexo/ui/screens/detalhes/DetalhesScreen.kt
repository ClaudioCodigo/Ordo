package dev.claudiocodigo.nexo.ui.screens.detalhes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalhesScreen(
    osId: String,
    onBack: () -> Unit,
    viewModel: DetalhesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var leaving by remember { mutableStateOf(false) }

    fun requestExit() {
        if (!leaving) {
            leaving = true
            viewModel.saveBeforeExit(
                onSaved = onBack,
                onFailed = { leaving = false }
            )
        }
    }

    BackHandler { requestExit() }

    LaunchedEffect(osId) { viewModel.loadServiceOrder(osId) }

    DisposableEffect(lifecycleOwner, osId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushNow()
        }
    }

    val success = uiState as? DetalhesUiState.Success
    Scaffold(
        modifier = Modifier.testTag("screen_detalhes"),
        topBar = {
            TopAppBar(
                title = { Text("Detalhes da OS") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit, enabled = !leaving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {}
            )
        }
    ) { padding ->
        when (val state = uiState) {
            DetalhesUiState.Loading -> Text("Carregando…", modifier = Modifier.padding(padding))
            is DetalhesUiState.Error -> Text(text = state.message, modifier = Modifier.padding(padding))
            is DetalhesUiState.Success -> DetalhesContent(
                padding = padding,
                state = state,
                onTitleChange = viewModel::updateTitle,
                onExternalIdChange = viewModel::updateExternalId,
                onClientNameChange = viewModel::updateClientName,
                onUnitNameChange = viewModel::updateUnitName,
                onDescriptionChange = viewModel::updateDescription,
                onSaveDraft = viewModel::saveDraft,
                onFinish = if (state.os.status == ServiceOrderStatus.CONCLUIDA) {
                    viewModel::requestReopen
                } else {
                    viewModel::requestFinish
                }
            )
        }
    }

    success?.pendingAction?.let { action ->
        val finishing = action == DetalhesAction.FINALIZAR
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingAction,
            title = { Text(if (finishing) "Finalizar OS localmente?" else "Reabrir OS localmente?") },
            text = {
                Text(
                    if (finishing) {
                        "O status será marcado como concluído somente neste aparelho. Ainda não será publicado no calendário."
                    } else {
                        "O status voltará para Em andamento somente neste aparelho. Ainda não será publicado no calendário."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPendingAction) {
                    Text(if (finishing) "Finalizar localmente" else "Reabrir localmente")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPendingAction) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun DetalhesContent(
    padding: PaddingValues,
    state: DetalhesUiState.Success,
    onExternalIdChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onClientNameChange: (String) -> Unit,
    onUnitNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onFinish: () -> Unit
) {
    val os = state.os
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InfoSection("Informações Gerais") {
            OutlinedTextField(
                value = os.externalId.orEmpty(),
                onValueChange = onExternalIdChange,
                label = { Text("Número da OS") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = false
            )
            OutlinedTextField(
                value = os.title,
                onValueChange = onTitleChange,
                label = { Text("Título") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = os.clientName,
                onValueChange = onClientNameChange,
                label = { Text("Cliente") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = false
            )
            OutlinedTextField(
                value = os.unitName,
                onValueChange = onUnitNameChange,
                label = { Text("Unidade") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = false
            )
        }

        InfoSection("Execução") {
            OutlinedTextField(
                value = os.description,
                onValueChange = onDescriptionChange,
                label = { Text("Demanda/Descrição") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Text(
                text = "Data prevista: ${os.scheduledDate?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "Não agendada"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("Status local: ${statusLabel(os.status)}", style = MaterialTheme.typography.bodyMedium)
            SaveStatusText(state.saveState)
            state.validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onSaveDraft, modifier = Modifier.weight(1f)) {
                Text("Salvar rascunho")
            }
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                Text(if (os.status == ServiceOrderStatus.CONCLUIDA) "Reabrir OS" else "Finalizar OS")
            }
        }
        Text(
            text = "Todas as ações acima são locais e ainda não foram sincronizadas.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun SaveStatusText(state: DetalhesSaveState) {
    val text = when (state) {
        DetalhesSaveState.Idle -> "Ainda não salvo localmente"
        DetalhesSaveState.Saving -> "Salvando…"
        DetalhesSaveState.SavedLocally -> "Salvo localmente"
        is DetalhesSaveState.Error -> "Erro ao salvar: ${state.message}"
    }
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
}

private fun statusLabel(status: ServiceOrderStatus): String = when (status) {
    ServiceOrderStatus.PENDENTE -> "Pendente"
    ServiceOrderStatus.EM_ANDAMENTO -> "Em andamento"
    ServiceOrderStatus.CONCLUIDA -> "Concluída"
    ServiceOrderStatus.CANCELADA -> "Cancelada"
}

@Composable
fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}
