package dev.claudiocodigo.nexo.ui.screens.conta

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaNextcloudScreen(
    onBack: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenDiscovery: () -> Unit = {},
    pendingQrPayload: String? = null,
    onQrConsumed: (() -> Unit)? = null,
    viewModel: ContaNextcloudViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showQrInput by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf("") }

    LaunchedEffect(pendingQrPayload) {
        if (!pendingQrPayload.isNullOrBlank()) {
            viewModel.connectQr(pendingQrPayload)
            onQrConsumed?.invoke()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("screen_conta"),
        topBar = {
            TopAppBar(
                title = { Text("Conta Nextcloud") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
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
                ContaUiState.Loading -> Text("Carregando…", modifier = Modifier.testTag("conta_loading"))

                is ContaUiState.Disconnected -> {
                    s.error?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 12.dp).testTag("conta_error")
                        )
                    }

                    if (showQrInput) {
                        OutlinedTextField(
                            value = qrText,
                            onValueChange = { qrText = it },
                            label = { Text("Cole o conteúdo do QR") },
                            modifier = Modifier.fillMaxWidth().testTag("conta_qr_input"),
                            minLines = 3
                        )
                        Button(
                            onClick = { viewModel.connectQr(qrText) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("conta_connect_qr")
                        ) { Text("Usar este QR") }
                        OutlinedButton(
                            onClick = { showQrInput = false },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) { Text("Digitar manualmente") }
                    } else {
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("URL do servidor") },
                            placeholder = { Text("https://cloud.exemplo.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("conta_server")
                        )
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("Usuário") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("conta_user")
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Senha de aplicativo") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("conta_password")
                        )
                        Button(
                            onClick = {
                                viewModel.connectManual(server, user, password)
                                password = ""
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("conta_connect")
                        ) { Text("Conectar") }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            OutlinedButton(
                                onClick = onOpenQrScanner,
                                modifier = Modifier.weight(1f).testTag("conta_scan")
                            ) {
                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                                Text("Escanear QR", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { showQrInput = true },
                                modifier = Modifier.weight(1f).testTag("conta_paste")
                            ) { Text("Colar QR") }
                        }
                    }
                }

                is ContaUiState.Validating -> {
                    Text("Salvando conta…", modifier = Modifier.testTag("conta_validating"))
                }

                is ContaUiState.Connected -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Conta conectada",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Servidor: ${s.server}", style = MaterialTheme.typography.bodyMedium)
                            Text("Usuário: ${s.user}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "A senha não é exibida nem é armazenada em texto claro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = onOpenDiscovery,
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("conta_selecionar_agenda")
                            ) { Text("Selecionar agenda de trabalho") }
                            OutlinedButton(
                                onClick = viewModel::disconnect,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("conta_disconnect")
                            ) { Text("Desconectar") }
                        }
                    }
                }
            }
        }
    }
}
