package github.franco.chat.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (account.custom) {
                Text("Nombre", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Text("Tipo de API", style = MaterialTheme.typography.labelLarge)
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

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("URL base") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { viewModel.saveAccountSettings(providerId, label, baseUrl, apiType) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Guardar datos del proveedor") }

            HorizontalDivider()

            if (account.requiresKey) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    placeholder = { Text(if (hasKey) "Guardada - escribe otra para reemplazar" else "Pega la key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Row2 {
                    Button(
                        onClick = {
                            viewModel.saveApiKey(providerId, key)
                            key = ""
                        },
                        enabled = key.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Guardar key") }
                    if (hasKey) {
                        TextButton(onClick = { viewModel.saveApiKey(providerId, "") }) {
                            Text("Borrar key")
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Modelos", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(
                onClick = { viewModel.refreshModels(providerId) },
                enabled = !state.busy,
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
                    text = "Aun no hay modelos. Carga la lista o agrega uno manualmente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            viewModel.toggleModel(providerId, manualModel.trim())
                            manualModel = ""
                        }
                    },
                    enabled = manualModel.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Anadir") }
            }

            if (account.custom) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        viewModel.deleteProvider(providerId)
                        onBack()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Eliminar proveedor personalizado") }
            }

            Text(
                text = "Los cambios de nombre, URL y tipo se guardan con el boton. Los modelos se guardan al instante.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun Row2(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
