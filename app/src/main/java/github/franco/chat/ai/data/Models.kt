package github.franco.chat.ai.data

import github.franco.chat.ai.net.Providers
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
    val id: String,
    val label: String,
    val baseUrl: String,
    val apiType: String = "openai",
    val models: List<String> = emptyList(),
    val enabled: Boolean = false,
    val custom: Boolean = false,
    val requiresKey: Boolean = true,
    val supportsTemperature: Boolean = true
)

@Serializable
data class AppSettings(
    val accounts: List<ProviderAccount> = Providers.all,
    val lastProviderId: String = "",
    val lastModel: String = ""
)
