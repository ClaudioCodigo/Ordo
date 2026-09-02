package dev.claudiocodigo.nexo.ui.screens.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.R
import dev.claudiocodigo.nexo.domain.caldav.EventColor
import dev.claudiocodigo.nexo.domain.caldav.RemoteEvent
import dev.claudiocodigo.nexo.domain.model.ServiceOrder
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Surface
import dev.claudiocodigo.nexo.ui.screens.hoje.UnifiedOsCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val isSyncing = (uiState as? AgendaUiState.Success)?.isSyncing == true

    Scaffold(
        modifier = Modifier.testTag("screen_agenda"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.agenda_title))
                        if (isSyncing) {
                            Text(
                                text = "Sincronizando com Nextcloud…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("btn_sync_agenda")
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sincronizar com Nextcloud")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncNow() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    AgendaUiState.Loading -> Text(
                        text = "Carregando agenda local…",
                        modifier = Modifier.padding(16.dp).testTag("agenda_loading")
                    )
                    is AgendaUiState.Success -> {
                        AgendaContent(
                            state = state,
                            searchQuery = searchQuery,
                            onNavigateToDetails = onNavigateToDetails,
                            onNavigateToRemoteEvent = onNavigateToRemoteEvent,
                            onDeleteRequested = { pendingDeletion = it }
                        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaContent(
    state: AgendaUiState.Success,
    searchQuery: String = "",
    onNavigateToDetails: (String) -> Unit,
    onNavigateToRemoteEvent: (accountId: String, calendarHref: String, href: String) -> Unit,
    onDeleteRequested: (ServiceOrder) -> Unit
) {
    val todayLabel = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")).format(Date())

    val groups = (state.groupedOrders.keys + state.groupedRemoteEvents.keys).distinct()
    if (groups.isEmpty()) {
        Text(
            text = if (searchQuery.isBlank()) "Nenhuma ordem de serviço cadastrada." else "Nenhum resultado encontrado.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(16.dp).testTag("agenda_empty")
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            groups.forEach { date ->
                val isToday = date.equals(todayLabel, ignoreCase = true)
                val orders = state.groupedOrders[date].orEmpty()
                val remoteEvents = state.groupedRemoteEvents[date].orEmpty()
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isToday) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "HOJE • $date",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
                items(orders) { os ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        dev.claudiocodigo.nexo.ui.screens.hoje.ServiceOrderCard(
                            os = os,
                            onClick = onNavigateToDetails,
                            onDelete = if (os.status != ServiceOrderStatus.CONCLUIDA) {
                                { onDeleteRequested(os) }
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

@Composable
private fun AgendaRemoteEventCard(event: RemoteEvent, onClick: () -> Unit) {
    val (statusLabel, badgeBgColor, badgeTextColor, cardBgColor) = when (event.color) {
        EventColor.VALIDADO -> Quadruple(
            "Validado",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        EventColor.REQUER_ATENCAO -> Quadruple(
            "Requer atenção",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
        else -> Quadruple(
            "Não classificado",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Event,
                        contentDescription = "Evento remoto",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        event.summary ?: "Evento sem título",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (event.rawEventColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(remoteColor(event.rawEventColor), CircleShape)
                    )
                }
            }

            event.start?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            event.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = badgeBgColor,
                shape = CircleShape
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun remoteColor(raw: String?): Color {
    val hex = raw?.removePrefix("#") ?: return Color.Gray
    return runCatching { Color(0xFF000000L or hex.toLong(16)) }.getOrDefault(Color.Gray)
}
