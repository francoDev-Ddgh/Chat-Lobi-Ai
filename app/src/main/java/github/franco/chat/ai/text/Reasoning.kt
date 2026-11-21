package github.franco.chat.ai.text

data class ParsedMessage(
    val reasoning: String,
    val body: String
)

/**
 * Separa el bloque de razonamiento del texto visible.
 * Tolera <think>, <thinking>, <reasoning> y <thought> (Gemma/Gemini las emiten asi),
 * etiquetas de cierre con espacios (</think >) y bloques que aun no se cerraron
 * mientras se transmite.
 */
object Reasoning {

    private val open = Regex("(?i)<(?:think|thinking|reasoning|thought|thoughts)\\b[^>]*>")
    private val close = Regex("(?i)</(?:think|thinking|reasoning|thought|thoughts)\\s*>")

    fun split(raw: String): ParsedMessage {
        if (raw.isEmpty()) return ParsedMessage("", "")
        val reasons = StringBuilder()
        val body = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val o = open.find(raw, i)
            if (o == null) {
                body.append(raw, i, raw.length)
                break
            }
            body.append(raw, i, o.range.first)
            val start = o.range.last + 1
            val c = close.find(raw, start)
            if (c == null) {
                reasons.append(raw, start, raw.length)
                break
            }
            if (reasons.isNotEmpty()) reasons.append("\n\n")
            reasons.append(raw, start, c.range.first)
            i = c.range.last + 1
        }
        val clean = close.replace(body.toString(), "").trim()
        return ParsedMessage(reasons.toString().trim(), clean)
    }

    fun bodyOnly(raw: String): String = split(raw).body
}
