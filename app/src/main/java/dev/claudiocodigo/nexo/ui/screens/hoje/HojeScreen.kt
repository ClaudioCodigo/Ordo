package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.R
import dev.claudiocodigo.nexo.domain.model.ServiceOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToNewOS: () -> Unit,
    viewModel: HojeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.testTag("screen_hoje"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.hoje_title))
                        Text(
                            text = "Ainda não sincronizado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewOS) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nova_os))
            }
        }
    ) { padding ->
        when (val state = uiState) {
            HojeUiState.Loading -> Text("Carregando ordens locais…", modifier = Modifier.padding(padding).testTag("hoje_loading"))
            is HojeUiState.Success -> {
                HojeContent(
                    padding = padding,
                    state = state,
                    onNavigateToDetails = onNavigateToDetails
                )
            }
        }
    }
}

@Composable
fun HojeContent(
    padding: PaddingValues,
    state: HojeUiState.Success,
    onNavigateToDetails: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (state.emAndamento.isEmpty() && state.requerAtencao.isEmpty() && state.pendencias.isEmpty()) {
            item {
                Text(
                    text = "Nenhuma ordem de serviço para hoje.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("hoje_empty").padding(vertical = 24.dp)
                )
            }
        }
        if (state.emAndamento.isNotEmpty()) {
            item { SectionHeader("Em andamento") }
            items(state.emAndamento) { os ->
                ServiceOrderCard(os, onNavigateToDetails)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (state.requerAtencao.isNotEmpty()) {
            item { SectionHeader("Requer atenção") }
            items(state.requerAtencao) { os ->
                ServiceOrderCard(os, onNavigateToDetails)
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (state.pendencias.isNotEmpty()) {
            item { SectionHeader("Pendências") }
            items(state.pendencias) { os ->
                ServiceOrderCard(os, onNavigateToDetails)
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ServiceOrderCard(
    os: ServiceOrder,
    onClick: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = { onClick(os.id.toString()) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = os.externalId ?: "Sem número",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = os.title.ifBlank { "Rascunho sem título" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${os.clientName.ifBlank { "Empresa não informada" }} - ${os.unitName.ifBlank { "Local não informado" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onDelete?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.testTag("delete_draft_${os.id}")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Excluir rascunho local: ${os.title.ifBlank { "Rascunho sem título" }}"
                    )
                }
            }
        }
    }
}
