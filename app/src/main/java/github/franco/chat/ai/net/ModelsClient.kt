package github.franco.chat.ai.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelsClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(baseUrl: String, apiKey: String, anthropicStyle: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/models"

            fun execute(build: (Request.Builder) -> Unit): Pair<Int, String> {
                val builder = Request.Builder().url(url).get().header("Accept", "application/json")
                build(builder)
                http.newCall(builder.build()).execute().use { response ->
                    return response.code to response.body?.string().orEmpty()
                }
            }

            var result = execute { it.header("Authorization", "Bearer $apiKey") }
            if (result.first !in 200..299 && anthropicStyle && apiKey.isNotBlank()) {
                result = execute {
                    it.header("x-api-key", apiKey)
                    it.header("anthropic-version", "2026-10-21")
                }
            }
            if (result.first !in 200..299) throw IOException("HTTP ${result.first}")

            val root = runCatching { json.parseToJsonElement(result.second) as? JsonObject }.getOrNull()
                ?: throw IOException("Respuesta invalida")
            val data = root["data"] as? JsonArray ?: return@withContext emptyList()

            data.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                id.removePrefix("models/")
            }.distinct().sorted()
        }
}
