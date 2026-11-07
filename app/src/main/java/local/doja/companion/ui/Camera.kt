package local.doja.companion.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraScreen(
    watching: Boolean,
    frame: Bitmap?,
    note: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            "Cámara en vivo",
            "Mira la cámara del Moto en directo. No se graba nada; máximo 10 minutos y un espectador."
        )

        val ratio = frame?.let { (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.5f, 2f) } ?: (4f / 3f)
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(shape)
                .background(Palette.Void)
                .border(1.dp, if (frame != null) Palette.Cyan.copy(alpha = 0.6f) else Palette.Stroke, shape)
                .hudCorners(if (frame != null) Palette.Cyan else Palette.Lavender.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                frame != null -> {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Cámara en vivo de la casa",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(Palette.Offline, 7.dp)
                        androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                        Text("EN VIVO", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
                watching -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Palette.Cyan, strokeWidth = 2.dp)
                    Text("Conectando…", fontSize = 13.sp, color = Palette.Muted)
                }
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(AppIcons.Videocam, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(42.dp))
                    Text("Cámara en reposo", fontSize = 13.sp, color = Palette.Muted)
                }
            }
        }

        if (note.isNotEmpty()) {
            NoticeChip(note, if (frame != null) Palette.Cyan else Palette.Lavender)
        }

        if (watching) {
            SecondaryButton(
                text = "Detener la cámara",
                onClick = onStop,
                danger = true,
                icon = AppIcons.Stop,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            PrimaryButton(
                text = "Ver cámara",
                onClick = onStart,
                icon = AppIcons.Videocam,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Sin grabación", "10 min máx.", "1 espectador").forEach { tag ->
                Text(
                    tag,
                    fontSize = 11.sp,
                    color = Palette.Muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, Palette.Stroke, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** Esquinas tipo visor (HUD) dibujadas sobre el contenido. */
private fun Modifier.hudCorners(color: Color): Modifier = drawWithContent {
    drawContent()
    val inset = 10.dp.toPx()
    val len = 18.dp.toPx()
    val w = 2.dp.toPx()
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset
    fun line(a: Offset, c: Offset) = drawLine(color, a, c, strokeWidth = w, cap = StrokeCap.Round)
    line(Offset(l, t), Offset(l + len, t)); line(Offset(l, t), Offset(l, t + len))
    line(Offset(r, t), Offset(r - len, t)); line(Offset(r, t), Offset(r, t + len))
    line(Offset(l, b), Offset(l + len, b)); line(Offset(l, b), Offset(l, b - len))
    line(Offset(r, b), Offset(r - len, b)); line(Offset(r, b), Offset(r, b - len))
}
