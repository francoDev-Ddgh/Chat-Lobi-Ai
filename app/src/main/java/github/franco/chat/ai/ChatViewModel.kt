package github.franco.chat.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import github.franco.chat.ai.data.AppSettings
import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.data.ChatRepository
import github.franco.chat.ai.data.Message
import github.franco.chat.ai.data.ProviderConfig
import github.franco.chat.ai.net.ChatClient
import github.franco.chat.ai.net.ChatRequest
import github.franco.chat.ai.net.Providers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val chats: List<Chat> = emptyList(),
    val current: Chat? = null,
    val settings: AppSettings = AppSettings(),
    val hasKey: Boolean = false,
    val streaming: Boolean = false,
    val error: String? = null
)

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val client = ChatClient()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                val preset = Providers.byId(settings.config.presetId)
                _state.update {
                    it.copy(settings = settings, hasKey = repository.apiKey(preset.id) != null)
                }
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
        _state.update {
            it.copy(current = Chat(id = UUID.randomUUID().toString()), error = null)
        }
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

    fun send(text: String) {
        val snapshot = _state.value
        if (text.isBlank() || snapshot.streaming) return

        val base = snapshot.current
            ?: Chat(id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
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

        val config = snapshot.settings.config
        val preset = Providers.byId(config.presetId)

        streamJob = viewModelScope.launch {
            val apiKey = repository.apiKey(preset.id).orEmpty()
            if (preset.requiresKey && apiKey.isBlank()) {
                _state.update { it.copy(streaming = false, error = "Falta la API key de ${preset.label}") }
                persist()
                return@launch
            }

            val accumulated = StringBuilder()
            try {
                client.stream(
                    ChatRequest(
                        url = config.baseUrl.ifBlank { preset.baseUrl }.trimEnd('/') + "/chat/completions",
                        apiKey = apiKey,
                        model = config.model.ifBlank { preset.defaultModel },
                        messages = history,
                        system = config.systemPrompt,
                        temperature = config.temperature,
                        omitTemperature = preset.omitTemperature
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

    fun saveSettings(config: ProviderConfig) {
        viewModelScope.launch {
            repository.saveSettings(AppSettings(config = config))
            _state.update { it.copy(hasKey = repository.apiKey(config.presetId) != null) }
        }
    }

    fun saveApiKey(presetId: String, raw: String) {
        viewModelScope.launch {
            repository.saveApiKey(presetId, raw)
            _state.update { it.copy(hasKey = repository.apiKey(presetId) != null) }
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
