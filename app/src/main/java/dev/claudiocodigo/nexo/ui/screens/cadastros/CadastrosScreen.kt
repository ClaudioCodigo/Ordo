package dev.claudiocodigo.nexo.ui.screens.cadastros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.SettingsInputComponent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.claudiocodigo.nexo.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadastrosScreen(
    onNavigateToLista: (String) -> Unit
) {
    val items = listOf(
        CadastroItem(stringResource(R.string.clientes_unidades), Icons.Rounded.Business, "clientes"),
        CadastroItem(stringResource(R.string.locais), Icons.Rounded.LocationOn, "locais"),
        CadastroItem(stringResource(R.string.equipamentos), Icons.Rounded.SettingsInputComponent, "equipamentos"),
        CadastroItem(stringResource(R.string.nobreaks), Icons.Rounded.Power, "nobreaks"),
        CadastroItem(stringResource(R.string.baterias), Icons.Rounded.BatteryFull, "baterias")
    )

    Scaffold(
        modifier = Modifier.testTag("screen_cadastros"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cadastros_title)) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                CadastroCard(item, onClick = { onNavigateToLista(item.tipo) })
            }
        }
    }
}

@Composable
fun CadastroCard(item: CadastroItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.aspectRatio(1f),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

data class CadastroItem(val label: String, val icon: ImageVector, val tipo: String)
