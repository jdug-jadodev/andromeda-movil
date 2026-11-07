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

/**
 * Cliente del gateway de casa.
 *
 * El certificado se fija por SHA-256: el hostname no decide la identidad.
 * El token viaja en Authorization y nunca se registra.
 */
class GatewayClient(
    private val baseUrl: String,
    pinSha256: String,
    private val token: String
) {
    private val sslContext = pinnedContext(pinSha256)

    fun health(): JSONObject = request("GET", "/v1/health", null)

    private fun request(method: String, path: String, body: ByteArray?): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 15_000
            sslSocketFactory = sslContext.socketFactory
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Andromeda-Contract", "1")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("HTTP $status: $text")
            return JSONObject(text)
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
