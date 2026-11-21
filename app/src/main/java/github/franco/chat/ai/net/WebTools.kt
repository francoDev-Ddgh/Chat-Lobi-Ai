package github.franco.chat.ai.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Herramientas locales que el modelo puede invocar: busqueda web y lectura de paginas. */
object WebTools {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_PAGE_CHARS = 6000

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val specs: JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "web_search")
                put("description", "Busca en internet y devuelve hasta 5 resultados con titulo, URL y fragmento.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Consulta de busqueda")
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("query")) }
                }
            }
        })
        add(buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", "open_url")
                put("description", "Abre una pagina web http o https y devuelve su texto principal.")
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("url") {
                            put("type", "string")
                            put("description", "URL completa de la pagina")
                        }
                    }
                    putJsonArray("required") { add(JsonPrimitive("url")) }
                }
            }
        })
    }

    suspend fun execute(name: String, argsJson: String): String = withContext(Dispatchers.IO) {
        val args: JsonObject = runCatching { Json.parseToJsonElement(argsJson).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }
        runCatching {
            when (name) {
                "web_search" -> search(args["query"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "open_url" -> openUrl(args["url"]?.jsonPrimitive?.contentOrNull.orEmpty())
                else -> "Herramienta desconocida: $name"
            }
        }.getOrElse { "Error al ejecutar $name: ${it.message ?: "desconocido"}" }
    }

    private fun search(query: String): String {
        require(query.isNotBlank()) { "consulta vacia" }
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val html = get(url)
        val links = Regex("""<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).toList()
        val snippets = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).map { clean(it.groupValues[1]) }.toList()
        if (links.isEmpty()) return "Sin resultados para: $query"
        return links.take(5).mapIndexed { i, m ->
            "${i + 1}. ${clean(m.groupValues[2])}\nURL: ${resolveHref(m.groupValues[1])}\n${snippets.getOrElse(i) { "" }}"
        }.joinToString("\n\n")
    }

    private fun openUrl(url: String): String {
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL no valida" }
        val body = get(url)
        val withoutNoise = body.replace(Regex("(?is)<(script|style|nav|header|footer|svg)[^>]*>.*?</\\1>"), " ")
        val text = clean(withoutNoise)
        return if (text.length > MAX_PAGE_CHARS) text.take(MAX_PAGE_CHARS) + " [texto recortado]" else text
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,text/plain;q=0.9,*/*;q=0.5")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty().take(400_000)
        }
    }

    private fun resolveHref(raw: String): String {
        val href = raw.replace("&amp;", "&")
        val fixed = if (href.startsWith("//")) "https:$href" else href
        val uddg = Regex("[?&]uddg=([^&]+)").find(fixed)?.groupValues?.get(1)
        return if (uddg != null) URLDecoder.decode(uddg, "UTF-8") else fixed
    }

    private fun clean(html: String): String = html
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
