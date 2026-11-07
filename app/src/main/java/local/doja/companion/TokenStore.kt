package local.doja.companion

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Token del dispositivo cifrado con una clave del Keystore de Android. */
class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "andromeda_secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(token: String, deviceId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEVICE, deviceId)
            .apply()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)

    fun deviceId(): String? = prefs.getString(KEY_DEVICE, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_DEVICE = "device_id"
    }
}
