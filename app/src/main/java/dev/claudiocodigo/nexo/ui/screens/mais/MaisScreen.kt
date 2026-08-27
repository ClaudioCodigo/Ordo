package dev.claudiocodigo.nexo.ui.screens.mais

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.claudiocodigo.nexo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaisScreen(
    onNavigateToConta: () -> Unit
) {
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
                Card(
                    onClick = onNavigateToConta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mais_conta_nextcloud"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                Icon(
                                    Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.conta_nextcloud),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Text(
                                text = "Conectar por QR, configurar manualmente e selecionar a agenda de trabalho.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}
