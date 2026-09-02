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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import dev.claudiocodigo.nexo.domain.model.ServiceOrderStatus

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

    var validadasExpanded by remember { mutableStateOf(false) }

    val openCount = state.hojeOpenCards.size + state.remoteEvents.size + state.emAndamento.size
    val attentionCount = state.requerAtencaoCards.size + state.remoteEventsRequerAtencao.size + state.requerAtencao.size
    val concludedCount = state.hojeConcludedCards.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        // Summary Chips Strip
        if (openCount > 0 || attentionCount > 0 || concludedCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusSummaryPill(
                        label = "$openCount Abertas",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (attentionCount > 0) {
                        StatusSummaryPill(
                            label = "$attentionCount Atenção",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (concludedCount > 0) {
                        StatusSummaryPill(
                            label = "$concludedCount Validadas",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // 1. Requer Atenção
        if (state.requerAtencaoCards.isNotEmpty()) {
            item { SectionHeader("Requer atenção", color = MaterialTheme.colorScheme.error) }
            items(state.requerAtencaoCards, key = { it.cardId }) { card ->
                UnifiedOsCard(card = card, onClick = { onCardClick(card) })
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        } else if (state.remoteEventsRequerAtencao.isNotEmpty()) {
            item { SectionHeader("Eventos requerem atenção", color = MaterialTheme.colorScheme.error) }
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

        // 3. Ordens Concluídas / Validadas (Colapsável)
        if (state.hojeConcludedCards.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { validadasExpanded = !validadasExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Concluídas e Validadas (${state.hojeConcludedCards.size})", color = MaterialTheme.colorScheme.primary)
                    Icon(
                        if (validadasExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (validadasExpanded) "Recolher" else "Expandir",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (validadasExpanded) {
                items(state.hojeConcludedCards, key = { it.cardId }) { card ->
                    UnifiedOsCard(card = card, onClick = { onCardClick(card) })
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // 4. Overdue events (fallback)
        if (state.remoteEventsAtrasados.isNotEmpty() && state.hojeCards.isEmpty()) {
            item { SectionHeader("Eventos atrasados", color = MaterialTheme.colorScheme.error) }
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
                item { SectionHeader("Requer atenção", color = MaterialTheme.colorScheme.error) }
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
fun StatusSummaryPill(
    label: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        shape = CircleShape
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
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

    val (statusLabel, badgeBgColor, badgeTextColor, cardBgColor) = when (card.status) {
        OperationalStatus.VALIDADO_EXTERNAMENTE -> Quadruple(
            "Validado no Servidor",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.AGUARDANDO_VALIDACAO_EXTERNA -> Quadruple(
            "Concluído (Aguardando Validação)",
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.onTertiary,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.REQUER_ATENCAO -> Quadruple(
            "Requer Atenção",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.CONFLITO_PUBLICACAO -> Quadruple(
            "Conflito de Publicação",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.ENVIANDO_PUBLICACAO -> Quadruple(
            "Enviando...",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.AGUARDANDO_CONEXAO -> Quadruple(
            "Pendente de Envio",
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.FALHA_PUBLICACAO -> Quadruple(
            "Falha no Envio",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.EM_ANDAMENTO -> Quadruple(
            "Em Andamento",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        OperationalStatus.PENDENTE -> Quadruple(
            "Agendado",
            MaterialTheme.colorScheme.outline,
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag(
                if (card.remoteEventHref != null) "remote_event_${card.remoteEventHref.hashCode()}"
                else "os_${card.localOrderId}"
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                ) {
                    Text(
                        text = if (!card.externalId.isNullOrBlank()) "OS: ${card.externalId}" else "OS PROVISÓRIA",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

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
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                if (card.officialNumberAssigned) {
                    Text(
                        text = "Número oficial atribuído",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("official_number_assigned_${card.cardId}")
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ServiceOrderCard(
    os: ServiceOrder,
    onClick: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val (statusLabel, badgeBgColor, badgeTextColor, cardBgColor) = when (os.status) {
        ServiceOrderStatus.CONCLUIDA -> Quadruple(
            "Concluída",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        ServiceOrderStatus.EM_ANDAMENTO -> Quadruple(
            "Em Andamento",
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
        ServiceOrderStatus.PENDENTE -> Quadruple(
            "Pendente",
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
        ServiceOrderStatus.CANCELADA -> Quadruple(
            "Cancelada",
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        onClick = { onClick(os.id.toString()) },
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
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape
                ) {
                    Text(
                        text = os.externalId ?: "Sem número",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                onDelete?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("delete_draft_${os.id}")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Excluir rascunho local: ${os.title.ifBlank { "Rascunho sem título" }}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = os.title.ifBlank { "Rascunho sem título" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val locationText = "${os.clientName.ifBlank { "Empresa não informada" }} • ${os.unitName.ifBlank { "Local não informado" }}"
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

@Composable
private fun RemoteEventCard(event: RemoteEvent, onClick: () -> Unit) {
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
            .padding(vertical = 5.dp)
            .testTag("remote_event_${event.href.hashCode()}"),
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
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = event.summary ?: "Evento sem título",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (event.rawEventColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(eventColorSquared(event.rawEventColor), CircleShape)
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
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
