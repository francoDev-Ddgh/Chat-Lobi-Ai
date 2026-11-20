package github.franco.chat.ai.data

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class Chat(
    val id: String,
    val title: String = "Nuevo chat",
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = 0L
)

@Serializable
data class ProviderConfig(
    val presetId: String = "openai",
    val baseUrl: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val temperature: Double? = null
)

@Serializable
data class AppSettings(
    val config: ProviderConfig = ProviderConfig()
)
