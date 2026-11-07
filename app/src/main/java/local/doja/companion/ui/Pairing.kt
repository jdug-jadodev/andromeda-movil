package local.doja.companion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.doja.companion.Contract
import local.doja.companion.TokenStore

/** Campos del formulario de emparejamiento (el estado vive en MainActivity). */
class PairingFields(
    val host: String,
    val onHost: (String) -> Unit,
    val port: String,
    val onPort: (String) -> Unit,
    val deviceName: String,
    val onDeviceName: (String) -> Unit,
    val pin: String,
    val onPin: (String) -> Unit,
    val certPin: String,
    val onCertPin: (String) -> Unit
)

@Composable
fun PairingForm(fields: PairingFields) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (advanced) 180f else 0f, label = "arrow")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonField(fields.deviceName, fields.onDeviceName, "Nombre de este teléfono")
        NeonField(
            fields.pin,
            fields.onPin,
            "PIN de 6 dígitos",
            keyboardType = KeyboardType.NumberPassword,
            password = true
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { advanced = !advanced }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Conexión avanzada", fontSize = 14.sp, color = Palette.Lavender, fontWeight = FontWeight.Medium)
                Text("${fields.host.trim()}:${fields.port.trim()}", fontSize = 12.sp, color = Palette.Muted)
            }
            Icon(
                AppIcons.ExpandMore,
                contentDescription = if (advanced) "Ocultar" else "Mostrar",
                tint = Palette.Lavender,
                modifier = Modifier.rotate(arrow)
            )
        }
        AnimatedVisibility(visible = advanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonField(fields.host, fields.onHost, "Host o IP")
                NeonField(fields.port, fields.onPort, "Puerto", keyboardType = KeyboardType.Number)
                NeonField(
                    fields.certPin,
                    fields.onCertPin,
                    "Pin del certificado (SHA-256)",
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** Pantalla de bienvenida cuando el teléfono aún no está emparejado. */
@Composable
fun PairingScreen(
    status: String,
    busy: Boolean,
    fields: PairingFields,
    onPair: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Orb(150.dp, active = busy)
        Text("Andrómeda", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(
            "Conecta este teléfono con tu casa",
            fontSize = 14.sp,
            color = Palette.Muted,
            textAlign = TextAlign.Center
        )
        val error = status.startsWith("Error") || status.startsWith("El PIN")
        NoticeChip(status, if (error) Palette.Offline else Palette.Lavender) {
            PulsingDot(if (error) Palette.Offline else if (busy) Palette.Warn else Palette.Lavender)
        }
        Spacer(Modifier.height(4.dp))
        GlassCard {
            SectionLabel("EMPAREJAR")
            PairingForm(fields)
            PrimaryButton(
                text = if (busy) "Emparejando…" else "Emparejar",
                onClick = onPair,
                enabled = !busy,
                loading = busy,
                icon = AppIcons.Link,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Footer()
    }
}

/** Pestaña "Enlace": estado de la casa, conexión y re-emparejamiento. */
@Composable
fun LinkScreen(
    session: TokenStore.Session,
    status: String,
    busy: Boolean,
    onHealth: () -> Unit,
    relinking: Boolean,
    onToggleRelink: () -> Unit,
    fields: PairingFields,
    onPair: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Enlace con la casa", "Conexión, emparejamiento y seguridad de este teléfono.")

        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("CASA", Modifier.weight(1f))
                StatusPill(houseState(status, busy), busy)
            }
            Text(
                session.baseUrl.removePrefix("https://"),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Text
            )
            InfoRow("Dispositivo", session.deviceId)
            InfoRow("Contrato", "v${Contract.VERSION}")
            InfoRow("Canal", "Tailscale · TLS fijado")
            Text(status, fontSize = 13.sp, color = Palette.Muted, lineHeight = 18.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton(
                text = "Probar conexión",
                onClick = onHealth,
                enabled = !busy,
                loading = busy,
                icon = AppIcons.Refresh,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = if (relinking) "Cerrar" else "Ajustes",
                onClick = onToggleRelink,
                icon = AppIcons.Link,
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(visible = relinking) {
            GlassCard {
                SectionLabel("VOLVER A EMPAREJAR")
                Text(
                    "Cambia la conexión o empareja de nuevo con un PIN de la casa.",
                    fontSize = 13.sp,
                    color = Palette.Muted
                )
                PairingForm(fields)
                PrimaryButton(
                    text = if (busy) "Emparejando…" else "Emparejar",
                    onClick = onPair,
                    enabled = !busy,
                    loading = busy,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        SecondaryButton(
            text = "Olvidar este teléfono",
            onClick = onForget,
            danger = true,
            icon = AppIcons.Delete,
            modifier = Modifier.fillMaxWidth()
        )
        Footer()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, color = Palette.Text, fontWeight = FontWeight.Medium)
    }
}
