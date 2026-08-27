package dev.claudiocodigo.nexo.ui.screens.descoberta

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.domain.caldav.CalendarInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescobertaAgendaScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit,
    viewModel: DescobertaAgendaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.testTag("screen_descoberta"),
        topBar = {
            TopAppBar(
                title = { Text("Selecionar agenda de trabalho") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            val success = state as? DescobertaUiState.Success
            if (success != null && success.calendars.isNotEmpty()) {
                Button(
                    onClick = { viewModel.confirmSelection(onSelected) },
                    enabled = success.selectedHref != null && !success.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("confirmar_agenda")
                ) {
                    Text(if (success.isSaving) "Salvando…" else "Usar esta agenda")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DescobertaUiState.Loading -> Text(
                    "Descobrindo agendas…",
                    modifier = Modifier.padding(16.dp).testTag("descoberta_loading")
                )
                is DescobertaUiState.Error -> Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("descoberta_error")
                    )
                    Button(
                        onClick = viewModel::discover,
                        modifier = Modifier.padding(top = 12.dp)
                    ) { Text("Tentar novamente") }
                }
                is DescobertaUiState.Success -> {
                    if (s.calendars.isEmpty()) {
                        Text(
                            "Nenhum calendário compatível encontrado para esta conta.",
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    "Selecione uma agenda e confirme para usá-la como agenda de trabalho.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                                s.selectionError?.let { message ->
                                    Text(
                                        text = message,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            items(s.calendars, key = { it.href }) { calendar ->
                                CalendarRow(
                                    calendar = calendar,
                                    selected = calendar.href == s.selectedHref,
                                    onSelect = { viewModel.choose(calendar.href) }
                                )
                            }
                            item { Spacer(modifier = Modifier.size(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarRow(calendar: CalendarInfo, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("calendario_${calendar.href.hashCode()}"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(renderColor(calendar.color), CircleShape)
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = calendar.displayName ?: calendar.href,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("calendario_nome")
                )
                calendar.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            RadioButton(
                selected = selected,
                onClick = onSelect
            )
        }
    }
}

@Composable
private fun renderColor(raw: String?): Color {
    val hex = raw?.removePrefix("#") ?: return MaterialTheme.colorScheme.primary
    return try {
        val value = hex.toLong(16)
        Color(0xFF000000L or value)
    } catch (_: IllegalArgumentException) {
        MaterialTheme.colorScheme.primary
    } catch (_: NumberFormatException) {
        MaterialTheme.colorScheme.primary
    }
}
