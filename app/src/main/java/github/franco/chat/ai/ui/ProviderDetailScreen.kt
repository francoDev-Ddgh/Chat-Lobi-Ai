package github.franco.chat.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.net.ApiTypes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderDetailScreen(
    viewModel: ChatViewModel,
    providerId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account = state.settings.accounts.firstOrNull { it.id == providerId }

    if (account == null) {
        LaunchedEffect(providerId) { onBack() }
        return
    }

    val hasKey = state.savedKeys.contains(providerId)

    var label by remember(providerId, account.label) { mutableStateOf(account.label) }
    var baseUrl by remember(providerId, account.baseUrl) { mutableStateOf(account.baseUrl) }
    var apiType by remember(providerId, account.apiType) { mutableStateOf(account.apiType) }
    var key by remember(providerId) { mutableStateOf("") }
    var manualModel by remember(providerId) { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.label) },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionLabel("Conexión")
            SectionCard {
                if (account.custom) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("URL base") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Text(
                    text = "Tipo de API",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApiTypes.all.forEach { tipo ->
                        FilterChip(
                            selected = apiType == tipo.first,
                            onClick = { apiType = tipo.first },
                            label = { Text(tipo.second) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (apiType == tipo.first) {
                            Text(
                                text = tipo.third,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
                Button(
                    onClick = { viewModel.saveAccountSettings(providerId, label, baseUrl, apiType) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Guardar cambios") }
                OutlinedButton(
                    onClick = { viewModel.testConnection(providerId) },
                    enabled = !state.pingBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.pingBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(if (state.pingBusy) "Probando…" else "Probar conexión")
                }
                state.pingResult?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("OK")) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error
                    )
                }
            }

            if (account.requiresKey) {
                SectionLabel("Clave de API")
                SectionCard {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API key") },
                        placeholder = {
                            Text(if (hasKey) "Guardada. Escribe otra para reemplazarla" else "Pega la clave")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveApiKey(providerId, key)
                                key = ""
                            },
                            enabled = key.isNotBlank(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Guardar clave") }
                        if (hasKey) {
                            TextButton(onClick = { viewModel.saveApiKey(providerId, "") }) {
                                Text("Borrar clave")
                            }
                        }
                    }
                }
            }

            SectionLabel("Modelos")
            SectionCard {
                OutlinedButton(
                    onClick = { viewModel.refreshModels(providerId) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text("Cargar modelos del proveedor")
                }
                if (account.models.isEmpty()) {
                    Text(
                        text = "Todavía no hay modelos. Carga la lista o agrega uno manualmente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        account.models.forEach { model ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.toggleModel(providerId, model) },
                                label = { Text(model) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "Toca un modelo para quitarlo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                viewModel.toggleModel(providerId, manualModel.trim())
                                manualModel = ""
                            }
                        },
                        enabled = manualModel.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Añadir") }
                }
            }

            SectionLabel("Comportamiento")
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Streaming",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Activado por defecto. Desactívalo si el proveedor corta la respuesta a mitad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = account.streaming,
                        onCheckedChange = { viewModel.setStreaming(providerId, it) }
                    )
                }
            }

            if (account.custom) {
                TextButton(
                    onClick = {
                        viewModel.deleteProvider(providerId)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Eliminar proveedor personalizado") }
            }

            Text(
                text = "Los cambios de nombre, URL y tipo se guardan con el botón. Los modelos y el streaming se guardan al instante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

/** Tarjeta de seccion compartida por las pantallas de proveedor. */
@Composable
internal fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
