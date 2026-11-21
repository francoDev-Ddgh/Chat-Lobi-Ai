package github.franco.chat.ai.net

import github.franco.chat.ai.data.ProviderAccount

object ApiTypes {
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"

    val all: List<Triple<String, String, String>> = listOf(
        Triple(
            OPENAI,
            "Compatible con OpenAI",
            "POST /chat/completions con Bearer. Sirve para OpenAI, Groq, OpenRouter, Ollama, LM Studio y casi cualquier servidor."
        ),
        Triple(
            ANTHROPIC,
            "Compatible con Claude",
            "Endpoint compatible de Anthropic (api.anthropic.com/v1). Ignora temperature y top_p."
        ),
        Triple(
            GEMINI,
            "Compatible con Gemini",
            "Capa OpenAI de Google. La base suele ser generativelanguage.googleapis.com/v1beta/openai"
        )
    )

    fun readable(id: String): String = all.firstOrNull { it.first == id }?.second ?: "Compatible con OpenAI"
    fun hint(id: String): String = all.firstOrNull { it.first == id }?.third.orEmpty()
}

object Providers {
    val all: List<ProviderAccount> = listOf(
        ProviderAccount(
            id = "openai",
            label = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            apiType = ApiTypes.OPENAI,
            models = listOf("gpt-4o-mini")
        ),
        ProviderAccount(
            id = "anthropic",
            label = "Claude",
            baseUrl = "https://api.anthropic.com/v1",
            apiType = ApiTypes.ANTHROPIC,
            models = listOf("claude-sonnet-4-5"),
            supportsTemperature = false
        ),
        ProviderAccount(
            id = "gemini",
            label = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            apiType = ApiTypes.GEMINI,
            models = listOf("gemini-2.5-flash")
        ),
        ProviderAccount(
            id = "openrouter",
            label = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiType = ApiTypes.OPENAI,
            models = listOf("openai/gpt-4o-mini")
        ),
        ProviderAccount(
            id = "groq",
            label = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiType = ApiTypes.OPENAI,
            models = listOf("llama-3.3-70b-versatile")
        ),
        ProviderAccount(
            id = "ollama",
            label = "Ollama (local)",
            baseUrl = "http://127.0.0.1:11434/v1",
            apiType = ApiTypes.OPENAI,
            models = listOf("llama3.2"),
            requiresKey = false
        )
    )

    fun byId(id: String): ProviderAccount? = all.firstOrNull { it.id == id }
}
