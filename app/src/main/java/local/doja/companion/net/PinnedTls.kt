package local.doja.companion.net

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS fijado por SHA-256 del certificado. El hostname no decide la identidad:
 * solo importa que la casa presente exactamente el certificado emparejado.
 */
object PinnedTls {
    fun context(pin: String): SSLContext {
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
