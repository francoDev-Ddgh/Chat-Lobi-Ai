package github.franco.chat.ai.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.ui.platform.LocalContext
import github.franco.chat.ai.data.Attachment
import github.franco.chat.ai.text.ModelCapabilities
import github.franco.chat.ai.text.ModelCaps
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.franco.chat.ai.ChatViewModel
import github.franco.chat.ai.ModelOption
import github.franco.chat.ai.UiState
import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.data.Message
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet {
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
            }
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        Column {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = chat?.title ?: "Chat-Lobi-Ai",
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
                                onSelect = { viewModel.selectModel(it) },
                                onConfigure = { showChatConfig = true }
                            )
                            HorizontalDivider()
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
                            EmptyState(modifier = Modifier.fillMaxSize())
                        } else {
                            val reversed = remember(messages) { messages.asReversed() }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onSelect: (ModelOption) -> Unit,
    onConfigure: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = active?.let { "${it.providerLabel} - ${it.model}" } ?: "Sin modelo conectado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onConfigure) {
            Icon(Icons.Filled.Build, contentDescription = "Ajustes del chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Conecta un proveedor en Ajustes") },
                    onClick = { expanded = false }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.providerLabel} - ${option.model}") },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
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
    val container = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background.copy(alpha = 0f)
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(20.dp),
            modifier = if (isUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (isUser) {
                    Column {
                        message.attachments.forEach { attachment ->
                            Text(
                                text = "Adjunto: ${attachment.name}",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (message.content.isNotBlank()) {
                            Text(
                                text = message.content,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    MessageContent(
                        raw = message.content,
                        streaming = streaming,
                        waitSeconds = waitSeconds,
                        isUser = false
                    )
                }
            }
        }
    }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (isUser) {
                IconButton(
                    onClick = {
                        draft = message.content
                        editing = true
                    },
                    enabled = !busy
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Editar mensaje")
                }
            } else if (!streaming) {
                IconButton(onClick = onRegenerate, enabled = !busy) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerar respuesta")
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Editar mensaje") },
            text = {
                Column {
                    Text(
                        text = "Los mensajes posteriores a este se eliminaran y el modelo no los recordara.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Chat-Lobi-Ai", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Conecta un proveedor y elige un modelo arriba.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (efforts != null) {
                Box {
                    AssistChip(
                        onClick = { effortMenu = true },
                        label = { Text("Razonamiento: " + effortLabel(effort)) }
                    )
                    DropdownMenu(expanded = effortMenu, onDismissRequest = { effortMenu = false }) {
                        DropdownMenuItem(text = { Text("Automatico") }, onClick = { effortMenu = false; onEffort(null) })
                        efforts.forEach { option ->
                            DropdownMenuItem(text = { Text(effortLabel(option)) }, onClick = { effortMenu = false; onEffort(option) })
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
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
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
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
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un mensaje") },
                    maxLines = 5,
                    enabled = !streaming,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = if (streaming) onStop else onSend,
                    enabled = if (streaming) true else value.isNotBlank() || pending.isNotEmpty(),
                    modifier = Modifier.padding(bottom = 4.dp)
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
            require(bytes.size <= MAX_MEDIA_BYTES) { "Archivo demasiado grande (maximo 5 MB)" }
            Attachment(name, rawMime, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        rawMime.startsWith("text/") || ext in TEXT_EXTENSIONS -> {
            require(bytes.size <= MAX_TEXT_BYTES) { "Archivo de texto demasiado grande (maximo 200 KB)" }
            Attachment(name, "text/plain", String(bytes, Charsets.UTF_8))
        }
        else -> error("Tipo de archivo no compatible con este modelo")
    }
}

@Composable
private fun HistoryDrawer(
    state: UiState,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val chats = remember(state.chats) { state.chats.sortedByDescending { it.updatedAt } }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text(
            text = "Historial",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        NavigationDrawerItem(
            label = { Text("Nuevo chat") },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onNewChat
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chats, key = { it.id }) { chat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(chat.id) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chat.title.ifBlank { "Sin titulo" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (chat.id == state.current?.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        val model = chat.config.model.ifBlank { state.settings.lastModel }
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
                    IconButton(onClick = { onDelete(chat.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Borrar chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Ajustes") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = onOpenSettings
        )
    }
}

private fun effortLabel(effort: String?): String = when (effort) {
    null -> "Automatico"
    "low" -> "Bajo"
    "medium" -> "Medio"
    "high" -> "Alto"
    else -> effort
}
