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
import github.franco.chat.ai.net.ChatClient
import github.franco.chat.ai.net.ChatRequest
import github.franco.chat.ai.net.ModelsClient
import github.franco.chat.ai.net.Providers
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
    val error: String? = null
) {
    val modelOptions: List<ModelOption>
        get() = settings.accounts.filter { it.enabled }.flatMap { account ->
            val preset = Providers.byId(account.presetId)
            val models = account.models.ifEmpty { listOf(preset.defaultModel) }
            models.map { ModelOption(account.presetId, preset.label, it) }
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
                val saved = Providers.all.filter { repository.apiKey(it.id) != null }.map { it.id }.toSet()
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
        streamJob = null
        _state.update { it.copy(streaming = false) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun selectModel(option: ModelOption) {
        val chat = _state.value.current ?: return
        val updated = chat.copy(
            config = chat.config.copy(providerId = option.providerId, model = option.model)
        )
        _state.update { it.copy(current = updated) }
        viewModelScope.launch {
            repository.saveSettings(
                repository.settingsFlow.let { it } .let { _ -> _state.value.settings }
                    .copy(lastProviderId = option.providerId, lastModel = option.model)
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

    fun setProviderEnabled(presetId: String, enabled: Boolean) {
        viewModelScope.launch {
            val account = repository.account(presetId) ?: ProviderAccount(presetId)
            repository.updateAccount(account.copy(enabled = enabled))
        }
    }

    fun saveAccountSettings(presetId: String, baseUrl: String) {
        viewModelScope.launch {
            val account = repository.account(presetId) ?: ProviderAccount(presetId)
            repository.updateAccount(account.copy(baseUrl = baseUrl.trim()))
        }
    }

    fun toggleModel(presetId: String, model: String) {
        viewModelScope.launch {
            val account = repository.account(presetId) ?: ProviderAccount(presetId, enabled = true)
            val models = if (account.models.contains(model)) account.models - model else account.models + model
            repository.updateAccount(account.copy(models = models))
        }
    }

    fun refreshModels(presetId: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val preset = Providers.byId(presetId)
            val account = repository.account(presetId)
            val baseUrl = account?.baseUrl?.ifBlank { preset.baseUrl } ?: preset.baseUrl
            val key = repository.apiKey(presetId).orEmpty()
            runCatching {
                modelsClient.fetch(baseUrl, key, preset.id == "anthropic")
            }.onSuccess { models ->
                val current = repository.account(presetId) ?: ProviderAccount(presetId)
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

    fun saveApiKey(presetId: String, raw: String) {
        viewModelScope.launch {
            repository.saveApiKey(presetId, raw)
            val saved = Providers.all.filter { repository.apiKey(it.id) != null }.map { it.id }.toSet()
            _state.update { it.copy(savedKeys = saved) }
        }
    }

    fun send(text: String) {
        val snapshot = _state.value
        if (text.isBlank() || snapshot.streaming) return

        val option = snapshot.active
        if (option == null) {
            _state.update { it.copy(error = "Conecta un proveedor en Ajustes > Proveedores") }
            return
        }

        val preset = Providers.byId(option.providerId)
        val account = snapshot.settings.accounts.firstOrNull { it.presetId == option.providerId }

        val base = snapshot.current ?: Chat(id = UUID.randomUUID().toString())
        val title = if (base.messages.isEmpty()) text.trim().take(48) else base.title
        val withUser = base.copy(
            messages = base.messages + Message("user", text.trim()),
            title = title,
            updatedAt = System.currentTimeMillis()
        )
        val history = withUser.messages
        val assistantIndex = history.size

        _state.update {
            it.copy(
                current = withUser.copy(messages = history + Message("assistant", "")),
                streaming = true,
                error = null
            )
        }

        streamJob = viewModelScope.launch {
            val apiKey = repository.apiKey(option.providerId).orEmpty()
            if (preset.requiresKey && apiKey.isBlank()) {
                _state.update { it.copy(streaming = false, error = "Falta la API key de ${preset.label}") }
                persist()
                return@launch
            }

            val accumulated = StringBuilder()
            val baseUrl = account?.baseUrl?.ifBlank { preset.baseUrl } ?: preset.baseUrl
            try {
                client.stream(
                    ChatRequest(
                        url = baseUrl.trimEnd('/') + "/chat/completions",
                        apiKey = apiKey,
                        model = option.model,
                        messages = history,
                        system = withUser.config.systemPrompt,
                        temperature = withUser.config.temperature,
                        omitTemperature = !preset.supportsTemperature
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
