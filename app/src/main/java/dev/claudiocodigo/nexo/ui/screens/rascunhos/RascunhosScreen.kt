package dev.claudiocodigo.nexo.ui.screens.rascunhos

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.domain.serviceorder.StructuredServiceOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RascunhosScreen(
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    viewModel: RascunhosViewModel = hiltViewModel()
) {
    val drafts by viewModel.drafts.collectAsState()
    var draftToDiscard by remember { mutableStateOf<UUID?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (draftToDiscard != null) {
        AlertDialog(
            onDismissRequest = { draftToDiscard = null },
            title = { Text("Descartar rascunho?") },
            text = { Text("Este rascunho será removido permanentemente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftToDiscard?.let { viewModel.discardDraft(it) }
                        draftToDiscard = null
                    },
                    modifier = Modifier.testTag("btn_confirm_discard_single")
                ) {
                    Text("Descartar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { draftToDiscard = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Limpar todos os rascunhos?") },
            text = { Text("Todos os rascunhos de ordens de serviço provisórias serão apagados.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllDrafts()
                        showClearAllDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_clear_all")
                ) {
                    Text("Limpar Tudo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.testTag("screen_rascunhos"),
        topBar = {
            TopAppBar(
                title = { Text("Rascunhos Salvos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("btn_clear_all_drafts")
                        ) {
                            Text("Limpar fila", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhum rascunho salvo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }
                items(drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        dateFormatted = formatter.format(Date(draft.updatedAt)),
                        onOpen = { onOpenDraft(draft.id.toString()) },
                        onDiscard = { draftToDiscard = draft.id }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: StructuredServiceOrder,
    dateFormatted: String,
    onOpen: () -> Unit,
    onDiscard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("card_draft_"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = draft.title.ifBlank { "OS Provisória sem título" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDiscard, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            val client = draft.clientName.ifBlank { "Cliente não informado" }
            val unit = draft.unitName.takeIf { it.isNotBlank() }?.let { " • " }.orEmpty()
            Text(
                text = "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (draft.originalDemand.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = draft.originalDemand,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modificado: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Button(
                    onClick = onOpen,
                    modifier = Modifier.testTag("btn_continue_draft_")
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Continuar")
                }
            }
        }
    }
}
