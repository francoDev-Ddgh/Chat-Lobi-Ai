package github.franco.chat.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.net.ApiTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomProviderScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiType by remember { mutableStateOf(ApiTypes.OPENAI) }
    var key by remember { mutableStateOf("") }
    var requiresKey by remember { mutableStateOf(true) }

    val suggestedUrl = when (apiType) {
        ApiTypes.ANTHROPIC -> "https://api.anthropic.com/v1"
        ApiTypes.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/openai"
        else -> "https://api.openai.com/v1"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo proveedor") },
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
            Text("Nombre", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                placeholder = { Text("Mi servidor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Text("URL base", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { Text(suggestedUrl) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            TextButton(onClick = { baseUrl = suggestedUrl }) {
                Text("Usar la URL sugerida para este tipo")
            }

            HorizontalDivider()

            Text("Tipo de API", style = MaterialTheme.typography.labelLarge)
            Text(
                text = "Elige como habla ese servidor. La app siempre usa el formato de chat compatible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ApiTypes.all.forEach { tipo ->
                    val id = tipo.first
                    val legible = tipo.second
                    FilterChip(
                        selected = apiType == id,
                        onClick = { apiType = id },
                        label = { Text(legible) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (apiType == id) {
                        Text(
                            text = tipo.third,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Este proveedor necesita API key", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = requiresKey, onCheckedChange = { requiresKey = it })
            }

            if (requiresKey) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key (opcional ahora)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Button(
                onClick = {
                    viewModel.createCustomProvider(
                        label = label,
                        baseUrl = baseUrl.ifBlank { suggestedUrl },
                        apiType = apiType,
                        apiKey = key,
                        requiresKey = requiresKey
                    )
                    onCreated()
                },
                enabled = baseUrl.isNotBlank() || baseUrl.isBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Crear proveedor")
            }

            Text(
                text = "Se creara activo y con la lista de modelos vacia. Abre su ficha para cargar los modelos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
