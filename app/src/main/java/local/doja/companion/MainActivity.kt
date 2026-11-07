package local.doja.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.doja.companion.net.GatewayClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndromedaTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val store = remember { TokenStore(context) }
    val scope = rememberCoroutineScope()

    var host by rememberSaveable { mutableStateOf("100.79.112.122") }
    var port by rememberSaveable { mutableStateOf("11440") }
    var deviceName by rememberSaveable { mutableStateOf("A56") }
    var pin by rememberSaveable { mutableStateOf("") }
    var certPin by rememberSaveable {
        mutableStateOf("c390aca21cb3920030f8f5879a44060a95baba7d9d82fd6489612d44192e7091")
    }
    var status by rememberSaveable { mutableStateOf("Casa: sin emparejar") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var session by remember { mutableStateOf<TokenStore.Session?>(null) }

    LaunchedEffect(Unit) {
        session = store.session()
        if (session != null) status = "Casa emparejada: ${session?.deviceId}"
    }

    fun baseUrl(): String = "https://${host.trim()}:${port.trim()}"

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
                status = "Error: ${error.message}"
            } finally {
                busy = false
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Andrómeda", fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            Text(status, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host o IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Puerto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it.take(40) },
                label = { Text("Nombre de este teléfono") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                label = { Text("PIN de 6 dígitos") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = certPin,
                onValueChange = { certPin = it.trim() },
                label = { Text("Pin del certificado (SHA-256)") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (pin.length != 6) {
                            status = "El PIN debe tener 6 dígitos"
                            return@Button
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
                                session = store.session()
                                status = "Emparejado: $deviceId"
                            } catch (error: Exception) {
                                status = "Error: ${error.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Emparejar")
                }
                Button(
                    onClick = {
                        val current = session
                        if (current == null) status = "Primero empareja"
                        else askHealth(current)
                    },
                    enabled = !busy && session != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Probar salud")
                }
            }

            OutlinedButton(
                onClick = {
                    store.clear()
                    session = null
                    status = "Casa: sin emparejar"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Olvidar este teléfono")
            }

            Text(
                "Contrato v${Contract.VERSION} · la casa debe estar en Tailscale",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AndromedaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C4DFF),
            background = Color(0xFF0B0B0F),
            surface = Color(0xFF0B0B0F)
        ),
        content = content
    )
}
