package github.franco.chat.ai.text

data class ModelCaps(val image: Boolean, val audio: Boolean)

/**
 * Infiere capacidades del modelo a partir de su id. Los endpoints /models de la
 * mayoria de proveedores no informan modalidades, asi que es una aproximacion.
 */
object ModelCapabilities {
    private val imageHints = listOf(
        "gpt-4o", "gpt-4.1", "gpt-5", "gpt-4-turbo", "claude", "gemini",
        "vision", "-vl", "llava", "pixtral", "grok-4"
    )
    private val audioHints = listOf("audio")

    /** Niveles de razonamiento que el modelo parece aceptar, o null si no expone el control. */
    fun reasoningEfforts(modelId: String): List<String>? {
        val id = modelId.lowercase()
        val reasoning = Regex("(^|[/:._-])(o1|o3|o4)([-._:]|$)").containsMatchIn(id) ||
            listOf("gpt-5", "reasoner", "-r1", "qwq", "thinking").any { id.contains(it) }
        return if (reasoning) listOf("low", "medium", "high") else null
    }

    fun of(modelId: String): ModelCaps {
        val id = modelId.lowercase()
        return ModelCaps(
            image = imageHints.any { id.contains(it) },
            audio = audioHints.any { id.contains(it) }
        )
    }
}
