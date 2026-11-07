package local.doja.companion

import android.content.Context
import org.json.JSONObject

/**
 * Constantes y límites del contrato home-api-v1.
 * La copia canónica vive en contract/ y se vendoriza desde andromeda-contrato.
 */
object Contract {
    const val VERSION = 1

    const val GATEWAY_POLICY = "home_gateway_v1"
    const val REMOTE_VOICE_POLICY = "remote_voice_v1"
    const val VOICE_MESSAGE_POLICY = "voice_message_v1"
    const val CAMERA_STREAM_POLICY = "camera_stream_v1"

    data class Limits(
        val commandChars: Int,
        val messageBytes: Int,
        val messageMs: Int,
        val cameraMinutes: Int,
        val cameraFps: Int,
        val requestsPerMinute: Int
    )

    fun policies(context: Context): JSONObject = asset(context, "policies.json")

    fun limits(context: Context): Limits {
        val json = asset(context, "limits.json")
        return Limits(
            commandChars = json.getInt("command_chars"),
            messageBytes = json.getInt("message_bytes"),
            messageMs = json.getInt("message_ms"),
            cameraMinutes = json.getInt("camera_minutes"),
            cameraFps = json.getInt("camera_fps"),
            requestsPerMinute = json.getInt("requests_per_minute")
        )
    }

    /** Falla pronto si la copia vendorizada no es la que espera la app. */
    fun check(context: Context) {
        val policies = policies(context)
        require(policies.getInt("contract") == VERSION) { "Contrato incompatible" }
        require(policies.getString("gateway") == GATEWAY_POLICY) { "Política de gateway distinta" }
        require(policies.getString("remote_voice") == REMOTE_VOICE_POLICY) { "Política de voz distinta" }
        require(policies.getString("voice_message") == VOICE_MESSAGE_POLICY) { "Política de mensajes distinta" }
        require(policies.getString("camera_stream") == CAMERA_STREAM_POLICY) { "Política de cámara distinta" }
    }

    private fun asset(context: Context, name: String): JSONObject =
        context.assets.open("contract/$name").bufferedReader().use { JSONObject(it.readText()) }
}
