package local.doja.companion

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.doja.companion.net.CameraStreamClient
import local.doja.companion.net.GatewayClient
import local.doja.companion.net.GatewayException
import local.doja.companion.ui.AndromedaTheme
import local.doja.companion.ui.CameraScreen
import local.doja.companion.ui.ChatEntry
import local.doja.companion.ui.ChatScreen
import local.doja.companion.ui.HomeShell
import local.doja.companion.ui.LinkScreen
import local.doja.companion.ui.PairingFields
import local.doja.companion.ui.PairingScreen
import local.doja.companion.ui.Palette
import local.doja.companion.ui.SpaceBackground
import local.doja.companion.ui.TAB_CAMERA
import local.doja.companion.ui.TAB_CHAT
import local.doja.companion.ui.TAB_LINK
import local.doja.companion.ui.TAB_VOICE
import local.doja.companion.ui.VoiceNoteScreen
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            AndromedaTheme {
                AndromedaApp()
            }
        }
    }
}

private const val CHAT_LIMIT = 100

private val ChatSaver = listSaver<SnapshotStateList<ChatEntry>, String>(
    save = { list -> list.map(ChatEntry::encode) },
    restore = { saved -> mutableStateListOf<ChatEntry>().apply { saved.forEach { add(ChatEntry.decode(it)) } } }
)

@Composable
private fun AndromedaApp() {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(TAB_CHAT) }
    var relinking by rememberSaveable { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val chat = rememberSaveable(saver = ChatSaver) { mutableStateListOf() }
    var awaitingReply by remember { mutableStateOf(false) }

    fun addChat(kind: Int, text: String) {
        chat.add(ChatEntry(kind, System.currentTimeMillis(), text))
        while (chat.size > CHAT_LIMIT) chat.removeAt(0)
    }

    var host by rememberSaveable { mutableStateOf("andromeda") }
    var port by rememberSaveable { mutableStateOf("11440") }
    var deviceName by rememberSaveable { mutableStateOf("A56") }
    var pin by rememberSaveable { mutableStateOf("") }
    var certPin by rememberSaveable {
        mutableStateOf("c390aca21cb3920030f8f5879a44060a95baba7d9d82fd6489612d44192e7091")
    }
    var status by rememberSaveable { mutableStateOf("Casa: sin emparejar") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var order by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    var sending by rememberSaveable { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var noteStatus by rememberSaveable { mutableStateOf("") }
    var noteSending by remember { mutableStateOf(false) }
    val recorder = remember { VoiceNoteRecorder(context) }
    var session by remember { mutableStateOf<TokenStore.Session?>(null) }

    fun baseUrl(): String = "https://${host.trim()}:${port.trim()}"

    fun beginNote() {
        if (recorder.start()) {
            recording = true
            noteStatus = "Grabando… máximo 60 segundos."
        } else {
            noteStatus = "No se pudo abrir el micrófono."
        }
    }

    fun sendNote(note: VoiceNoteRecorder.Note, current: TokenStore.Session) {
        noteStatus = "Enviando mensaje…"
        noteSending = true
        scope.launch {
            try {
                val id = "msg-" + java.util.UUID.randomUUID().toString().replace("-", "")
                val answer = withContext(Dispatchers.IO) {
                    val bytes = note.file.readBytes()
                    try {
                        GatewayClient(current.baseUrl, current.pin, current.token)
                            .sendMessage(id, bytes, note.type, note.durationMs)
                    } finally {
                        note.file.delete()
                    }
                }
                noteStatus = if (answer.optString("status") == "queued") {
                    "Mensaje encolado. Andrómeda lo dirá en el altavoz de casa."
                } else {
                    "Mensaje recibido por la casa."
                }
            } catch (error: GatewayException) {
                noteStatus = when (error.status) {
                    429 -> if (error.retryAfterMs > 0)
                        "Espera ${(error.retryAfterMs + 999) / 1000} s antes de otro mensaje."
                    else "Demasiados mensajes; espera un momento."
                    413 -> "El mensaje es demasiado grande."
                    else -> error.message ?: "No se pudo enviar el mensaje."
                }
            } catch (error: Exception) {
                noteStatus = "Sin conexión: ${error.message}"
            } finally {
                noteSending = false
            }
        }
    }

    fun stopNote(current: TokenStore.Session?) {
        val note = recorder.stop()
        recording = false
        if (note == null) {
            noteStatus = "La grabación fue demasiado corta."
            return
        }
        if (current == null) {
            note.file.delete()
            noteStatus = "Empareja el teléfono primero."
            return
        }
        sendNote(note, current)
    }

    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginNote() else noteStatus = "Sin permiso de micrófono no puedo grabar."
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(500)
            if (recorder.elapsedMs() >= VoiceNoteRecorder.MAX_MS) {
                stopNote(session)
                break
            }
        }
    }

    var voiceRecording by remember { mutableStateOf(false) }
    var voiceStatus by rememberSaveable { mutableStateOf("") }
    var replyPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var replyPlaying by remember { mutableStateOf(false) }

    fun playReply(audio: ByteArray) {
        try {
            val file = java.io.File(context.cacheDir, "andromeda-reply.wav")
            file.writeBytes(audio)
            replyPlayer?.release()
            val player = MediaPlayer()
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                it.release()
                file.delete()
                replyPlaying = false
            }
            player.prepare()
            player.start()
            replyPlayer = player
            replyPlaying = true
        } catch (ignored: Exception) {
            replyPlaying = false
        }
    }

    val commandRecorder = remember { VoiceCommandRecorder(context) }

    fun beginVoice() {
        if (recording || commandRecorder.start()) {
            if (recording) {
                voiceStatus = "Termina primero el mensaje de voz."
                return
            }
            voiceRecording = true
            voiceStatus = "Escuchando… máximo 15 segundos."
        } else {
            voiceStatus = "No se pudo abrir el micrófono."
        }
    }

    suspend fun deliverAnswer(client: GatewayClient, id: String, answer: JSONObject) {
        reply = answer.optString("say").ifEmpty { "La casa no devolvió respuesta." }
        order = ""
        awaitingReply = false
        addChat(ChatEntry.HOUSE, reply)
        val sayWav = answer.optString("say_wav")
        if (sayWav.isNotEmpty()) {
            try {
                val audio = withContext(Dispatchers.IO) { client.speech(sayWav) }
                playReply(audio)
            } catch (ignored: Exception) { }
        }
    }

    fun sendVoice(current: TokenStore.Session, note: VoiceCommandRecorder.Note) {
        sending = true
        voiceStatus = "Entendiendo lo que dijiste…"
        scope.launch {
            val id = "cmd-" + java.util.UUID.randomUUID().toString().replace("-", "")
            val client = GatewayClient(current.baseUrl, current.pin, current.token)
            val wav = withContext(Dispatchers.IO) {
                note.file.readBytes().also { note.file.delete() }
            }
            var attempt = 0
            try {
                while (true) {
                    try {
                        var answer = withContext(Dispatchers.IO) {
                            client.commandAudio(id, wav, note.durationMs)
                        }
                        if (answer.optString("say_wav").isEmpty()) {
                            delay(1_200)
                            answer = withContext(Dispatchers.IO) {
                                client.commandAudio(id, wav, note.durationMs)
                            }
                        }
                        deliverAnswer(client, id, answer)
                        val heard = answer.optString("heard")
                        voiceStatus = if (heard.isNotEmpty()) "Vosk entendió: \"$heard\"" else ""
                        break
                    } catch (error: GatewayException) {
                        val retryable = error.status == 409 || error.status == 503 || error.status == 429 || error.status == 504
                        if (retryable && attempt < 3) {
                            attempt++
                            voiceStatus = "La casa está ocupada; reintentando…"
                            delay(if (error.retryAfterMs > 0) error.retryAfterMs else 3_000L)
                            continue
                        }
                        voiceStatus = when (error.status) {
                            409 -> "La casa está ocupada; espera un momento."
                            503 -> "Andrómeda no está escuchando ahora."
                            else -> error.message ?: "No se pudo enviar la orden."
                        }
                        break
                    }
                }
            } catch (error: Exception) {
                voiceStatus = "Sin conexión: ${error.message}"
            } finally {
                sending = false
            }
        }
    }

    fun stopVoice(current: TokenStore.Session?) {
        val note = commandRecorder.stop()
        voiceRecording = false
        if (note == null) {
            voiceStatus = "No se escuchó nada; intenta de nuevo."
            return
        }
        if (current == null) {
            note.file.delete()
            voiceStatus = "Empareja el teléfono primero."
            return
        }
        sendVoice(current, note)
    }

    val microphoneVoice = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginVoice() else voiceStatus = "Sin permiso de micrófono no puedo escuchar."
    }

    // Órdenes por voz: suspendidas en la interfaz. Se conserva el flujo para reactivarlo.
    @Suppress("UNUSED_VARIABLE")
    val onVoice = {
        if (!voiceRecording) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                beginVoice()
            } else {
                microphoneVoice.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            stopVoice(session)
        }
    }

    LaunchedEffect(voiceRecording) {
        while (voiceRecording) {
            delay(500)
            if (commandRecorder.elapsedMs() >= VoiceCommandRecorder.MAX_MS) {
                stopVoice(session)
                break
            }
        }
    }

    var watching by remember { mutableStateOf(false) }
    var cameraFrame by remember { mutableStateOf<Bitmap?>(null) }
    var cameraNote by rememberSaveable { mutableStateOf("") }
    var cameraClient by remember { mutableStateOf<CameraStreamClient?>(null) }

    fun stopCamera() {
        val current = cameraClient
        cameraClient = null
        current?.stop()
        watching = false
        cameraFrame = null
    }

    fun startCamera() {
        val current = session ?: return
        if (cameraClient != null) return
        val client = CameraStreamClient(current.baseUrl, current.pin, current.token)
        cameraClient = client
        watching = true
        cameraNote = "Conectando…"
        client.start(
            onFrame = { frame ->
                if (cameraClient === client) {
                    cameraFrame = frame
                    if (cameraNote == "Conectando…") cameraNote = "En vivo · sin grabación. Hasta 10 minutos."
                }
            },
            onEnd = { message ->
                if (cameraClient === client) {
                    cameraClient = null
                    watching = false
                    cameraFrame = null
                    cameraNote = message
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraClient?.stop()
            cameraClient = null
            replyPlayer?.release()
            replyPlayer = null
            commandRecorder.cancel()
            recorder.cancel()
        }
    }

    fun askHealth(current: TokenStore.Session) {
        busy = true
        status = "Consultando la casa…"
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    GatewayClient(current.baseUrl, current.pin, current.token).health()
                }
                status = "Casa en línea · residente ${json.optString("resident")} · cámara ${json.optString("camera")}"
            } catch (error: Exception) {
                status = "Sin conexión: ${error.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = store.session()
        session = saved
        loaded = true
        if (saved != null) {
            askHealth(saved)
        }
    }

    fun openSettings(current: TokenStore.Session?) {
        if (current != null) {
            val tail = current.baseUrl.removePrefix("https://")
            host = tail.substringBefore(":")
            port = tail.substringAfter(":", "11440")
            certPin = current.pin
            pin = ""
        }
        relinking = true
        tab = TAB_LINK
    }

    fun sendOrder() {
        val current = session
        val text = order.trim()
        if (current == null || text.isEmpty() || sending) return
        sending = true
        reply = "Enviando a la casa…"
        awaitingReply = true
        addChat(ChatEntry.USER, text)
        scope.launch {
            val id = "cmd-" + java.util.UUID.randomUUID().toString().replace("-", "")
            val client = GatewayClient(current.baseUrl, current.pin, current.token)
            var attempt = 0
            try {
                while (true) {
                    try {
                        var answer = withContext(Dispatchers.IO) { client.command(id, text, wantWav = true) }
                        if (answer.optString("say_wav").isEmpty()) {
                            // Cold start: the WAV is copied a moment later.
                            delay(1_200)
                            answer = withContext(Dispatchers.IO) { client.command(id, text, wantWav = true) }
                        }
                        deliverAnswer(client, id, answer)
                        break
                    } catch (error: GatewayException) {
                        val retryable = error.status == 409 || error.status == 503 || error.status == 429 || error.status == 504
                        if (retryable && attempt < 3) {
                            attempt++
                            reply = "La casa está ocupada; reintentando…"
                            delay(if (error.retryAfterMs > 0) error.retryAfterMs else 3_000L)
                            continue
                        }
                        reply = when (error.status) {
                            409 -> "La casa está ocupada; espera un momento."
                            503 -> "Andrómeda no está escuchando ahora."
                            else -> error.message ?: "No se pudo enviar la orden."
                        }
                        break
                    }
                }
            } catch (error: Exception) {
                reply = "Sin conexión: ${error.message}"
            } finally {
                sending = false
                if (awaitingReply) {
                    awaitingReply = false
                    addChat(ChatEntry.ERROR, reply)
                }
            }
        }
    }

    fun pair() {
        if (pin.length != 6) {
            status = "El PIN debe tener 6 dígitos"
            return
        }
        busy = true
        status = "Emparejando…"
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    GatewayClient(baseUrl(), certPin.trim(), null)
                        .pair(pin.trim(), deviceName.trim())
                }
                val token = response.getString("token")
                val deviceId = response.getString("device_id")
                store.saveSession(baseUrl(), certPin.trim(), token, deviceId)
                val saved = store.session()
                session = saved
                relinking = false
                tab = TAB_CHAT
                status = "Emparejado: $deviceId"
                saved?.let { askHealth(it) }
            } catch (error: Exception) {
                status = "Error: ${error.message}"
            } finally {
                busy = false
            }
        }
    }

    fun forget() {
        stopCamera()
        store.clear()
        session = null
        relinking = false
        tab = TAB_CHAT
        status = "Casa: sin emparejar"
    }

    val fields = PairingFields(
        host = host,
        onHost = { host = it },
        port = port,
        onPort = { port = it.filter(Char::isDigit).take(5) },
        deviceName = deviceName,
        onDeviceName = { deviceName = it.take(40) },
        pin = pin,
        onPin = { pin = it.filter(Char::isDigit).take(6) },
        certPin = certPin,
        onCertPin = { certPin = it.trim() }
    )

    BackHandler(enabled = session != null && (relinking || tab != TAB_CHAT)) {
        if (relinking && tab == TAB_LINK) relinking = false else tab = TAB_CHAT
    }

    Box(Modifier.fillMaxSize()) {
        SpaceBackground()
        val current = session
        when {
            !loaded -> Unit
            current == null -> PairingScreen(
                status = status,
                busy = busy,
                fields = fields,
                onPair = { pair() }
            )
            else -> HomeShell(
                tab = tab,
                onTab = { tab = it },
                status = status,
                busy = busy,
                onHealth = { askHealth(current) },
                recording = recording,
                watching = watching
            ) { page, modifier ->
                when (page) {
                    TAB_CHAT -> ChatScreen(
                        chat = chat,
                        order = order,
                        onOrder = { order = it.take(500) },
                        sending = sending,
                        awaitingReply = awaitingReply,
                        pending = reply,
                        voiceStatus = voiceStatus,
                        replyPlaying = replyPlaying,
                        onSend = { sendOrder() },
                        modifier = modifier
                    )
                    TAB_VOICE -> VoiceNoteScreen(
                        recording = recording,
                        noteSending = noteSending,
                        noteStatus = noteStatus,
                        elapsedMs = { recorder.elapsedMs() },
                        onRecord = {
                            if (voiceRecording) {
                                noteStatus = "Termina primero la orden de voz."
                            } else if (!recording) {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    beginNote()
                                } else {
                                    microphone.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                stopNote(session)
                            }
                        },
                        modifier = modifier
                    )
                    TAB_CAMERA -> CameraScreen(
                        watching = watching,
                        frame = cameraFrame,
                        note = cameraNote,
                        onStart = { startCamera() },
                        onStop = { stopCamera() },
                        modifier = modifier
                    )
                    else -> LinkScreen(
                        session = current,
                        status = status,
                        busy = busy,
                        onHealth = { askHealth(current) },
                        relinking = relinking,
                        onToggleRelink = { if (relinking) relinking = false else openSettings(current) },
                        fields = fields,
                        onPair = { pair() },
                        onForget = { confirmForget = true },
                        modifier = modifier
                    )
                }
            }
        }

        if (confirmForget) {
            AlertDialog(
                onDismissRequest = { confirmForget = false },
                containerColor = Palette.PanelHigh,
                titleContentColor = Palette.Text,
                textContentColor = Palette.Muted,
                title = { Text("¿Olvidar este teléfono?") },
                text = { Text("Se borrará el emparejamiento guardado. Para volver a usar la app tendrás que emparejar de nuevo con un PIN.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmForget = false
                        forget()
                    }) { Text("Olvidar", color = Palette.Offline) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmForget = false }) { Text("Cancelar", color = Palette.Lavender) }
                }
            )
        }
    }
}
