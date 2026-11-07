package local.doja.companion.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.doja.companion.Contract
import local.doja.companion.R

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Panel.copy(alpha = 0.80f))
            .border(1.dp, Palette.GlowGradient, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Lavender) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 10.sp,
        color = color.copy(alpha = 0.85f),
        letterSpacing = 2.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(subtitle, fontSize = 13.sp, color = Palette.Muted, lineHeight = 18.sp)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val active = enabled && !loading
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (enabled) Palette.PrimaryGradient else SolidColor(Palette.PanelHigh))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tint = if (enabled) Color.White else Palette.Muted
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val color = when {
        !enabled -> Palette.Muted
        danger -> Palette.Offline
        else -> Palette.Lavender
    }
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        Modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

/** Estado de la casa a partir del texto de estado existente. */
data class HouseState(val label: String, val color: Color)

fun houseState(status: String, busy: Boolean): HouseState = when {
    busy -> HouseState("Consultando", Palette.Warn)
    status.startsWith("Casa en línea") -> HouseState("En línea", Palette.Online)
    status.startsWith("Emparejado") -> HouseState("Emparejado", Palette.Cyan)
    else -> HouseState("Sin conexión", Palette.Offline)
}

@Composable
fun StatusPill(state: HouseState, busy: Boolean, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = !busy, role = Role.Button, onClick = onClick)
    } else Modifier
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(state.color.copy(alpha = 0.12f))
            .border(1.dp, state.color.copy(alpha = 0.40f), CircleShape)
            .then(clickable)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(10.dp), color = state.color, strokeWidth = 1.5.dp)
        } else {
            PulsingDot(state.color)
        }
        Spacer(Modifier.width(7.dp))
        Text(state.label, fontSize = 12.sp, color = state.color, fontWeight = FontWeight.Medium)
    }
}

/** Esfera luminosa con ondas, usada como "presencia" de Andrómeda. */
@Composable
fun Orb(diameter: Dp, modifier: Modifier = Modifier, active: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 1400 else 3200, easing = LinearEasing)),
        label = "orbPulse"
    )
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        for (i in 0..2) {
            val p = (pulse + i / 3f) % 1f
            drawCircle(
                Palette.Lavender.copy(alpha = (1f - p) * 0.35f),
                radius = r * (0.5f + 0.5f * p),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Palette.Violet.copy(alpha = 0.55f), Color.Transparent),
                center = center,
                radius = r * 0.8f
            ),
            radius = r * 0.8f
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Palette.Cyan, Palette.Violet),
                center = center.copy(x = center.x - r * 0.12f, y = center.y - r * 0.12f),
                radius = r * 0.5f
            ),
            radius = r * 0.42f
        )
    }
}

/** El mismo dibujo del icono de la app, recortado en un círculo. */
@Composable
fun LogoMark(size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF0B0B0F))
            .border(1.dp, Palette.GlowGradient, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.45f)
        )
    }
}

/** Barras animadas tipo ecualizador. */
@Composable
fun EqualizerBars(color: Color = Palette.Cyan) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "eqPhase"
    )
    Canvas(Modifier.size(width = 18.dp, height = 14.dp)) {
        val bars = 4
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (0.3f + 0.7f * ((kotlin.math.sin(phase + i * 1.3f) + 1f) / 2f))
            drawRoundRect(
                color,
                topLeft = androidx.compose.ui.geometry.Offset(i * (w + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f)
            )
        }
    }
}

@Composable
fun NoticeChip(text: String, color: Color = Palette.Lavender, leading: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading?.invoke()
        Text(text, fontSize = 13.sp, color = Palette.Text, lineHeight = 18.sp)
    }
}

@Composable
fun NeonField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    textStyle: TextStyle? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = textStyle ?: TextStyle(fontSize = 15.sp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Cyan,
            unfocusedBorderColor = Palette.Stroke,
            focusedLabelColor = Palette.Cyan,
            unfocusedLabelColor = Palette.Muted,
            cursorColor = Palette.Cyan,
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedContainerColor = Palette.Void.copy(alpha = 0.35f),
            unfocusedContainerColor = Palette.Void.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun Footer() {
    Text(
        text = "Contrato v${Contract.VERSION} · Tailscale · TLS fijado",
        fontSize = 11.sp,
        color = Palette.Muted,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
