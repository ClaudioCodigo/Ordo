package dev.claudiocodigo.nexo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import dagger.hilt.android.AndroidEntryPoint
import dev.claudiocodigo.nexo.ui.navigation.Route
import dev.claudiocodigo.nexo.ui.screens.agenda.AgendaScreen
import dev.claudiocodigo.nexo.ui.screens.cadastros.CadastrosScreen
import dev.claudiocodigo.nexo.ui.screens.cadastros.ListaCadastroScreen
import dev.claudiocodigo.nexo.ui.screens.conflito.ConflictReviewScreen
import dev.claudiocodigo.nexo.ui.screens.conta.ContaNextcloudScreen
import dev.claudiocodigo.nexo.ui.screens.sincronizacoes.SyncCenterScreen
import dev.claudiocodigo.nexo.ui.screens.conta.QrScanScreen
import dev.claudiocodigo.nexo.ui.screens.descoberta.DescobertaAgendaScreen
import dev.claudiocodigo.nexo.ui.screens.detalhes.DetalhesScreen
import dev.claudiocodigo.nexo.ui.screens.oseditor.ServiceOrderEditorScreen
import dev.claudiocodigo.nexo.ui.screens.preview.PublicationPreviewScreen
import dev.claudiocodigo.nexo.ui.screens.remoto.RemoteEventDetailScreen
import dev.claudiocodigo.nexo.ui.screens.ferramentas.DiagnosticoBateriaScreen
import dev.claudiocodigo.nexo.ui.screens.ferramentas.FerramentasScreen
import dev.claudiocodigo.nexo.ui.screens.hoje.HojeScreen
import dev.claudiocodigo.nexo.ui.screens.mais.MaisScreen
import dev.claudiocodigo.nexo.ui.screens.nova.NovaOSScreen
import dev.claudiocodigo.nexo.ui.theme.NexoTheme
import java.util.UUID

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexoTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val tabs = listOf(Route.Hoje, Route.Agenda, Route.Ferramentas, Route.Cadastros, Route.Mais)
    var selectedTab by remember { mutableStateOf<Route>(Route.Hoje) }

    val backStacks = remember {
        tabs.associateWith { mutableStateListOf<Route>(it) }
    }

    val currentBackStack = backStacks[selectedTab]!!
    val currentRoute = currentBackStack.lastOrNull() ?: selectedTab

    val hideBottomBar = currentRoute is Route.DetalhesOS ||
                        currentRoute is Route.NovaOS ||
                        currentRoute is Route.EditorOS ||
                        currentRoute is Route.PreviewPublicacao ||
                        currentRoute is Route.RevisaoConflito ||
                        currentRoute is Route.CentralSincronizacao ||
                        currentRoute is Route.DiagnosticoBateria ||
                        currentRoute is Route.ListaCadastro ||
                        currentRoute is Route.ContaNextcloud ||
                        currentRoute is Route.QrScanner ||
                        currentRoute is Route.DescobertaAgenda ||
                        currentRoute is Route.EventoRemoto

    var pendingQrPayload by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.testTag("screen_main"),
        bottomBar = {
            if (!hideBottomBar) {
                NexoNavigationBar(
                    currentRoute = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = currentBackStack,
            onBack = { if (currentBackStack.size > 1) currentBackStack.removeAt(currentBackStack.size - 1) },
            modifier = Modifier.padding(innerPadding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = { key ->
                when (key) {
                    is Route.Hoje -> NavEntry(key) {
                        HojeScreen(
                            onNavigateToDetails = { id -> currentBackStack.add(Route.EditorOS(id)) },
                            onNavigateToNewOS = {
                                currentBackStack.add(Route.EditorOS(UUID.randomUUID().toString()))
                            },
                            onNavigateToRemoteEvent = { accountId, calHref, href ->
                                currentBackStack.add(Route.EventoRemoto(accountId, calHref, href))
                            }
                        )
                    }
                    is Route.Agenda -> NavEntry(key) {
                        AgendaScreen(
                            onNavigateToDetails = { id -> currentBackStack.add(Route.EditorOS(id)) },
                            onNavigateToRemoteEvent = { accountId, calHref, href ->
                                currentBackStack.add(Route.EventoRemoto(accountId, calHref, href))
                            }
                        )
                    }
                    is Route.Ferramentas -> NavEntry(key) {
                        FerramentasScreen(
                            onNavigateToDiagnostico = { currentBackStack.add(Route.DiagnosticoBateria) }
                        )
                    }
                    is Route.Cadastros -> NavEntry(key) {
                        CadastrosScreen(
                            onNavigateToLista = { tipo -> currentBackStack.add(Route.ListaCadastro(tipo)) }
                        )
                    }
                    is Route.Mais -> NavEntry(key) {
                        MaisScreen(
                            onNavigateToConta = { currentBackStack.add(Route.ContaNextcloud) },
                            onNavigateToSyncCenter = { currentBackStack.add(Route.CentralSincronizacao) }
                        )
                    }
                    is Route.CentralSincronizacao -> NavEntry(key) {
                        SyncCenterScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onNavigateToConflict = { orderId ->
                                currentBackStack.add(Route.RevisaoConflito(orderId.toString()))
                            }
                        )
                    }
                    is Route.DetalhesOS -> NavEntry(key) {
                        DetalhesScreen(
                            osId = key.id,
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) }
                        )
                    }
                    is Route.NovaOS -> NavEntry(key) {
                        NovaOSScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onSaved = { id ->
                                currentBackStack.removeAt(currentBackStack.size - 1)
                                currentBackStack.add(Route.EditorOS(id))
                            }
                        )
                    }
                    is Route.EditorOS -> NavEntry(key) {
                        ServiceOrderEditorScreen(
                            orderId = UUID.fromString(key.orderId),
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onNavigateToPreview = { id ->
                                currentBackStack.add(Route.PreviewPublicacao(id.toString()))
                            }
                        )
                    }
                    is Route.PreviewPublicacao -> NavEntry(key) {
                        PublicationPreviewScreen(
                            orderId = UUID.fromString(key.orderId),
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onConfirmed = {
                                if (currentBackStack.size > 1) {
                                    currentBackStack.removeAt(currentBackStack.size - 1)
                                }
                            }
                        )
                    }
                    is Route.RevisaoConflito -> NavEntry(key) {
                        ConflictReviewScreen(
                            orderId = UUID.fromString(key.orderId),
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onResolved = {
                                if (currentBackStack.size > 1) {
                                    currentBackStack.removeAt(currentBackStack.size - 1)
                                }
                            }
                        )
                    }
                    is Route.DiagnosticoBateria -> NavEntry(key) {
                        DiagnosticoBateriaScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) }
                        )
                    }
                    is Route.ListaCadastro -> NavEntry(key) {
                        ListaCadastroScreen(
                            tipo = key.tipo,
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) }
                        )
                    }
                    is Route.ContaNextcloud -> NavEntry(key) {
                        ContaNextcloudScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onOpenQrScanner = { currentBackStack.add(Route.QrScanner) },
                            onOpenDiscovery = { currentBackStack.add(Route.DescobertaAgenda) },
                            pendingQrPayload = pendingQrPayload,
                            onQrConsumed = { pendingQrPayload = null }
                        )
                    }
                    is Route.QrScanner -> NavEntry(key) {
                        QrScanScreen(
                            onQrResult = { payload ->
                                pendingQrPayload = payload
                                if (currentBackStack.size > 1) currentBackStack.removeAt(currentBackStack.size - 1)
                            },
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) }
                        )
                    }
                    is Route.DescobertaAgenda -> NavEntry(key) {
                        DescobertaAgendaScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onSelected = { currentBackStack.removeAt(currentBackStack.size - 1) }
                        )
                    }
                    is Route.EventoRemoto -> NavEntry(key) {
                        RemoteEventDetailScreen(
                            onBack = { currentBackStack.removeAt(currentBackStack.size - 1) },
                            onStartAttendance = { orderId ->
                                currentBackStack.add(Route.EditorOS(orderId.toString()))
                            },
                            accountId = key.accountId,
                            calendarHref = key.calendarHref,
                            href = key.href
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun NexoNavigationBar(
    currentRoute: Route,
    onTabSelected: (Route) -> Unit
) {
    NavigationBar(modifier = Modifier.testTag("bottom_navigation")) {
        NavigationBarItem(
            selected = currentRoute is Route.Hoje,
            onClick = { onTabSelected(Route.Hoje) },
            modifier = Modifier.testTag("tab_hoje"),
            icon = { Icon(Icons.Rounded.Today, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_hoje)) }
        )
        NavigationBarItem(
            selected = currentRoute is Route.Agenda,
            onClick = { onTabSelected(Route.Agenda) },
            modifier = Modifier.testTag("tab_agenda"),
            icon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_agenda)) }
        )
        NavigationBarItem(
            selected = currentRoute is Route.Ferramentas,
            onClick = { onTabSelected(Route.Ferramentas) },
            modifier = Modifier.testTag("tab_ferramentas"),
            icon = { Icon(Icons.Rounded.Build, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_ferramentas)) }
        )
        NavigationBarItem(
            selected = currentRoute is Route.Cadastros,
            onClick = { onTabSelected(Route.Cadastros) },
            modifier = Modifier.testTag("tab_cadastros"),
            icon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_cadastros)) }
        )
        NavigationBarItem(
            selected = currentRoute is Route.Mais,
            onClick = { onTabSelected(Route.Mais) },
            modifier = Modifier.testTag("tab_mais"),
            icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_mais)) }
        )
    }
}
