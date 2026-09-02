package dev.claudiocodigo.nexo.ui.screens.sincronizacoes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncCenterScreen(
    onBack: () -> Unit,
    onNavigateToConflict: (UUID) -> Unit,
    viewModel: SyncCenterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    Scaffold(
        modifier = Modifier.testTag("screen_sync_center"),
        topBar = {
            TopAppBar(
                title = { Text("Central de Sincronizações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = state) {
            SyncCenterUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
            is SyncCenterUiState.Success -> {
                val hasAny = s.conflicts.isNotEmpty() || s.pending.isNotEmpty() ||
                    s.sending.isNotEmpty() || s.failed.isNotEmpty() || s.recentSent.isNotEmpty()

                if (!hasAny) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tudo sincronizado!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Não há publicações pendentes ou conflitos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (s.conflicts.isNotEmpty()) {
                            item {
                                SectionHeader("Conflitos no Servidor (412)", MaterialTheme.colorScheme.error)
                            }
                            items(s.conflicts, key = { it.operation.id }) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.size(6.dp))
                                            Text(item.orderTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Text("Cliente: ${item.clientName}", style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.operation.lastError ?: "O evento foi modificado no servidor.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { onNavigateToConflict(item.operation.orderId) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Resolver Conflito")
                                        }
                                    }
                                }
                            }
                        }

                        if (s.pending.isNotEmpty()) {
                            item {
                                SectionHeader("Pendentes de Envio", MaterialTheme.colorScheme.primary)
                            }
                            items(s.pending, key = { it.operation.id }) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.orderTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Aguardando rede • ${item.clientName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                        IconButton(onClick = { viewModel.cancelOperation(item.operation.id) }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Cancelar")
                                        }
                                    }
                                }
                            }
                        }

                        if (s.sending.isNotEmpty()) {
                            item {
                                SectionHeader("Enviando agora...", MaterialTheme.colorScheme.secondary)
                            }
                            items(s.sending, key = { it.operation.id }) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.size(12.dp))
                                        Column {
                                            Text(item.orderTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("Transmitindo para o Nextcloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }

                        if (s.failed.isNotEmpty()) {
                            item {
                                SectionHeader("Falhas de Envio", MaterialTheme.colorScheme.error)
                            }
                            items(s.failed, key = { it.operation.id }) { item ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(item.orderTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(item.operation.lastError ?: "Falha na comunicação", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.retryOperation(item.operation.id) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.size(4.dp))
                                                Text("Tentar Novamente")
                                            }
                                            IconButton(
                                                onClick = { viewModel.cancelOperation(item.operation.id) },
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            ) {
                                                Icon(Icons.Rounded.Close, contentDescription = "Cancelar Operação", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (s.recentSent.isNotEmpty()) {
                            item {
                                SectionHeader("Enviados Recentemente", MaterialTheme.colorScheme.outline)
                            }
                            items(s.recentSent, key = { it.operation.id }) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.orderTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text("Publicado em ${formatter.format(Date(item.operation.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}
