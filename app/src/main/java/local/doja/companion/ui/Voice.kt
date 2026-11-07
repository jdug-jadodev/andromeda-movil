package local.doja.companion.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import local.doja.companion.VoiceNoteRecorder

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun VoiceNoteScreen(
    recording: Boolean,
    noteSending: Boolean,
    noteStatus: String,
    elapsedMs: () -> Long,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        if (recording) {
            while (true) {
                elapsed = elapsedMs()
                delay(100)
            }
        } else {
            elapsed = 0L
        }
    }
    val progress = (elapsed.toFloat() / VoiceNoteRecorder.MAX_MS).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ScreenHeader(
            "Mensaje de voz",
            "Grábalo aquí. Andrómeda lo dirá en el altavoz de casa cuando esté libre."
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            MicButton(recording = recording, progress = progress, onClick = onRecord)
            Text(
                text = if (recording) mmss(elapsed) else "0:00",
                fontSize = 34.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Light,
                color = if (recording) Palette.Text else Palette.Muted
            )
            Text(
                text = if (recording) {
                    "Grabando… pulsa «Detener y enviar». Máximo 60 segundos."
                } else {
                    "Toca el micrófono para empezar. Máximo ${mmss(VoiceNoteRecorder.MAX_MS)}."
                },
                fontSize = 13.sp,
                color = Palette.Muted,
                textAlign = TextAlign.Center
            )
        }

        if (recording) {
            SecondaryButton(
                text = "Detener y enviar",
                onClick = onRecord,
                danger = true,
                icon = AppIcons.Stop,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            PrimaryButton(
                text = "Grabar mensaje",
                onClick = onRecord,
                icon = AppIcons.Mic,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (noteStatus.isNotEmpty()) {
            GlassCard {
                SectionLabel("MENSAJE")
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (noteSending) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                    }
                    Text(noteStatus, fontSize = 14.sp, color = Palette.Text, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun MicButton(recording: Boolean, progress: Float, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (recording) 1300 else 3000, easing = LinearEasing)),
        label = "micPulse"
    )
    val ringColor = if (recording) Palette.Offline else Palette.Lavender
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            for (i in 0..2) {
                val p = (pulse + i / 3f) % 1f
                drawCircle(
                    ringColor.copy(alpha = (1f - p) * if (recording) 0.45f else 0.22f),
                    radius = r * (0.58f + 0.42f * p),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
            val stroke = 4.dp.toPx()
            val arcR = r * 0.66f
            val topLeft = Offset(center.x - arcR, center.y - arcR)
            drawCircle(Palette.PanelHigh, radius = arcR, style = Stroke(width = stroke))
            if (recording) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Palette.Cyan, Palette.Violet, Palette.Cyan)),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcR * 2, arcR * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    if (recording) Brush.linearGradient(listOf(Palette.Offline, Palette.Violet))
                    else Palette.PrimaryGradient
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (recording) AppIcons.Stop else AppIcons.Mic,
                contentDescription = if (recording) "Detener y enviar" else "Grabar mensaje",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
