package github.franco.chat.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.net.Providers

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderDetailScreen(
    viewModel: ChatViewModel,
    presetId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val preset = Providers.byId(presetId)
    val account = state.settings.accounts.firstOrNull { it.presetId == presetId }
    val hasKey = state.savedKeys.contains(presetId)

    var key by remember(presetId) { mutableStateOf("") }
    var baseUrl by remember(presetId, account?.baseUrl) { mutableStateOf(account?.baseUrl.orEmpty()) }
    var manualModel by remember(presetId) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(preset.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (preset.requiresKey) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    placeholder = { Text(if (hasKey) "Guardada - escribe otra para reemplazar" else preset.keyHint) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row2 {
                    Button(
                        onClick = {
                            viewModel.saveApiKey(presetId, key)
                            key = ""
                        },
                        enabled = key.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Guardar key") }
                    if (hasKey) {
                        TextButton(onClick = { viewModel.saveApiKey(presetId, "") }) {
                            Text("Borrar key")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (opcional)") },
                placeholder = { Text(preset.baseUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedButton(
                onClick = { viewModel.saveAccountSettings(presetId, baseUrl) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Guardar base URL") }

            HorizontalDivider()

            Text("Modelos", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = { viewModel.refreshModels(presetId) },
                enabled = !state.busy,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                }
                Text("Cargar modelos del proveedor")
            }

            val models = account?.models.orEmpty()
            if (models.isEmpty()) {
                Text(
                    text = "Aun no hay modelos. Carga la lista o agrega uno manualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    models.forEach { model ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.toggleModel(presetId, model) },
                            label = { Text(model) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            Row2 {
                OutlinedTextField(
                    value = manualModel,
                    onValueChange = { manualModel = it },
                    label = { Text("Agregar modelo manual") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = {
                        if (manualModel.isNotBlank()) {
                            viewModel.toggleModel(presetId, manualModel.trim())
                            manualModel = ""
                        }
                    },
                    enabled = manualModel.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Anadir") }
            }

            Text(
                text = "Toca un modelo para quitarlo de la lista del chat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Row2(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content
    )
}
