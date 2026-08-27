package dev.claudiocodigo.nexo.ui.screens.remoto

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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Detail screen for a mirrored calendar event with attendance initiation (Phase 3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteEventDetailScreen(
    onBack: () -> Unit,
    onStartAttendance: (UUID) -> Unit,
    accountId: String,
    calendarHref: String,
    href: String,
    viewModel: RemoteEventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(accountId, calendarHref, href) {
        viewModel.load(accountId, calendarHref, href)
    }

    Scaffold(
        modifier = Modifier.testTag("screen_evento_remoto"),
        topBar = {
            TopAppBar(
                title = { Text("Evento do Calendário") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            if (state is RemoteEventDetailUiState.Success) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = { viewModel.startAttendance(onStartAttendance) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_iniciar_atendimento")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Iniciar Atendimento (OS)")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (val s = state) {
                RemoteEventDetailUiState.Loading -> Text("Carregando evento...")
                is RemoteEventDetailUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is RemoteEventDetailUiState.Success -> {
                    val e = s.event
                    Text(
                        text = e.summary ?: "Evento sem título",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("remoto_title")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LabeledValue("Nome do evento", e.summary ?: "—")
                            LabeledValue(
                                "Data",
                                e.start?.let { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).format(Date(it)) } ?: "—"
                            )
                            LabeledValue(
                                "Horário",
                                e.start?.let { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(it)) } ?: "—"
                            )
                            LabeledValue("Local", e.location ?: "—")
                            LabeledValue("Classificação", when (e.color) {
                                dev.claudiocodigo.nexo.domain.caldav.EventColor.VALIDADO -> "Validado"
                                dev.claudiocodigo.nexo.domain.caldav.EventColor.REQUER_ATENCAO -> "Requer atenção"
                                else -> "Não classificado"
                            })
                            LabeledValue("Identidade remota (UID)", e.uid)
                            LabeledValue("ETag", e.etag ?: "—")
                        }
                    }
                    if (!e.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Descrição Original:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(e.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}
