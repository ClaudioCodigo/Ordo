package dev.claudiocodigo.nexo.ui.screens.conflito

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.domain.serviceorder.FieldChoice
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictReviewScreen(
    orderId: UUID,
    onBack: () -> Unit,
    onResolved: () -> Unit,
    viewModel: ConflictReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(orderId) {
        viewModel.load(orderId)
    }

    Scaffold(
        modifier = Modifier.testTag("screen_conflict_review"),
        topBar = {
            TopAppBar(
                title = { Text("Revisão de Conflito (412)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            if (state is ConflictUiState.Ready) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Button(
                            onClick = { viewModel.applyResolution(onResolved) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_apply_conflict_resolution")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Aplicar Escolhas e Voltar ao Editor")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            ConflictUiState.Loading -> Text("Carregando versões em conflito...", modifier = Modifier.padding(padding).padding(16.dp))
            is ConflictUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(padding).padding(16.dp))
            is ConflictUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                            Text(
                                text = "Este evento foi alterado no servidor enquanto você editava. Escolha qual valor deseja manter para cada campo:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    if (s.differences.isEmpty()) {
                        Text("Nenhuma divergência detectada nos campos principais.")
                    } else {
                        s.differences.forEach { diff ->
                            val currentChoice = s.choices[diff.field] ?: FieldChoice.KEEP_LOCAL

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = diff.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = currentChoice == FieldChoice.KEEP_LOCAL,
                                            onClick = { viewModel.onChoiceSelected(diff.field, FieldChoice.KEEP_LOCAL) },
                                            label = { Text("Manter Local") }
                                        )
                                        FilterChip(
                                            selected = currentChoice == FieldChoice.USE_REMOTE,
                                            onClick = { viewModel.onChoiceSelected(diff.field, FieldChoice.USE_REMOTE) },
                                            label = { Text("Usar Remoto") }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Meu Valor Local:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(diff.localValue.orEmpty().ifBlank { "(Vazio)" }, style = MaterialTheme.typography.bodyMedium)

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Valor no Servidor (Remoto):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Text(diff.remoteValue.orEmpty().ifBlank { "(Vazio)" }, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }
}
