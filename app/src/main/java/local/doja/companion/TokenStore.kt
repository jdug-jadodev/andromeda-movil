package local.doja.companion

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Sesión por dispositivo cifrada con una clave del Keystore de Android. */
class TokenStore(context: Context) {
    data class Session(
        val baseUrl: String,
        val pin: String,
        val token: String,
        val deviceId: String
    )

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "andromeda_secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(baseUrl: String, pin: String, token: String, deviceId: String) {
        prefs.edit()
            .putString(KEY_BASE, baseUrl)
            .putString(KEY_PIN, pin)
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEVICE, deviceId)
            .apply()
    }

    fun session(): Session? {
        val base = prefs.getString(KEY_BASE, null) ?: return null
        val pin = prefs.getString(KEY_PIN, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val device = prefs.getString(KEY_DEVICE, null) ?: return null
        return Session(base, pin, token, device)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BASE = "base_url"
        const val KEY_PIN = "cert_pin"
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE = "device_id"
    }
}
