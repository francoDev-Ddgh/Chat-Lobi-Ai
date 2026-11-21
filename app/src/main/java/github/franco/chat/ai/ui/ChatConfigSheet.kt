package github.franco.chat.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.franco.chat.ai.ModelOption
import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.data.ChatConfig
import github.franco.chat.ai.data.ProviderAccount

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatConfigSheet(
    chat: Chat,
    options: List<ModelOption>,
    accounts: List<ProviderAccount>,
    onDismiss: () -> Unit,
    onSave: (ChatConfig) -> Unit
) {
    var config by remember(chat.id) { mutableStateOf(chat.config) }

    val providerIds = remember(options) { options.map { it.providerId }.distinct() }
    val selectedProvider = when {
        config.providerId.isNotBlank() -> config.providerId
        providerIds.isNotEmpty() -> providerIds.first()
        else -> ""
    }
    val account = accounts.firstOrNull { it.id == selectedProvider }
    val models = remember(options, selectedProvider) {
        options.filter { it.providerId == selectedProvider }.map { it.model }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Ajustes de este chat", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Solo afectan a esta conversación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (providerIds.isEmpty()) {
                Text(
                    text = "No hay proveedores conectados. Ve a Ajustes > Proveedores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                SheetLabel("Proveedor")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    providerIds.forEach { id ->
                        val name = accounts.firstOrNull { it.id == id }?.label ?: id
                        FilterChip(
                            selected = id == selectedProvider,
                            onClick = { config = config.copy(providerId = id, model = "") },
                            label = { Text(name) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                SheetLabel("Modelo")
                if (models.isEmpty()) {
                    Text(
                        text = "Este proveedor no tiene modelos. Agrégalos en Ajustes > Proveedores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        models.forEach { model ->
                            FilterChip(
                                selected = model == config.model,
                                onClick = { config = config.copy(model = model) },
                                label = { Text(model) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            val supportsTemperature = account?.supportsTemperature ?: true
            if (supportsTemperature) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SheetLabel("Temperatura")
                    Text(
                        text = config.temperature?.let { String.format("%.1f", it) } ?: "auto",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = (config.temperature ?: 0.7).toFloat(),
                    onValueChange = { config = config.copy(temperature = it.toDouble()) },
                    valueRange = 0f..2f
                )
                TextButton(onClick = { config = config.copy(temperature = null) }) {
                    Text("Usar el valor por defecto del proveedor")
                }
            } else {
                Text(
                    text = "Este proveedor no acepta temperatura; se omite automáticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SheetLabel("Instrucciones del sistema")
            OutlinedTextField(
                value = config.systemPrompt,
                onValueChange = { config = config.copy(systemPrompt = it) },
                placeholder = { Text("Cómo debe comportarse el modelo en este chat") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val fixed = config.copy(
                        providerId = if (config.providerId.isBlank()) selectedProvider else config.providerId,
                        model = if (config.model.isBlank()) models.firstOrNull().orEmpty() else config.model
                    )
                    onSave(fixed)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Guardar ajustes")
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}
