package github.franco.chat.ai.net

data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val requiresKey: Boolean = true,
    val omitTemperature: Boolean = false
)

object Providers {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "sk-..."),
        ProviderPreset("anthropic", "Claude", "https://api.anthropic.com/v1", "claude-sonnet-4-5", "sk-ant-...", omitTemperature = true),
        ProviderPreset("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "AIza..."),
        ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "sk-or-..."),
        ProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "gsk_..."),
        ProviderPreset("ollama", "Ollama (local)", "http://127.0.0.1:11434/v1", "llama3.2", "", requiresKey = false)
    )

    fun byId(id: String): ProviderPreset = all.firstOrNull { it.id == id } ?: all.first()
}
