package dev.claudiocodigo.nexo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.claudiocodigo.nexo.R

@Composable
fun HojeScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.hoje_title))
        }
    }
}

@Composable
fun AgendaScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.agenda_title))
        }
    }
}

@Composable
fun FerramentasScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.ferramentas_title))
        }
    }
}

@Composable
fun CadastrosScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.cadastros_title))
        }
    }
}

@Composable
fun MaisScreen() {
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(text = stringResource(R.string.mais_title))
        }
    }
}
