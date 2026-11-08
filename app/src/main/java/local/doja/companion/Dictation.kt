package local.doja.companion

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Dictado en el teléfono: transcribe lo que dices y NO envía nada.
 * El texto se entrega al terminar para que el usuario lo revise y decida.
 *
 * Sigue escuchando a través de las pausas hasta que se llama a [stop];
 * [cancel] descarta todo. Debe usarse desde el hilo principal.
 */
class Dictation(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onLevel(level: Float)
        fun onFinished(text: String)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private val committed = StringBuilder()
    private var partial = ""
    private var stopping = false
    private var onDevice = false
    private var startedAt = 0L

    val active: Boolean get() = listener != null

    fun available(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) || onDeviceAvailable()

    private fun onDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(listener: Listener): Boolean {
        cancel()
        if (!available()) return false
        this.listener = listener
        committed.clear()
        partial = ""
        stopping = false
        onDevice = onDeviceAvailable()
        startedAt = System.currentTimeMillis()
        main.postDelayed(maxTimer, MAX_MS)
        return listen()
    }

    /** Deja de escuchar y entrega el texto reconocido. */
    fun stop() {
        if (listener == null || stopping) return
        stopping = true
        try {
            recognizer?.stopListening()
        } catch (ignored: Exception) {
            finish()
            return
        }
        // Algunos reconocedores no responden tras stopListening: no esperar para siempre.
        main.postDelayed(stopTimeout, 2_500)
    }

    /** Descarta el dictado sin entregar nada. */
    fun cancel() {
        listener = null
        teardown()
    }

    private val maxTimer = Runnable { stop() }
    private val stopTimeout = Runnable { finish() }

    private fun text(): String =
        listOf(committed.toString(), partial).filter { it.isNotBlank() }.joinToString(" ").trim()

    private fun restart() {
        main.post { if (listener != null && !stopping) listen() }
    }

    private fun listen(): Boolean {
        recognizer?.destroy()
        val created = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (error: Exception) {
            null
        }
        if (created == null) {
            fail("No se pudo abrir el reconocimiento de voz.")
            return false
        }
        recognizer = created
        created.setRecognitionListener(callbacks)
        created.startListening(intent())
        return true
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        val locale = Locale.getDefault()
        val tag = if (locale.language == "es") locale.toLanguageTag() else "es-ES"
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
    }

    private fun finish() {
        val current = listener ?: return
        val result = text()
        listener = null
        teardown()
        current.onFinished(result)
    }

    private fun fail(message: String) {
        val current = listener ?: return
        listener = null
        teardown()
        current.onError(message)
    }

    private fun teardown() {
        main.removeCallbacks(maxTimer)
        main.removeCallbacks(stopTimeout)
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (ignored: Exception) { }
        recognizer = null
    }

    private fun first(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            listener?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val heard = first(partialResults)
            if (heard.isNotEmpty()) {
                partial = heard
                listener?.onPartial(text())
            }
        }

        override fun onResults(results: Bundle?) {
            val heard = first(results).ifEmpty { partial }
            if (heard.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(heard.trim())
            }
            partial = ""
            listener?.onPartial(text())
            if (stopping) finish() else restart() // pausa natural: seguir escuchando
        }

        override fun onError(error: Int) {
            if (listener == null) return
            val languageMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            when {
                onDevice && languageMissing && SpeechRecognizer.isRecognitionAvailable(context) -> {
                    // Sin paquete de español en el teléfono: usar el reconocedor normal.
                    onDevice = false
                    restart()
                }
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (partial.isNotBlank()) {
                        if (committed.isNotEmpty()) committed.append(' ')
                        committed.append(partial.trim())
                        partial = ""
                    }
                    if (stopping) finish() else restart()
                }
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Sin permiso de micrófono no puedo escuchar.")
                stopping || text().isNotEmpty() -> finish()
                error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    fail("El reconocimiento de voz necesita conexión en este teléfono.")
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    fail("El micrófono está ocupado; intenta de nuevo.")
                else -> fail("No se pudo reconocer la voz (código $error).")
            }
        }
    }

    companion object {
        const val MAX_MS = 120_000L
    }
}
