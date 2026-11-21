package github.franco.chat.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import github.franco.chat.ai.data.AppSettings
import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.data.ChatConfig
import github.franco.chat.ai.data.ChatRepository
import github.franco.chat.ai.data.Message
import github.franco.chat.ai.data.ProviderAccount
import github.franco.chat.ai.net.ApiTypes
import github.franco.chat.ai.net.ChatClient
import github.franco.chat.ai.net.ChatRequest
import github.franco.chat.ai.net.ModelsClient
import github.franco.chat.ai.net.Providers
import github.franco.chat.ai.text.Reasoning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ModelOption(
    val providerId: String,
    val providerLabel: String,
    val model: String
)

data class UiState(
    val chats: List<Chat> = emptyList(),
    val current: Chat? = null,
    val settings: AppSettings = AppSettings(),
    val savedKeys: Set<String> = emptySet(),
    val streaming: Boolean = false,
    val busy: Boolean = false,
    val pingBusy: Boolean = false,
    val pingResult: String? = null,
    val error: String? = null
) {
    val accounts: List<ProviderAccount>
        get() = settings.accounts.ifEmpty { Providers.all }

    fun account(id: String): ProviderAccount? = accounts.firstOrNull { it.id == id }

    val modelOptions: List<ModelOption>
        get() = accounts.filter { it.enabled }.flatMap { account ->
            account.models.map { ModelOption(account.id, account.label, it) }
        }

    val active: ModelOption?
        get() {
            val options = modelOptions
            if (options.isEmpty()) return null
            val config = current?.config
            if (config != null && config.providerId.isNotBlank() && config.model.isNotBlank()) {
                options.firstOrNull { it.providerId == config.providerId && it.model == config.model }
                    ?.let { return it }
            }
            if (settings.lastProviderId.isNotBlank()) {
                options.firstOrNull { it.providerId == settings.lastProviderId && it.model == settings.lastModel }
                    ?.let { return it }
            }
            return options.first()
        }
}

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val client = ChatClient()
    private val modelsClient = ModelsClient()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                val saved = settings.accounts.filter { repository.apiKey(it.id) != null }.map { it.id }.toSet()
                _state.update { it.copy(settings = settings, savedKeys = saved) }
            }
        }
        viewModelScope.launch {
            repository.chatsFlow.collect { chats ->
                _state.update { current ->
                    current.copy(
                        chats = chats,
                        current = current.current ?: chats.maxByOrNull { it.updatedAt }
                    )
                }
            }
        }
    }

    fun newChat() {
        persist()
        stop()
        _state.update { it.copy(current = Chat(id = UUID.randomUUID().toString()), error = null) }
    }

    fun selectChat(id: String) {
        persist()
        stop()
        val chat = _state.value.chats.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(current = chat, error = null) }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            repository.deleteChat(id)
            if (_state.value.current?.id == id) {
                _state.update { it.copy(current = Chat(id = UUID.randomUUID().toString())) }
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        client.cancel()
        streamJob = null
        _state.update { it.copy(streaming = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun retry() {
        val snapshot = _state.value
        if (snapshot.streaming) return
        val chat = snapshot.current ?: return
        val userIndex = chat.messages.indexOfLast { it.role == "user" }
        if (userIndex < 0) return
        val text = chat.messages[userIndex].content
        val trimmed = chat.copy(messages = chat.messages.take(userIndex))
        _state.update { it.copy(current = trimmed, error = null, pingResult = null) }
        send(text)
    }

    fun selectModel(option: ModelOption) {
        val chat = _state.value.current ?: return
        val updated = chat.copy(
            config = chat.config.copy(providerId = option.providerId, model = option.model)
        )
        _state.update { it.copy(current = updated) }
        viewModelScope.launch {
            repository.saveSettings(
                _state.value.settings.copy(
                    lastProviderId = option.providerId,
                    lastModel = option.model
                )
            )
            if (updated.messages.isNotEmpty()) repository.upsertChat(updated)
        }
    }

    fun updateChatConfig(config: ChatConfig) {
        val chat = _state.value.current ?: return
        val updated = chat.copy(config = config)
        _state.update { it.copy(current = updated) }
        viewModelScope.launch {
            repository.saveSettings(
                _state.value.settings.copy(
                    lastProviderId = config.providerId.ifBlank { _state.value.settings.lastProviderId },
                    lastModel = config.model.ifBlank { _state.value.settings.lastModel }
                )
            )
            if (updated.messages.isNotEmpty()) repository.upsertChat(updated)
        }
    }

    fun setProviderEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val account = repository.account(id) ?: return@launch
            repository.updateAccount(account.copy(enabled = enabled))
        }
    }

    fun setStreaming(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val account = repository.account(id) ?: return@launch
            repository.updateAccount(account.copy(streaming = enabled))
        }
    }

    fun saveAccountSettings(id: String, label: String, baseUrl: String, apiType: String) {
        viewModelScope.launch {
            val account = repository.account(id) ?: return@launch
            repository.updateAccount(
                account.copy(
                    label = label.trim().ifBlank { account.label },
                    baseUrl = baseUrl.trim(),
                    apiType = apiType,
                    supportsTemperature = apiType != ApiTypes.ANTHROPIC
                )
            )
        }
    }

    fun createCustomProvider(
        label: String,
        baseUrl: String,
        apiType: String,
        apiKey: String,
        requiresKey: Boolean
    ) {
        val id = "custom-" + UUID.randomUUID().toString().take(8)
        viewModelScope.launch {
            repository.updateAccount(
                ProviderAccount(
                    id = id,
                    label = label.trim().ifBlank { "Proveedor personalizado" },
                    baseUrl = baseUrl.trim(),
                    apiType = apiType,
                    enabled = true,
                    custom = true,
                    requiresKey = requiresKey,
                    supportsTemperature = apiType != ApiTypes.ANTHROPIC,
                    models = emptyList()
                )
            )
            if (apiKey.isNotBlank()) repository.saveApiKey(id, apiKey)
            refreshKeys()
            refreshModels(id)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.removeAccount(id)
            refreshKeys()
        }
    }

    fun toggleModel(id: String, model: String) {
        viewModelScope.launch {
            val account = repository.account(id) ?: return@launch
            val models = if (account.models.contains(model)) account.models - model else account.models + model
            repository.updateAccount(account.copy(models = models.sorted()))
        }
    }

    fun refreshModels(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val account = repository.account(id)
            if (account == null) {
                _state.update { it.copy(busy = false) }
                return@launch
            }
            val key = repository.apiKey(id).orEmpty()
            val anthropicStyle = account.apiType == ApiTypes.ANTHROPIC
            runCatching {
                modelsClient.fetch(account.baseUrl, key, anthropicStyle)
            }.onSuccess { models ->
                val current = repository.account(id) ?: return@onSuccess
                repository.updateAccount(
                    current.copy(models = (current.models + models).distinct().sorted())
                )
                if (models.isEmpty()) {
                    _state.update { it.copy(error = "El proveedor no devolvio modelos. Agrega uno manualmente.") }
                }
            }.onFailure { error ->
                _state.update { it.copy(error = "Modelos: ${error.message ?: "error"}") }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    fun testConnection(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(pingBusy = true, pingResult = null) }
            val account = repository.account(id)
            if (account == null) {
                _state.update { it.copy(pingBusy = false, pingResult = "Proveedor no encontrado") }
                return@launch
            }
            val key = repository.apiKey(id).orEmpty()
            val result = runCatching {
                modelsClient.ping(account.baseUrl, key, account.apiType == ApiTypes.ANTHROPIC)
            }
            _state.update {
                it.copy(
                    pingBusy = false,
                    pingResult = result.fold(
                        onSuccess = { ms -> "OK - respuesta en $ms ms" },
                        onFailure = { e -> "Sin conexion: ${e.message ?: "error"}" }
                    )
                )
            }
        }
    }

    fun saveApiKey(id: String, raw: String) {
        viewModelScope.launch {
            repository.saveApiKey(id, raw)
            refreshKeys()
        }
    }

    private suspend fun refreshKeys() {
        val saved = _state.value.accounts.filter { repository.apiKey(it.id) != null }.map { it.id }.toSet()
        _state.update { it.copy(savedKeys = saved) }
    }

    fun send(text: String) {
        val snapshot = _state.value
        if (text.isBlank() || snapshot.streaming) return

        val option = snapshot.active
        if (option == null) {
            _state.update { it.copy(error = "Conecta un proveedor en Ajustes > Proveedores") }
            return
        }

        val account = snapshot.account(option.providerId)
        if (account == null) {
            _state.update { it.copy(error = "Proveedor no configurado") }
            return
        }

        val base = snapshot.current ?: Chat(id = UUID.randomUUID().toString())
        val title = if (base.messages.isEmpty()) text.trim().take(48) else base.title
        val withUser = base.copy(
            messages = base.messages + Message("user", text.trim()),
            title = title,
            updatedAt = System.currentTimeMillis()
        )
        // Al reenviar el historial quitamos el razonamiento: es ruido y gasta tokens.
        val history = withUser.messages.map { message ->
            if (message.role == "assistant") {
                val parsed = Reasoning.split(message.content)
                if (parsed.body.isBlank()) message else message.copy(content = parsed.body)
            } else {
                message
            }
        }
        val assistantIndex = history.size

        _state.update {
            it.copy(
                current = withUser.copy(messages = history + Message("assistant", "")),
                streaming = true,
                error = null
            )
        }

        streamJob = viewModelScope.launch {
            val apiKey = repository.apiKey(account.id).orEmpty()
            if (account.requiresKey && apiKey.isBlank()) {
                _state.update { it.copy(streaming = false, error = "Falta la API key de ${account.label}") }
                persist()
                return@launch
            }

            val accumulated = StringBuilder()
            try {
                client.stream(
                    ChatRequest(
                        url = account.baseUrl.trimEnd('/') + "/chat/completions",
                        apiKey = apiKey,
                        model = option.model,
                        messages = history,
                        system = withUser.config.systemPrompt,
                        temperature = withUser.config.temperature,
                        omitTemperature = !account.supportsTemperature,
                        streaming = account.streaming,
                        apiType = account.apiType
                    )
                ).collect { delta ->
                    accumulated.append(delta)
                    updateAssistant(assistantIndex, accumulated.toString())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Error desconocido") }
            } finally {
                _state.update { it.copy(streaming = false) }
                persist()
            }
        }
    }

    private fun updateAssistant(index: Int, text: String) {
        _state.update { state ->
            val chat = state.current ?: return@update state
            if (index !in chat.messages.indices) return@update state
            val messages = chat.messages.toMutableList()
            messages[index] = messages[index].copy(content = text)
            state.copy(current = chat.copy(messages = messages, updatedAt = System.currentTimeMillis()))
        }
    }

    private fun persist() {
        val chat = _state.value.current ?: return
        if (chat.messages.isEmpty()) return
        viewModelScope.launch { repository.upsertChat(chat) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(ChatRepository(context.applicationContext)) }
        }
    }
}
