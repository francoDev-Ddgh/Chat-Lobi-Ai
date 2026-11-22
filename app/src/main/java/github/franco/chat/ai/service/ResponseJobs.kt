package github.franco.chat.ai.service

import github.franco.chat.ai.data.Chat
import github.franco.chat.ai.net.ChatRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Trabajo pendiente: el chat (con el mensaje del usuario) y la petición al proveedor. */
data class ResponseJob(
    val chat: Chat,
    val request: ChatRequest
)

/** Entrega los trabajos del ViewModel al servicio sin meter objetos complejos en el Intent. */
object ResponseJobs {
    private val pending = ConcurrentHashMap<String, ResponseJob>()

    fun put(job: ResponseJob): String {
        val id = UUID.randomUUID().toString()
        pending[id] = job
        return id
    }

    fun take(id: String): ResponseJob? = pending.remove(id)
}

/** Estado en vivo de una respuesta. La UI lo muestra si la app está abierta. */
data class ResponseUpdate(
    val chatId: String,
    val text: String,
    val streaming: Boolean,
    val error: String?
)

object ResponseBus {
    private val _updates = MutableStateFlow<ResponseUpdate?>(null)
    val updates: StateFlow<ResponseUpdate?> = _updates.asStateFlow()

    fun publish(update: ResponseUpdate) {
        _updates.value = update
    }
}
