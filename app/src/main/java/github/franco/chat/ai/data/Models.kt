package github.franco.chat.ai.data

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatConfig(
    val providerId: String = "",
    val model: String = "",
    val temperature: Double? = null,
    val systemPrompt: String = ""
)

@Serializable
data class Chat(
    val id: String,
    val title: String = "Nuevo chat",
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = 0L,
    val config: ChatConfig = ChatConfig()
)

@Serializable
data class ProviderAccount(
    val presetId: String,
    val baseUrl: String = "",
    val models: List<String> = emptyList(),
    val enabled: Boolean = false
)

@Serializable
data class AppSettings(
    val accounts: List<ProviderAccount> = emptyList(),
    val lastProviderId: String = "",
    val lastModel: String = ""
)
