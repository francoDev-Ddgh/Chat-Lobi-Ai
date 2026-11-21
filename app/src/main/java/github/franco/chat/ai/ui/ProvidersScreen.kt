package github.franco.chat.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.net.ApiTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onCreateCustom: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Proveedores") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateCustom,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Proveedor personalizado") },
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            items(state.accounts, key = { it.id }) { account ->
                val hasKey = state.savedKeys.contains(account.id)
                ListItem(
                    headlineContent = { Text(account.label) },
                    supportingContent = {
                        val parts = mutableListOf<String>()
                        parts += if (account.custom) "Personalizado" else "Integrado"
                        parts += ApiTypes.readable(account.apiType)
                        if (account.requiresKey) parts += if (hasKey) "key guardada" else "sin key"
                        if (account.models.isNotEmpty()) parts += "${account.models.size} modelo(s)"
                        Text(parts.joinToString(" - "))
                    },
                    trailingContent = {
                        Switch(
                            checked = account.enabled,
                            onCheckedChange = { viewModel.setProviderEnabled(account.id, it) }
                        )
                    },
                    modifier = Modifier.clickable { onOpenProvider(account.id) }
                )
                HorizontalDivider()
            }
            item {
                Text(
                    text = "Activa un proveedor y abre su ficha para guardar la key y elegir modelos. " +
                        "Usa el boton de abajo para agregar cualquier servidor compatible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp)
                )
            }
        }
    }
}
