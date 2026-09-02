package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
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
import dev.claudiocodigo.nexo.domain.serviceorder.CardNavigationTarget
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalOrderCard
import dev.claudiocodigo.nexo.domain.serviceorder.OperationalStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToNewOS: () -> Unit,
    onNavigateToDrafts: () -> Unit,
    onNavigateToRemoteEvent: (accountId: String, calendarHref: String, href: String) -> Unit,
    viewModel: HojeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing = (uiState as? HojeUiState.Success)?.isSyncing == true
    val draftsCount = (uiState as? HojeUiState.Success)?.provisionalDraftsCount ?: 0
    var showFabMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag("screen_hoje"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.hoje_title))
                        val successState = uiState as? HojeUiState.Success
                        val subtitle = if (successState?.isSyncing == true) {
                            "Sincronizando com Nextcloud…"
                        } else {
                            syncLabel(successState?.syncState)
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (successState?.isSyncing == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("btn_sync_hoje")
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Sincronizar com Nextcloud")
                    }
                }
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = {
                        if (draftsCount > 0) {
                            showFabMenu = true
                        } else {
                            onNavigateToNewOS()
                        }
                    },
                    modifier = Modifier.testTag("fab_new_os")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nova_os))
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Criar Nova OS") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onNavigateToNewOS()
                        },
                        modifier = Modifier.testTag("menu_create_new_os")
                    )
                    DropdownMenuItem(
                        text = { Text("Rascunhos Salvos ($draftsCount)") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) },
                        onClick = {
                            showFabMenu = false
                            onNavigateToDrafts()
                        },
                        modifier = Modifier.testTag("menu_saved_drafts")
                    )
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.syncNow() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (val state = uiState) {
                HojeUiState.Loading -> Text("Carregando ordens locais…", modifier = Modifier.padding(16.dp).testTag("hoje_loading"))
                is HojeUiState.Success -> {
                    HojeContent(
                        padding = PaddingValues(0.dp),
                        state = state,
                        onNavigateToDetails = onNavigateToDetails,
                        onNavigateToRemoteEvent = onNavigateToRemoteEvent
                    )
                }
            }
        }
    }
}

@Composable
fun HojeContent(
    padding: PaddingValues,
    state: HojeUiState.Success,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToRemoteEvent: (accountId: String, calendarHref: String, href: String) -> Unit
) {
    val onCardClick: (OperationalOrderCard) -> Unit = { card ->
        when (card.navigationTarget) {
            CardNavigationTarget.EVENTO_REMOTO -> {
                if (card.remoteAccountId != null && card.remoteCalendarHref != null && card.remoteEventHref != null) {
                    onNavigateToRemoteEvent(card.remoteAccountId, card.remoteCalendarHref, card.remoteEventHref)
                } else if (card.localOrderId != null) {
                    onNavigateToDetails(card.localOrderId.toString())
                }
            }
            CardNavigationTarget.EDITOR_OS, CardNavigationTarget.REVISAO_CONFLITO -> {
                if (card.localOrderId != null) {
                    onNavigateToDetails(card.localOrderId.toString())
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        // 1. Requer Atenção
        if (state.requerAtencaoCards.isNotEmpty()) {
            item { SectionHeader("Requer atenção") }
            items(state.requerAtencaoCards, key = { it.cardId }) { card ->
                UnifiedOsCard(card = card, onClick = { onCardClick(card) })
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        } else if (state.remoteEventsRequerAtencao.isNotEmpty()) {
            item { SectionHeader("Eventos requerem atenção") }
            items(state.remoteEventsRequerAtencao, key = { "remote_attention_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 2. Ordens do Dia (Abertas / Em Andamento)
        if (state.hojeOpenCards.isNotEmpty()) {
            item { SectionHeader("Ordens de Serviço do Dia") }
            items(state.hojeOpenCards, key = { it.cardId }) { card ->
                UnifiedOsCard(card = card, onClick = { onCardClick(card) })
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        } else if (state.remoteEvents.isNotEmpty()) {
            item { SectionHeader("Eventos do calendário") }
            items(state.remoteEvents, key = { "remote_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 3. Ordens Concluídas / Validadas (Validação de dois fatores)
        if (state.hojeConcludedCards.isNotEmpty()) {
            item { SectionHeader("Concluídas e Validadas") }
            items(state.hojeConcludedCards, key = { it.cardId }) { card ->
                UnifiedOsCard(card = card, onClick = { onCardClick(card) })
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 4. Overdue events (fallback)
        if (state.remoteEventsAtrasados.isNotEmpty() && state.hojeCards.isEmpty()) {
            item { SectionHeader("Eventos atrasados") }
            items(state.remoteEventsAtrasados, key = { "remote_overdue_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Empty state check
        val isEmpty = state.hojeCards.isEmpty() &&
            state.emAndamento.isEmpty() &&
            state.requerAtencao.isEmpty() &&
            state.pendencias.isEmpty() &&
            state.remoteEvents.isEmpty() &&
            state.remoteEventsRequerAtencao.isEmpty() &&
            state.remoteEventsAtrasados.isEmpty()

        if (isEmpty) {
            item {
                Text(
                    text = "Nenhuma ordem de serviço para hoje.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.testTag("hoje_empty").padding(vertical = 24.dp)
                )
            }
        }

        // Legacy sections fallback
        if (state.hojeCards.isEmpty()) {
            if (state.emAndamento.isNotEmpty()) {
                item { SectionHeader("Em andamento") }
                items(state.emAndamento, key = { "os_${it.id}" }) { os ->
                    ServiceOrderCard(os, onNavigateToDetails)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            if (state.requerAtencao.isNotEmpty()) {
                item { SectionHeader("Requer atenção") }
                items(state.requerAtencao, key = { "req_${it.id}" }) { os ->
                    ServiceOrderCard(os, onNavigateToDetails)
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            if (state.pendencias.isNotEmpty()) {
                item { SectionHeader("Pendências") }
                items(state.pendencias, key = { "pend_${it.id}" }) { os ->
                    ServiceOrderCard(os, onNavigateToDetails)
                }
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
fun UnifiedOsCard(
    card: OperationalOrderCard,
    onClick: () -> Unit
) {
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR"))
    val timeText = if (card.startMillis != null) {
        val startStr = timeFormatter.format(Date(card.startMillis))
        val endStr = card.endMillis?.let { " - ${timeFormatter.format(Date(it))}" }.orEmpty()
        "$startStr$endStr"
    } else {
        "Sem horário agendado"
    }

    val (statusLabel, statusColor) = when (card.status) {
        OperationalStatus.VALIDADO_EXTERNAMENTE -> "Validado no Servidor" to Color(0xFF2E7D32)
        OperationalStatus.AGUARDANDO_VALIDACAO_EXTERNA -> "Concluído (Aguardando Validação)" to Color(0xFFE65100)
        OperationalStatus.REQUER_ATENCAO -> "Requer Atenção" to Color(0xFFC62828)
        OperationalStatus.CONFLITO_PUBLICACAO -> "Conflito de Publicação" to Color(0xFFD32F2F)
        OperationalStatus.ENVIANDO_PUBLICACAO -> "Enviando..." to MaterialTheme.colorScheme.primary
        OperationalStatus.AGUARDANDO_CONEXAO -> "Pendente de Envio" to Color(0xFFF57C00)
        OperationalStatus.FALHA_PUBLICACAO -> "Falha no Envio" to Color(0xFFD32F2F)
        OperationalStatus.EM_ANDAMENTO -> "Em Andamento" to MaterialTheme.colorScheme.primary
        OperationalStatus.PENDENTE -> "Agendado" to MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(
                if (card.remoteEventHref != null) "remote_event_${card.remoteEventHref.hashCode()}"
                else "os_${card.localOrderId}"
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!card.externalId.isNullOrBlank()) "OS: ${card.externalId}" else "OS PROVISÓRIA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val locationText = listOfNotNull(
                    card.clientName.takeIf { it.isNotBlank() },
                    card.unitName?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

                if (locationText.isNotBlank()) {
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
                if (card.officialNumberAssigned) {
                    Text(
                        text = "Número oficial atribuído",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.testTag("official_number_assigned_${card.cardId}")
                    )
                }
            }

            if (card.rawColor != null) {
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(eventColorSquared(card.rawColor), CircleShape)
                )
            }
        }
    }
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

@Composable
private fun RemoteEventCard(event: RemoteEvent, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("remote_event_${event.href.hashCode()}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = event.summary ?: "Evento sem título",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                event.start?.let {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when (event.color) {
                        EventColor.VALIDADO -> "Validado"
                        EventColor.REQUER_ATENCAO -> "Requer atenção"
                        else -> "Não classificado"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (event.color) {
                        EventColor.VALIDADO -> Color(0xFF2E7D32)
                        EventColor.REQUER_ATENCAO -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(eventColorSquared(event.rawEventColor), CircleShape)
            )
        }
    }
}

@Composable
private fun eventColorSquared(raw: String?): Color {
    val hex = raw?.removePrefix("#") ?: return MaterialTheme.colorScheme.primary
    return try {
        val value = hex.toLong(16)
        Color(0xFF000000L or value)
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
}

private fun syncLabel(syncState: dev.claudiocodigo.nexo.domain.caldav.CalendarSyncState?): String = when {
    syncState == null -> "Ainda não sincronizado"
    syncState.isUnauthenticated -> "Requer reconexão"
    syncState.isError -> "Sincronização com erro"
    syncState.isSuccess -> {
        val time = syncState.lastSuccessMillis ?: syncState.lastSyncMillis
        "Sincronizado ${SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(time))}"
    }
    else -> "Ainda não sincronizado"
}
