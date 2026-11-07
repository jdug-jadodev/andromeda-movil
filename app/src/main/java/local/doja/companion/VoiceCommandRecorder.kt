package local.doja.companion

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Graba una orden de voz en WAV PCM16 mono a 16 kHz: exactamente lo que el
 * Vosk del Moto entiende, sin conversiones ni pérdida.
 */
class VoiceCommandRecorder(private val context: Context) {
    data class Note(val file: File, val durationMs: Long, val type: String = "audio/wav")

    private var record: AudioRecord? = null
    private var file: File? = null
    private var thread: Thread? = null
    private var output: FileOutputStream? = null
    @Volatile private var recording = false
    private var startedAt = 0L
    private var bytes = 0

    fun start(): Boolean {
        cancel()
        val minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) return false
        val created = AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }
        val target = File(context.cacheDir, "voice-command-${System.currentTimeMillis()}.wav")
        return try {
            val stream = FileOutputStream(target)
            stream.write(ByteArray(HEADER))
            record = created
            file = target
            output = stream
            bytes = 0
            startedAt = SystemClock.elapsedRealtime()
            recording = true
            created.startRecording()
            thread = Thread({
                val buffer = ByteArray(640)
                try {
                    while (recording) {
                        val count = created.read(buffer, 0, buffer.size)
                        if (count <= 0) continue
                        stream.write(buffer, 0, count)
                        bytes += count
                    }
                } catch (ignored: Exception) {
                } finally {
                    try {
                        stream.close()
                    } catch (ignored: Exception) { }
                }
            }, "voice-command-recorder")
            thread?.start()
            true
        } catch (error: Exception) {
            created.release()
            target.delete()
            record = null
            file = null
            output = null
            false
        }
    }

    fun elapsedMs(): Long = if (record == null) 0 else SystemClock.elapsedRealtime() - startedAt

    fun stop(): Note? {
        val current = record ?: return null
        recording = false
        try {
            current.stop()
        } catch (ignored: RuntimeException) { }
        try {
            thread?.join(800)
        } catch (ignored: InterruptedException) { }
        try {
            current.release()
        } catch (ignored: RuntimeException) { }
        record = null
        thread = null
        output = null
        val target = file ?: return null
        file = null
        val duration = SystemClock.elapsedRealtime() - startedAt
        if (bytes < 320 || duration < 500) {
            target.delete()
            return null
        }
        return try {
            RandomAccessFile(target, "rw").use { stream ->
                stream.seek(0)
                stream.write(header(bytes))
            }
            Note(target, duration)
        } catch (error: Exception) {
            target.delete()
            null
        }
    }

    fun cancel() {
        val current = record ?: return
        recording = false
        try {
            current.stop()
        } catch (ignored: RuntimeException) { }
        try {
            current.release()
        } catch (ignored: RuntimeException) { }
        record = null
        thread = null
        file?.delete()
        file = null
        output = null
        bytes = 0
    }

    private fun header(dataBytes: Int): ByteArray {
        val header = ByteArray(HEADER)
        fun put(index: Int, value: Int) {
            header[index] = (value and 0xff).toByte()
            header[index + 1] = ((value shr 8) and 0xff).toByte()
            header[index + 2] = ((value shr 16) and 0xff).toByte()
            header[index + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun short(index: Int, value: Int) {
            header[index] = (value and 0xff).toByte()
            header[index + 1] = ((value shr 8) and 0xff).toByte()
        }
        "RIFF".toByteArray().copyInto(header, 0)
        put(4, 36 + dataBytes)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        put(16, 16)
        short(20, 1)
        short(22, 1)
        put(24, RATE)
        put(28, RATE * 2)
        short(32, 2)
        short(34, 16)
        "data".toByteArray().copyInto(header, 36)
        put(40, dataBytes)
        return header
    }

    companion object {
        const val MAX_MS = 15_000L
        private const val RATE = 16000
        private const val HEADER = 44
    }
}
