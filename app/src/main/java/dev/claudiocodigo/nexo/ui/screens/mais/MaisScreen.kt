package dev.claudiocodigo.nexo.ui.screens.mais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.claudiocodigo.nexo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaisScreen(
    viewModel: MaisViewModel = hiltViewModel()
) {
    val nextcloudUrl by viewModel.nextcloudUrl.collectAsState(initial = "")

    Scaffold(
        modifier = Modifier.testTag("screen_mais"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mais_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.relatorios)) },
                supportingContent = { Text("Disponível em uma fase futura") },
                leadingContent = { Icon(Icons.Rounded.Description, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.exportar_importar)) },
                supportingContent = { Text("Disponível em uma fase futura") },
                leadingContent = { Icon(Icons.Rounded.ImportExport, contentDescription = null) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.configuracoes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = null)
                            Text(
                                text = stringResource(R.string.conta_nextcloud),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "A integração Nextcloud ainda não está disponível. Este campo é apenas uma preparação local.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedTextField(
                            value = nextcloudUrl ?: "",
                            onValueChange = { viewModel.updateNextcloudUrl(it) },
                            label = { Text(stringResource(R.string.url_nextcloud)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://nextcloud.exemplo.com") }
                        )

                        OutlinedTextField(
                            value = "",
                            onValueChange = { },
                            label = { Text(stringResource(R.string.usuario_nextcloud)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            placeholder = { Text("Ainda não configurado") }
                        )
                    }
                }
            }
        }
    }
}
