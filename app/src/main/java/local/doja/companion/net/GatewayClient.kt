package local.doja.companion.net

import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GatewayException(
    val status: Int,
    val code: String,
    message: String,
    val retryAfterMs: Long = 0
) : IOException(message)

/**
 * Cliente del gateway de casa.
 *
 * El certificado se fija por SHA-256: el hostname no decide la identidad.
 * El token viaja en Authorization y nunca se registra.
 */
class GatewayClient(
    private val baseUrl: String,
    pinSha256: String,
    private val token: String? = null
) {
    private val sslContext = pinnedContext(pinSha256)

    fun pair(code: String, deviceName: String): JSONObject {
        val body = JSONObject()
            .put("code", code)
            .put("device_name", deviceName)
            .put("contract", 1)
        return request("POST", "/v1/pair", body)
    }

    fun health(): JSONObject = request("GET", "/v1/health", null)

    /** Orden remota. El id es idempotente: reintentar no la ejecuta dos veces. */
    fun command(id: String, text: String, wantWav: Boolean = false): JSONObject {
        val body = JSONObject()
            .put("id", id)
            .put("text", text)
            .put("client_ts_ms", System.currentTimeMillis())
            .put("want_wav", wantWav)
        return request("POST", "/v1/command", body, readTimeoutMs = 30_000)
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        readTimeoutMs: Int = 15_000
    ): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = readTimeoutMs
            sslSocketFactory = sslContext.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Andromeda-Contract", "1")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (status !in 200..299) {
                throw GatewayException(
                    status,
                    json.optString("error", "http"),
                    json.optString("message", "HTTP $status"),
                    json.optLong("retry_after_ms", 0)
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun pinnedContext(pin: String): SSLContext {
        val expected = pin.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(expected.size == 32) { "Pin SHA-256 inválido" }

        val trust = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Solo servidor")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain.isNullOrEmpty()) throw CertificateException("Sin certificado")
                val actual = MessageDigest.getInstance("SHA-256").digest(chain[0].encoded)
                if (!MessageDigest.isEqual(actual, expected)) {
                    throw CertificateException("Certificado no reconocido")
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trust), SecureRandom())
        }
    }
}
