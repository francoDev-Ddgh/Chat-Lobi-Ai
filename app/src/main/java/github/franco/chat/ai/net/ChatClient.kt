package github.franco.chat.ai.net

import github.franco.chat.ai.data.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatRequest(
    val url: String,
    val apiKey: String,
    val model: String,
    val messages: List<Message>,
    val system: String = "",
    val temperature: Double? = null,
    val omitTemperature: Boolean = false,
    val streaming: Boolean = true,
    val apiType: String = "openai"
)

private class ApiException(val status: Int, message: String) : IOException(message)

/** Envuelve los deltas y traduce reasoning_content a etiquetas <think>. */
private class StreamWrap(val emit: suspend (String) -> Unit) {
    var reasoningOpen = false

    suspend fun reasoning(delta: String) {
        if (!reasoningOpen) {
            reasoningOpen = true
            emit("<think>\n")
        }
        emit(delta)
    }

    suspend fun content(delta: String) {
        if (reasoningOpen) {
            reasoningOpen = false
            emit("\n</think>\n")
        }
        emit(delta)
    }

    suspend fun finish() {
        if (reasoningOpen) {
            reasoningOpen = false
            emit("\n</think>")
        }
    }
}

class ChatClient {

    companion object {
        const val SLOW_TIMEOUT_SECONDS = 900L
        private const val MAX_RETRIES = 3
    }

    // Red lenta o inestable: se espera lo que haga falta para conectar y para leer.
    private val http = OkHttpClient.Builder()
        .connectTimeout(SLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(SLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var activeCall: Call? = null

    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    fun stream(request: ChatRequest): Flow<String> = flow {
        var attempt = 0
        while (true) {
            var produced = false
            val emitDelta: suspend (String) -> Unit = { produced = true; emit(it) }
            try {
                if (request.streaming) streamOnce(request, emitDelta)
                else nonStreamingOnce(request, emitDelta)
                return@flow
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Solo reintentamos si no llego nada: reintentar a medias duplicaria texto.
                val canRetry = !produced && attempt < MAX_RETRIES && isRetryable(error)
                if (!canRetry) throw error
                attempt++
                delay(900L * attempt)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOnce(request: ChatRequest, emit: suspend (String) -> Unit) {
        val call = http.newCall(buildRequest(request, stream = true))
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw apiError(response)
                val body = response.body ?: throw IOException("Respuesta vacia del proveedor")
                val contentType = response.header("Content-Type").orEmpty()

                // Algunos proxies ignoran stream=true y devuelven un JSON completo.
                if (!contentType.contains("event-stream", true)) {
                    val text = body.string()
                    val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        ?: throw IOException("El proveedor devolvio una respuesta inesperada")
                    emitFull(obj, emit)
                    return
                }

                val source = body.source()
                val wrap = StreamWrap(emit)
                val pending = StringBuilder()

                suspend fun consume(raw: String): Boolean {
                    val trimmed = raw.trim()
                    if (trimmed == "[DONE]") return false
                    val obj = runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()
                        ?: return true
                    val (content, reasoning) = readDelta(obj)
                    if (!reasoning.isNullOrEmpty()) wrap.reasoning(reasoning)
                    if (!content.isNullOrEmpty()) wrap.content(content)
                    return true
                }

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        if (pending.isNotEmpty()) {
                            val chunk = pending.toString()
                            pending.setLength(0)
                            if (!consume(chunk)) break
                        }
                        continue
                    }
                    if (line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val value = line.substring(5).trim()
                    if (value.isEmpty()) continue
                    if (pending.isNotEmpty()) pending.append('\n')
                    pending.append(value)
                }
                if (pending.isNotEmpty()) consume(pending.toString())
                wrap.finish()
            }
        } finally {
            activeCall = null
            call.cancel()
        }
    }

    private suspend fun nonStreamingOnce(request: ChatRequest, emit: suspend (String) -> Unit) {
        val call = http.newCall(buildRequest(request, stream = false))
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw apiError(response)
                val text = response.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                    ?: throw IOException("El proveedor devolvio una respuesta inesperada")
                emitFull(obj, emit)
            }
        } finally {
            activeCall = null
            call.cancel()
        }
    }

    private suspend fun emitFull(obj: JsonObject, emit: suspend (String) -> Unit) {
        val choices = obj["choices"] as? JsonArray ?: return
        val first = choices.firstOrNull() as? JsonObject ?: return
        val message = first["message"] as? JsonObject ?: return
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val reasoning = (message["reasoning_content"] as? JsonPrimitive)?.contentOrNull
            ?: (message["reasoning"] as? JsonPrimitive)?.contentOrNull
        if (!reasoning.isNullOrBlank()) {
            emit("<think>\n")
            emit(reasoning)
            emit("\n</think>\n")
        }
        if (content.isNotEmpty()) emit(content)
    }

    private fun readDelta(obj: JsonObject): Pair<String?, String?> {
        val choices = obj["choices"] as? JsonArray ?: return null to null
        val first = choices.firstOrNull() as? JsonObject ?: return null to null
        val node = (first["delta"] as? JsonObject) ?: (first["message"] as? JsonObject) ?: return null to null
        val content = (node["content"] as? JsonPrimitive)?.contentOrNull
        val reasoning = (node["reasoning_content"] as? JsonPrimitive)?.contentOrNull
            ?: (node["reasoning"] as? JsonPrimitive)?.contentOrNull
        return content to reasoning
    }

    private fun buildRequest(request: ChatRequest, stream: Boolean): Request {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", stream)
            putJsonArray("messages") {
                if (request.system.isNotBlank()) {
                    addJsonObject {
                        put("role", "system")
                        put("content", request.system)
                    }
                }
                request.messages.forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        if (message.attachments.isEmpty()) {
                            put("content", message.content)
                        } else {
                            putJsonArray("content") {
                                if (message.content.isNotBlank()) {
                                    addJsonObject {
                                        put("type", "text")
                                        put("text", message.content)
                                    }
                                }
                                message.attachments.forEach { attachment ->
                                    addJsonObject {
                                        if (attachment.mime.startsWith("audio/")) {
                                            put("type", "input_audio")
                                            putJsonObject("input_audio") {
                                                put("data", attachment.data)
                                                put("format", audioFormat(attachment.mime))
                                            }
                                        } else {
                                            put("type", "image_url")
                                            putJsonObject("image_url") {
                                                put("url", "data:${attachment.mime};base64,${attachment.data}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val temp = request.temperature
            if (temp != null && !request.omitTemperature) {
                put("temperature", JsonPrimitive(temp))
            }
        }

        val builder = Request.Builder()
            .url(request.url)
            .post(payload.toString().toRequestBody(media))
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Chat-Lobi-Ai/1.3 (Android)")

        if (request.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${request.apiKey}")
            when (request.apiType) {
                ApiTypes.ANTHROPIC -> {
                    builder.header("x-api-key", request.apiKey)
                    builder.header("anthropic-version", "2023-06-01")
                }
                ApiTypes.GEMINI -> builder.header("x-goog-api-key", request.apiKey)
            }
        }
        return builder.build()
    }

    private fun apiError(response: Response): IOException {
        val detail = runCatching { response.body?.string() }.getOrNull().orEmpty().take(600)
        val hint = when (response.code) {
            401, 403 -> "API key rechazada o sin permisos"
            404 -> "URL base incorrecta (404)"
            408 -> "El proveedor tardo demasiado"
            429 -> "Limite de uso alcanzado (429)"
            in 500..599 -> "El proveedor fallo (${response.code})"
            else -> "Error HTTP ${response.code}"
        }
        val suffix = if (detail.isBlank()) "" else ": $detail"
        return ApiException(response.code, "$hint$suffix")
    }

    private fun isRetryable(error: Exception): Boolean = when (error) {
        is ApiException -> error.status >= 500 || error.status == 408
        is IOException -> true
        else -> false
    }
}

private fun audioFormat(mime: String): String = when {
    mime.contains("mpeg") -> "mp3"
    mime.contains("wav") -> "wav"
    else -> mime.substringAfter('/')
}
