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
        .connectTimeout(ChatClient.SLOW_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // El ping no puede quedarse 15 minutos esperando: es solo una prueba.
    private val pingHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(baseUrl: String, apiKey: String, anthropicStyle: Boolean): ModelListing =
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
                    it.header("anthropic-version", "2023-06-01")
                }
            }
            if (result.first !in 200..299) throw IOException("HTTP ${result.first}")

            val root = runCatching { json.parseToJsonElement(result.second) as? JsonObject }.getOrNull()
                ?: throw IOException("Respuesta invalida")
            val data = root["data"] as? JsonArray ?: return@withContext ModelListing(emptyList(), emptyList(), emptyList())

            val ids = mutableListOf<String>()
            val reasoning = mutableListOf<String>()
            val withMeta = mutableListOf<String>()
            data.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                val id = ((obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@forEach).removePrefix("models/")
                ids += id
                val params = obj["supported_parameters"] as? JsonArray
                if (params != null) {
                    withMeta += id
                    val names = params.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    if (names.any { it == "reasoning_effort" || it == "reasoning" }) reasoning += id
                }
            }
            ModelListing(ids.distinct().sorted(), reasoning.distinct().sorted(), withMeta.distinct().sorted())
        }

    suspend fun ping(baseUrl: String, apiKey: String, anthropicStyle: Boolean): Long =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/models"
            val started = System.nanoTime()

            fun call(build: (Request.Builder) -> Unit): Int {
                val builder = Request.Builder().url(url).get().header("Accept", "application/json")
                build(builder)
                pingHttp.newCall(builder.build()).execute().use { response ->
                    response.body?.close()
                    return response.code
                }
            }

            var code = call { it.header("Authorization", "Bearer $apiKey") }
            if (code !in 200..299 && anthropicStyle && apiKey.isNotBlank()) {
                code = call {
                    it.header("x-api-key", apiKey)
                    it.header("anthropic-version", "2023-06-01")
                }
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            (System.nanoTime() - started) / 1_000_000
        }
}

/** Modelos del proveedor. [reasoning] = los que exponen control de razonamiento; [withMeta] = los que informaron sus parametros. */
data class ModelListing(val ids: List<String>, val reasoning: List<String>, val withMeta: List<String>)
