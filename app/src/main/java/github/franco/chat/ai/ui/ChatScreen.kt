package github.franco.chat.ai.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import github.franco.chat.ai.data.Chat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.ModelOption
import github.franco.chat.ai.UiState
import github.franco.chat.ai.data.Attachment
import github.franco.chat.ai.data.Message
import github.franco.chat.ai.text.ModelCapabilities
import github.franco.chat.ai.text.ModelCaps
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by rememberSaveable { mutableStateOf("") }
    var showChatConfig by remember { mutableStateOf(false) }
    var waitSeconds by remember { mutableStateOf(0) }
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    val activeModel = state.active?.model.orEmpty()
    val caps = remember(activeModel) { ModelCapabilities.of(activeModel) }
    val activeAccount = state.settings.accounts.firstOrNull { it.id == state.active?.providerId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            readAttachment(context, uri)
                .onSuccess { pending = pending + it }
                .onFailure { Toast.makeText(context, it.message ?: "No se pudo adjuntar", Toast.LENGTH_SHORT).show() }
        }
    }

    val chat = state.current
    val messages = chat?.messages.orEmpty()
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0

    LaunchedEffect(state.streaming) {
        waitSeconds = 0
        if (state.streaming) {
            while (true) {
                delay(1000)
                waitSeconds += 1
            }
        }
    }

    // La lista esta invertida: el indice 0 es el final. Solo seguimos si el usuario ya estaba abajo.
    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isNotEmpty() && listState.firstVisibleItemIndex == 0) {
            listState.scrollToItem(0)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.background) {
                    HistoryDrawer(
                        state = state,
                        onNewChat = {
                            viewModel.newChat()
                            scope.launch { drawerState.close() }
                        },
                        onSelect = { id ->
                            viewModel.selectChat(id)
                            scope.launch { drawerState.close() }
                        },
                        onDelete = { id -> viewModel.deleteChat(id) },
                        onOpenSettings = {
                            scope.launch { drawerState.close() }
                            onOpenSettings()
                        }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = chat?.title ?: "Nuevo chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Historial de chats")
                                }
                            },
                            actions = {
                                IconButton(onClick = { showChatConfig = true }) {
                                    Icon(Icons.Filled.Build, contentDescription = "Ajustes del chat")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                        ModelSelectorBar(
                            options = state.modelOptions,
                            active = state.active,
                            onSelect = { viewModel.selectModel(it) }
                        )
                    }
                },
                bottomBar = {
                    ComposerBar(
                        value = input,
                        onValueChange = { input = it },
                        streaming = state.streaming,
                        pending = pending,
                        caps = caps,
                        efforts = ModelCapabilities.reasoningEfforts(
                            activeModel,
                            activeAccount?.reasoningModels.orEmpty(),
                            activeAccount?.metaModels.orEmpty()
                        ),
                        effort = viewModel.reasoningEffort,
                        onEffort = { viewModel.reasoningEffort = it },
                        onPick = { kind -> picker.launch(mimeFor(kind)) },
                        onRemovePending = { index -> pending = pending.filterIndexed { i, _ -> i != index } },
                        onSend = {
                            val text = input
                            val toSend = pending
                            input = ""
                            pending = emptyList()
                            viewModel.send(text, toSend)
                        },
                        onStop = { viewModel.stop() }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    state.error?.let { error ->
                        ErrorBanner(
                            message = error,
                            canRetry = !state.streaming && messages.any { it.role == "user" },
                            onRetry = { viewModel.retry() },
                            onDismiss = { viewModel.clearError() }
                        )
                    }

                    if (messages.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.weight(1f),
                            onSuggestion = { input = it }
                        )
                    } else {
                        val reversed = remember(messages) { messages.asReversed() }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                            reverseLayout = true
                        ) {
                            itemsIndexed(reversed) { index, message ->
                                MessageBubble(
                                    message = message,
                                    streaming = state.streaming && index == 0 && message.role == "assistant",
                                    waitSeconds = waitSeconds,
                                    busy = state.streaming,
                                    onRegenerate = { viewModel.regenerate(messages.size - 1 - index) },
                                    onEdit = { text -> viewModel.editUser(messages.size - 1 - index, text) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChatConfig && chat != null) {
        ChatConfigSheet(
            chat = chat,
            options = state.modelOptions,
            accounts = state.accounts,
            onDismiss = { showChatConfig = false },
            onSave = { config -> viewModel.updateChatConfig(config) }
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Text("Reintentar", style = MaterialTheme.typography.labelMedium)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cerrar error",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelSelectorBar(
    options: List<ModelOption>,
    active: ModelOption?,
    onSelect: (ModelOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = active?.let { "${it.providerLabel} · ${it.model}" } ?: "Sin modelo conectado",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Cambiar modelo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Conecta un proveedor en Ajustes") },
                        onClick = { expanded = false }
                    )
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.providerLabel} · ${option.model}") },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    streaming: Boolean,
    waitSeconds: Int,
    busy: Boolean,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit
) {
    val isUser = message.role == "user"
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isUser) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    message.attachments.forEach { attachment ->
                        Text(
                            text = "Adjunto: ${attachment.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    draft = message.content
                    editing = true
                },
                enabled = !busy,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Editar mensaje",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (message.content.isBlank() && !streaming) {
                Text(
                    text = "Sin respuesta. Puedes regenerarla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MessageContent(
                    raw = message.content,
                    streaming = streaming,
                    waitSeconds = waitSeconds,
                    isUser = false
                )
            }
            if (!streaming) {
                IconButton(
                    onClick = onRegenerate,
                    enabled = !busy,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Regenerar respuesta",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Editar mensaje", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        text = "Los mensajes posteriores a este se eliminarán y el modelo no los recordará.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editing = false
                        onEdit(draft)
                    },
                    enabled = draft.isNotBlank() || message.attachments.isNotEmpty()
                ) { Text("Reenviar") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        "Resume este texto",
        "Explícame un concepto",
        "Ayúdame a escribir un correo",
        "Revisa mi código"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "¿En qué puedo ayudarte?",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Conecta un proveedor y elige un modelo para empezar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onSuggestion(suggestion) },
                label = { Text(suggestion) },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    streaming: Boolean,
    pending: List<Attachment>,
    caps: ModelCaps,
    efforts: List<String>?,
    effort: String?,
    onEffort: (String?) -> Unit,
    onPick: (String) -> Unit,
    onRemovePending: (Int) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var effortMenu by remember { mutableStateOf(false) }
    val canSend = value.isNotBlank() || pending.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
                if (pending.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pending.forEachIndexed { index, item ->
                            AssistChip(
                                onClick = { onRemovePending(index) },
                                label = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Quitar",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Escribe tu mensaje") },
                    maxLines = 6,
                    enabled = !streaming,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !streaming) {
                            Icon(Icons.Filled.Add, contentDescription = "Adjuntar")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (caps.image) {
                                DropdownMenuItem(text = { Text("Imagen") }, onClick = { menuOpen = false; onPick("image") })
                            }
                            if (caps.audio) {
                                DropdownMenuItem(text = { Text("Audio") }, onClick = { menuOpen = false; onPick("audio") })
                            }
                            DropdownMenuItem(text = { Text("Archivo de texto") }, onClick = { menuOpen = false; onPick("text") })
                        }
                    }
                    if (efforts != null) {
                        Box {
                            AssistChip(
                                onClick = { effortMenu = true },
                                label = { Text("Razonamiento: " + effortLabel(effort)) }
                            )
                            DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                                DropdownMenuItem(text = { Text("Automático") }, onClick = { effortMenu = false; onEffort(null) })
                                efforts.forEach { option ->
                                    DropdownMenuItem(text = { Text(effortLabel(option)) }, onClick = { effortMenu = false; onEffort(option) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = if (streaming) onStop else onSend,
                        enabled = streaming || canSend
                    ) {
                        if (streaming) {
                            Icon(Icons.Filled.Close, contentDescription = "Detener")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
                    }
                }
            }
        }
    }
}

private fun mimeFor(kind: String): String = when (kind) {
    "image" -> "image/*"
    "audio" -> "audio/*"
    else -> "*/*"
}

private const val MAX_MEDIA_BYTES = 5 * 1024 * 1024
private const val MAX_TEXT_BYTES = 200 * 1024
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "kt", "java", "json", "xml", "py", "js", "ts", "csv", "log",
    "html", "css", "yaml", "yml", "gradle", "kts", "sh", "c", "cpp", "h", "go", "rs"
)

private fun readAttachment(context: Context, uri: Uri): Result<Attachment> = runCatching {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "archivo"
    val rawMime = resolver.getType(uri).orEmpty()
    val ext = name.substringAfterLast('.', "").lowercase()
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No se pudo leer el archivo")
    when {
        rawMime.startsWith("image/") || rawMime.startsWith("audio/") -> {
            require(bytes.size <= MAX_MEDIA_BYTES) { "Archivo demasiado grande (máximo 5 MB)" }
            Attachment(name, rawMime, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        rawMime.startsWith("text/") || ext in TEXT_EXTENSIONS -> {
            require(bytes.size <= MAX_TEXT_BYTES) { "Archivo de texto demasiado grande (máximo 200 KB)" }
            Attachment(name, "text/plain", String(bytes, Charsets.UTF_8))
        }
        else -> error("Tipo de archivo no compatible con este modelo")
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun groupLabel(ageMs: Long): String = when {
    ageMs < DAY_MS -> "Hoy"
    ageMs < 2 * DAY_MS -> "Ayer"
    ageMs < 7 * DAY_MS -> "Últimos 7 días"
    else -> "Anteriores"
}

@Composable
private fun HistoryDrawer(
    state: UiState,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val chats = remember(state.chats, query) {
        state.chats
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { it.updatedAt }
    }
    val groups = remember(chats, now) { chats.groupBy { groupLabel(now - it.updatedAt) } }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Text(
            text = "Chat-Lobi-Ai",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))
        DrawerLink(
            icon = Icons.Filled.Edit,
            label = "Custom Chats",
            onClick = { Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show() }
        )
        DrawerLink(
            icon = Icons.Filled.Build,
            label = "Customizations",
            onClick = { Toast.makeText(context, "Próximamente", Toast.LENGTH_SHORT).show() }
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nuevo chat")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar chats") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        DrawerSectionLabel("Historial")

        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (query.isBlank()) "Aún no hay conversaciones. Empieza una nueva." else "Sin resultados para esa búsqueda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                groups.forEach { (label, group) ->
                    item(key = "header-$label") {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(group, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            selected = chat.id == state.current?.id,
                            fallbackModel = state.settings.lastModel,
                            onSelect = onSelect,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        DrawerLink(
            icon = Icons.Filled.Settings,
            label = "Ajustes",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun DrawerLink(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    selected: Boolean,
    fallbackModel: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onSelect(chat.id) },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background.copy(alpha = 0f)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title.ifBlank { "Sin título" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val model = chat.config.model.ifBlank { fallbackModel }
                if (model.isNotBlank()) {
                    Text(
                        text = model,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { onDelete(chat.id) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Borrar chat",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun effortLabel(effort: String?): String = when (effort) {
    null -> "Automático"
    "low" -> "Bajo"
    "medium" -> "Medio"
    "high" -> "Alto"
    else -> effort
}
