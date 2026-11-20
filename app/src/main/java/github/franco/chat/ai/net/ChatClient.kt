package github.franco.chat.ai.net

import github.franco.chat.ai.data.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatRequest(
    val url: String,
    val apiKey: String,
    val model: String,
    val messages: List<Message>,
    val system: String = "",
    val temperature: Double? = null,
    val omitTemperature: Boolean = false
)

class ChatClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val media = "application/json; charset=utf-8".toMediaType()

    fun stream(request: ChatRequest): Flow<String> = flow {
        val payload = buildJsonObject {
            put("model", request.model)
            put("stream", true)
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
                        put("content", message.content)
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
            .header("Accept", "text/event-stream")
        if (request.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${request.apiKey}")
        }

        val call = http.newCall(builder.build())
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string()?.take(800).orEmpty()
                    throw IOException("HTTP ${response.code}: $detail")
                }
                val source = response.body?.source() ?: throw IOException("Respuesta vacia")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val obj = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull() ?: continue
                    val choices = obj["choices"] as? JsonArray ?: continue
                    val first = choices.firstOrNull() as? JsonObject ?: continue
                    val delta = first["delta"] as? JsonObject ?: continue
                    val text = (delta["content"] as? JsonPrimitive)?.contentOrNull
                    if (!text.isNullOrEmpty()) emit(text)
                }
            }
        } finally {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
