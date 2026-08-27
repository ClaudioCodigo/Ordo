package dev.claudiocodigo.nexo.ui.screens.hoje

import androidx.compose.foundation.background
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojeScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToNewOS: () -> Unit,
    onNavigateToRemoteEvent: (accountId: String, calendarHref: String, href: String) -> Unit,
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
                            text = syncLabel((uiState as? HojeUiState.Success)?.syncState),
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
                    onNavigateToDetails = onNavigateToDetails,
                    onNavigateToRemoteEvent = onNavigateToRemoteEvent
                )
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (state.remoteEvents.isNotEmpty()) {
            item { SectionHeader("Eventos do calendário") }
            items(state.remoteEvents, key = { "remote_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (state.remoteEventsRequerAtencao.isNotEmpty()) {
            item { SectionHeader("Eventos requerem atenção") }
            items(state.remoteEventsRequerAtencao, key = { "remote_attention_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (state.remoteEventsAtrasados.isNotEmpty()) {
            item { SectionHeader("Eventos atrasados") }
            items(state.remoteEventsAtrasados, key = { "remote_overdue_${it.href}" }) { event ->
                RemoteEventCard(event) {
                    onNavigateToRemoteEvent(event.accountId, event.calendarHref, event.href)
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (state.emAndamento.isEmpty() && state.requerAtencao.isEmpty() && state.pendencias.isEmpty() &&
            state.remoteEvents.isEmpty() && state.remoteEventsRequerAtencao.isEmpty() && state.remoteEventsAtrasados.isEmpty()) {
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
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
            androidx.compose.foundation.layout.Box(
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
