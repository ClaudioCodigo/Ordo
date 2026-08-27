package dev.claudiocodigo.nexo.ui.screens.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.R
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.ui.screens.hoje.ServiceOrderCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToRemoteEvent: (accountId: String, calendarHref: String, href: String) -> Unit = { _, _, _ -> },
    viewModel: AgendaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    var pendingDeletion by remember { mutableStateOf<ServiceOrder?>(null) }

    Scaffold(
        modifier = Modifier.testTag("screen_agenda"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agenda_title)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("agenda_search"),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = MaterialTheme.shapes.medium
            )

            deleteError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when (val state = uiState) {
                AgendaUiState.Loading -> Text("Carregando agenda local…", modifier = Modifier.padding(16.dp).testTag("agenda_loading"))
                is AgendaUiState.Success -> {
                    val groups = (state.groupedOrders.keys + state.groupedRemoteEvents.keys).distinct()
                    if (groups.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) "Nenhuma ordem de serviço cadastrada." else "Nenhum resultado encontrado.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp).testTag("agenda_empty")
                        )
                    } else LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groups.forEach { date ->
                            val orders = state.groupedOrders[date].orEmpty()
                            val remoteEvents = state.groupedRemoteEvents[date].orEmpty()
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            items(orders) { os ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    ServiceOrderCard(
                                        os = os,
                                        onClick = onNavigateToDetails,
                                        onDelete = if (os.status != ServiceOrderStatus.CONCLUIDA) {
                                            { pendingDeletion = os }
                                        } else null
                                    )
                                }
                            }
                            items(remoteEvents, key = { "agenda_remote_${it.href}" }) { event ->
                                AgendaRemoteEventCard(event) {
                                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("Excluir rascunho local?") },
            text = {
                Text(
                    "${draft.title.ifBlank { "Rascunho sem título" }} será removido somente deste aparelho. " +
                        "Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLocalDraft(draft.id)
                        pendingDeletion = null
                    },
                    modifier = Modifier.testTag("confirm_delete_draft")
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AgendaRemoteEventCard(event: RemoteEvent, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, contentDescription = "Evento remoto", tint = MaterialTheme.colorScheme.primary)
                    Text(event.summary ?: "Evento sem título", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                event.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                Text(
                    when (event.color) {
                        EventColor.REQUER_ATENCAO -> "Requer atenção"
                        EventColor.VALIDADO -> "Validado"
                        else -> "Não classificado"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.color == EventColor.REQUER_ATENCAO) ComposeColor(0xFFC62828) else MaterialTheme.colorScheme.outline
                )
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(12.dp).background(remoteColor(event.rawEventColor), androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

private fun remoteColor(raw: String?): ComposeColor {
    val hex = raw?.removePrefix("#") ?: return ComposeColor.Gray
    return runCatching { ComposeColor(0xFF000000L or hex.toLong(16)) }.getOrDefault(ComposeColor.Gray)
}
