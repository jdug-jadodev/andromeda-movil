package local.doja.companion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.doja.companion.net.GatewayClient
import local.doja.companion.net.GatewayException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndromedaTheme {
                AndromedaApp()
            }
        }
    }
}

private const val SCREEN_PAIRING = "pairing"
private const val SCREEN_HOME = "home"

private val CardColor = Color(0xFF121A2A)
private val Muted = Color(0xFF97A6C3)
private val Accent = Color(0xFFB39DFF)
private val Online = Color(0xFF4CAF50)
private val Offline = Color(0xFFFF7043)

@Composable
private fun AndromedaApp() {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(SCREEN_PAIRING) }
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
        if (saved != null) {
            screen = SCREEN_HOME
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
        screen = SCREEN_PAIRING
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Andrómeda",
                fontSize = 31.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (status.startsWith("Casa en línea")) Online else Offline)
                )
                Spacer(Modifier.width(8.dp))
                Text(status, fontSize = 14.sp, color = Color(0xFFDCE4F5))
            }

            if (screen == SCREEN_HOME && session != null) {
                HomeContent(
                    session = session!!,
                    busy = busy,
                    order = order,
                    onOrder = { order = it.take(500) },
                    reply = reply,
                    sending = sending,
                    recording = recording,
                    noteStatus = noteStatus,
                    onRecord = {
                        val current = session
                        if (!recording) {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                beginNote()
                            } else {
                                microphone.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            stopNote(current)
                        }
                    },
                    onSend = {
                        val current = session
                        val text = order.trim()
                        if (current != null && text.isNotEmpty() && !sending) {                            sending = true
                            reply = "Enviando a la casa…"
                            scope.launch {
                                val id = "cmd-" + java.util.UUID.randomUUID().toString().replace("-", "")
                                val client = GatewayClient(current.baseUrl, current.pin, current.token)
                                var attempt = 0
                                try {
                                    while (true) {
                                        try {
                                            val answer = withContext(Dispatchers.IO) { client.command(id, text) }
                                            reply = answer.optString("say").ifEmpty { "La casa no devolvió respuesta." }
                                            order = ""
                                            break
                                        } catch (error: GatewayException) {
                                            val retryable = error.status == 409 || error.status == 503 || error.status == 429
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
                                }
                            }
                        }
                    },
                    onHealth = { session?.let { askHealth(it) } },
                    onSettings = { openSettings(session) },
                    onForget = {
                        store.clear()
                        session = null
                        screen = SCREEN_PAIRING
                        status = "Casa: sin emparejar"
                    }
                )
            } else {
                PairingContent(
                    host = host,
                    onHost = { host = it },
                    port = port,
                    onPort = { port = it.filter(Char::isDigit).take(5) },
                    deviceName = deviceName,
                    onDeviceName = { deviceName = it.take(40) },
                    pin = pin,
                    onPin = { pin = it.filter(Char::isDigit).take(6) },
                    certPin = certPin,
                    onCertPin = { certPin = it.trim() },
                    busy = busy,
                    hasSession = session != null,
                    onPair = {
                        if (pin.length != 6) {
                            status = "El PIN debe tener 6 dígitos"
                        } else {
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
                                    screen = SCREEN_HOME
                                    status = "Emparejado: $deviceId"
                                    saved?.let { askHealth(it) }
                                } catch (error: Exception) {
                                    status = "Error: ${error.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onHealth = { session?.let { askHealth(it) } },
                    onBack = { screen = SCREEN_HOME },
                    onForget = {
                        store.clear()
                        session = null
                        status = "Casa: sin emparejar"
                    }
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Contrato v${Contract.VERSION} · Tailscale · TLS fijado",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}

@Composable
private fun PairingContent(
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    deviceName: String,
    onDeviceName: (String) -> Unit,
    pin: String,
    onPin: (String) -> Unit,
    certPin: String,
    onCertPin: (String) -> Unit,
    busy: Boolean,
    hasSession: Boolean,
    onPair: () -> Unit,
    onHealth: () -> Unit,
    onBack: () -> Unit,
    onForget: () -> Unit
) {
    SectionLabel("CONEXIÓN")
    OutlinedTextField(
        value = host,
        onValueChange = onHost,
        label = { Text("Host o IP") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = port,
        onValueChange = onPort,
        label = { Text("Puerto") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = deviceName,
        onValueChange = onDeviceName,
        label = { Text("Nombre de este teléfono") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = pin,
        onValueChange = onPin,
        label = { Text("PIN de 6 dígitos") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = certPin,
        onValueChange = onCertPin,
        label = { Text("Pin del certificado (SHA-256)") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth()
    )

    SectionLabel("ACCIONES")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onPair, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text("Emparejar")
        }
        Button(onClick = onHealth, enabled = !busy && hasSession, modifier = Modifier.weight(1f)) {
            Text("Probar salud")
        }
    }
    if (hasSession) {
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a la casa")
        }
    }
    OutlinedButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
        Text("Olvidar este teléfono")
    }
}

@Composable
private fun HomeContent(
    session: TokenStore.Session,
    busy: Boolean,
    order: String,
    onOrder: (String) -> Unit,
    reply: String,
    sending: Boolean,
    recording: Boolean,
    noteStatus: String,
    onRecord: () -> Unit,
    onSend: () -> Unit,
    onHealth: () -> Unit,
    onSettings: () -> Unit,
    onForget: () -> Unit
) {
    Surface(
        color = CardColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("CASA")
            Text(
                text = session.baseUrl.removePrefix("https://"),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Dispositivo ${session.deviceId}", fontSize = 12.sp, color = Muted)
            Text("Contrato v${Contract.VERSION}", fontSize = 12.sp, color = Muted)
        }
    }

    Surface(
        color = CardColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("ORDEN A CASA")
            Text(
                text = "Escríbela. Andrómeda la ejecuta y responde con su voz en el altavoz de casa.",
                fontSize = 13.sp,
                color = Color(0xFFDCE4F5)
            )
            OutlinedTextField(
                value = order,
                onValueChange = onOrder,
                label = { Text("Orden para Andrómeda") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSend,
                enabled = !sending && order.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (sending) "Enviando…" else "Enviar a la casa")
            }
            if (reply.isNotEmpty()) {
                SectionLabel("ANDRÓMEDA")
                Text(reply, fontSize = 14.sp, color = Color(0xFFDCE4F5))
            }
        }
    }

    Surface(
        color = CardColor,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("MENSAJE DE VOZ")
            Text(
                text = if (recording) {
                    "Grabando… pulsa «Detener y enviar». Máximo 60 segundos."
                } else {
                    "Grábalo aquí. Andrómeda lo dirá en el altavoz de casa cuando esté libre."
                },
                fontSize = 13.sp,
                color = Color(0xFFDCE4F5)
            )
            Button(
                onClick = onRecord,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (recording) "Detener y enviar" else "Grabar mensaje")
            }
            if (noteStatus.isNotEmpty()) {
                SectionLabel("MENSAJE")
                Text(noteStatus, fontSize = 14.sp, color = Color(0xFFDCE4F5))
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onHealth, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text("Probar conexión")
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
            Text("Ajustes")
        }
    }

    OutlinedButton(onClick = onForget, modifier = Modifier.fillMaxWidth()) {
        Text("Olvidar este teléfono")
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = Muted,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun AndromedaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Color(0xFF0B0B0F),
            surface = Color(0xFF0B0B0F)
        ),
        content = content
    )
}
