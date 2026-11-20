package github.franco.chat.ai.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

private val Context.dataStore by preferencesDataStore(name = "chat_lobi")

object KeyVault {
    private const val ALIAS = "chat_lobi_keys"
    private const val IV_LEN = 12

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(blob: String): String? = runCatching {
        val bytes = Base64.decode(blob, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_LEN)
        val data = bytes.copyOfRange(IV_LEN, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.getOrNull()
}

class ChatRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val chatsKey = stringPreferencesKey("chats")
    private val settingsKey = stringPreferencesKey("settings")

    private fun keyPref(id: String) = stringPreferencesKey("api_key_$id")

    val chatsFlow: Flow<List<Chat>> = context.dataStore.data.map { prefs ->
        prefs[chatsKey]?.let { raw ->
            runCatching { json.decodeFromString<List<Chat>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString<AppSettings>(raw) }.getOrNull()
        } ?: AppSettings()
    }

    suspend fun saveChats(chats: List<Chat>) {
        context.dataStore.edit { it[chatsKey] = json.encodeToString(chats) }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[settingsKey] = json.encodeToString(settings) }
    }

    suspend fun upsertChat(chat: Chat) {
        val current = chatsFlow.first()
        val next = current.filterNot { it.id == chat.id } + chat
        saveChats(next)
    }

    suspend fun deleteChat(id: String) {
        saveChats(chatsFlow.first().filterNot { it.id == id })
    }

    suspend fun saveApiKey(presetId: String, raw: String) {
        context.dataStore.edit { prefs ->
            if (raw.isBlank()) prefs.remove(keyPref(presetId))
            else prefs[keyPref(presetId)] = KeyVault.encrypt(raw.trim())
        }
    }

    suspend fun apiKey(presetId: String): String? {
        val stored = context.dataStore.data.first()[keyPref(presetId)] ?: return null
        return KeyVault.decrypt(stored)
    }
}
