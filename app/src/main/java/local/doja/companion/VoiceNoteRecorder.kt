package local.doja.companion

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Graba una nota de voz en AAC dentro de un contenedor MP4, que es el formato
 * que el contrato acepta (`audio/mp4`). Nada sale del telefono hasta que se
 * sube explícitamente.
 */
class VoiceNoteRecorder(private val context: Context) {
    data class Note(val file: File, val durationMs: Long, val type: String)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    fun start(): Boolean {
        cancel()
        val target = File(context.cacheDir, "voice-note-${System.currentTimeMillis()}.m4a")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioSamplingRate(16000)
            created.setAudioEncodingBitRate(32000)
            created.setAudioChannels(1)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            file = target
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (error: Exception) {
            try {
                created.release()
            } catch (ignored: RuntimeException) { }
            target.delete()
            recorder = null
            file = null
            false
        }
    }

    fun elapsedMs(): Long = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAt

    /** Returns null when nothing valid was recorded. */
    fun stop(): Note? {
        val current = recorder ?: return null
        val target = file
        recorder = null
        file = null
        val duration = SystemClock.elapsedRealtime() - startedAt
        return try {
            current.stop()
            current.release()
            if (target == null || !target.isFile || target.length() == 0L || duration < 700) {
                target?.delete()
                null
            } else {
                Note(target, duration, "audio/mp4")
            }
        } catch (error: RuntimeException) {
            // Too short or the encoder never produced a valid file.
            try {
                current.release()
            } catch (ignored: RuntimeException) { }
            target?.delete()
            null
        }
    }

    fun cancel() {
        val current = recorder ?: return
        recorder = null
        try {
            current.stop()
        } catch (ignored: RuntimeException) { }
        try {
            current.release()
        } catch (ignored: RuntimeException) { }
        file?.delete()
        file = null
    }

    companion object {
        const val MAX_MS = 60_000L
    }
}
