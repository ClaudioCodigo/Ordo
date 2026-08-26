package dev.claudiocodigo.nexo.ui.screens.nova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.claudiocodigo.nexo.ui.screens.detalhes.InfoSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaOSScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: NovaOSViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var leaving by remember { mutableStateOf(false) }
    fun requestExit() {
        if (!leaving) {
            leaving = true
            viewModel.saveBeforeExit(onSaved = onBack, onFailed = { leaving = false })
        }
    }
    BackHandler { requestExit() }

    LaunchedEffect(Unit) {
        viewModel.savedOrderIds.collect { onSaved(it.toString()) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushNow()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("screen_nova_os"),
        topBar = {
            TopAppBar(
                title = { Text("Nova OS") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit, enabled = !leaving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        NovaOSContent(
            padding = padding,
            form = form,
            onExternalIdChange = viewModel::onExternalIdChange,
            onTitleChange = viewModel::onTitleChange,
            onClientChange = viewModel::onClientChange,
            onUnitChange = viewModel::onUnitChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun NovaOSContent(
    padding: PaddingValues,
    form: NovaOSFormState,
    onExternalIdChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onClientChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Atendimento provisório local",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "O número oficial é opcional. O atendimento recebe um identificador interno até ser associado a uma OS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        InfoSection("Identificação") {
            OutlinedTextField(
                value = form.externalId,
                onValueChange = onExternalIdChange,
                label = { Text("Número oficial (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.title,
                onValueChange = onTitleChange,
                label = { Text("Título curto") },
                modifier = Modifier.fillMaxWidth().testTag("nova_os_title"),
                singleLine = true
            )
            OutlinedTextField(
                value = form.clientName,
                onValueChange = onClientChange,
                label = { Text("Empresa/Cliente") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = form.unitName,
                onValueChange = onUnitChange,
                label = { Text("Unidade/Local") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        InfoSection("Demanda") {
            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                label = { Text("Demanda/Descrição") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            Text(
                "Data do evento: ${SimpleDateFormat("dd/MM/yyyy", locale).format(Date(form.scheduledDate))}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        form.validationError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        NovaSaveStatusText(form.saveState)
        Button(
            onClick = onSave,
            enabled = !form.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("nova_os_save")
        ) {
            Text(if (form.isSaving) "Salvando…" else "Salvar atendimento local")
        }
    }
}

@Composable
private fun NovaSaveStatusText(state: NovaDraftSaveState) {
    val text = when (state) {
        NovaDraftSaveState.Idle -> "Rascunho ainda não salvo"
        NovaDraftSaveState.Saving -> "Salvando rascunho…"
        NovaDraftSaveState.SavedLocally -> "Rascunho salvo localmente"
        is NovaDraftSaveState.Error -> "Erro ao salvar: ${state.message}"
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
}
