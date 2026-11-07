package local.doja.companion.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLSocket

/**
 * Visor de la camara de casa por WebSocket con TLS fijado.
 *
 * El hilo lector nunca decodifica: solo guarda el ultimo JPEG recibido. El hilo
 * de pintado toma ese ultimo fotograma y descarta los anteriores, asi que una
 * red lenta o una pantalla lenta no acumulan retraso; se ve lo mas reciente.
 */
class CameraStreamClient(
    private val baseUrl: String,
    pinSha256: String,
    private val token: String?
) {
    private val sslContext = PinnedTls.context(pinSha256)
    private val main = Handler(Looper.getMainLooper())
    private val latest = AtomicReference<ByteArray>()

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var running = false

    fun start(onFrame: (Bitmap) -> Unit, onEnd: (String) -> Unit) {
        stop()
        running = true
        Thread({ readLoop(onEnd) }, "andromeda-camera-read").start()
        Thread({ renderLoop(onFrame) }, "andromeda-camera-render").start()
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (ignored: Exception) { }
        socket = null
        latest.set(null)
    }

    private fun readLoop(onEnd: (String) -> Unit) {
        var reason = "La vista terminó."
        try {
            val uri = URI(baseUrl.trimEnd('/'))
            val host = uri.host
            val port = if (uri.port > 0) uri.port else 11440
            val raw = Socket()
            raw.connect(InetSocketAddress(host, port), 5_000)
            raw.tcpNoDelay = true
            raw.receiveBufferSize = 128 * 1024
            val tls = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            tls.useClientMode = true
            tls.startHandshake()
            socket = tls

            val keyBytes = ByteArray(16)
            SecureRandom().nextBytes(keyBytes)
            val key = Base64.getEncoder().encodeToString(keyBytes)
            val head = "GET /v1/camera/stream HTTP/1.1\r\n"
                .plus("Host: $host:$port\r\n")
                .plus("Upgrade: websocket\r\n")
                .plus("Connection: Upgrade\r\n")
                .plus("Sec-WebSocket-Key: $key\r\n")
                .plus("Sec-WebSocket-Version: 13\r\n")
                .plus("X-Andromeda-Contract: 1\r\n")
                .plus("Authorization: Bearer $token\r\n\r\n")
            tls.outputStream.write(head.toByteArray(Charsets.UTF_8))
            tls.outputStream.flush()

            val input = DataInputStream(tls.inputStream)
            val status = readLine(input)
            if (!status.contains(" 101")) {
                val body = readBody(input)
                val json = try {
                    JSONObject(body)
                } catch (ignored: Exception) {
                    null
                }
                reason = json?.optString("message").orEmpty().ifEmpty {
                    if (status.contains(" 409")) "La cámara ya tiene un espectador." else "No se pudo abrir la cámara."
                }
                return
            }
            while (true) {
                if (readLine(input).isEmpty()) break
            }
            while (running) {
                val first = input.readUnsignedByte()
                val second = input.readUnsignedByte()
                var length = (second and 0x7F).toLong()
                if (length == 126L) {
                    length = ((input.readUnsignedByte() shl 8) or input.readUnsignedByte()).toLong()
                } else if (length == 127L) {
                    length = 0
                    repeat(8) { length = (length shl 8) or input.readUnsignedByte().toLong() }
                }
                if (length < 0 || length > 4_000_000L) break
                val payload = ByteArray(length.toInt())
                input.readFully(payload)
                val opcode = first and 0x0F
                if (opcode == 8) {
                    reason = "La vista terminó."
                    break
                }
                // Keep only the newest frame; decoding happens on the render thread.
                if (opcode == 2) latest.set(payload)
            }
        } catch (error: java.io.EOFException) {
            if (running) reason = "La casa cerró la vista."
        } catch (error: Exception) {
            if (running) {
                val detail = error.message
                reason = if (detail.isNullOrBlank()) "La casa cerró la vista." else "Sin conexión: $detail"
            }
        } finally {
            stop()
            main.post { onEnd(reason) }
        }
    }

    private fun renderLoop(onFrame: (Bitmap) -> Unit) {
        while (running) {
            val jpeg = latest.getAndSet(null)
            if (jpeg == null) {
                Thread.sleep(20)
                continue
            }
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
            if (!running) break
            main.post { if (running) onFrame(bitmap) }
            // Give the reader time to replace stale frames between paints.
            Thread.sleep(150)
        }
    }

    private fun readLine(input: DataInputStream): String {
        val line = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) throw java.io.EOFException()
            if (value == '\r'.code) continue
            if (value == '\n'.code) return line.toString()
            line.append(value.toChar())
            if (line.length > 8192) throw java.io.IOException("Cabecera demasiado larga")
        }
    }

    private fun readBody(input: DataInputStream): String {
        // The gateway answers error bodies with a fixed Content-Length.
        var contentLength = 0
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        if (contentLength <= 0 || contentLength > 65536) return ""
        val body = ByteArray(contentLength)
        input.readFully(body)
        return String(body, Charsets.UTF_8)
    }
}
