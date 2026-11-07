package local.doja.companion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta "espacio profundo": violeta del icono + acento cian. */
object Palette {
    val Void = Color(0xFF05060D)
    val Deep = Color(0xFF0C0D1F)
    val Panel = Color(0xFF12142B)
    val PanelHigh = Color(0xFF1B1E3D)
    val Stroke = Color(0x40B39DFF)
    val Violet = Color(0xFF7C4DFF)
    val Indigo = Color(0xFF536DFE)
    val Lavender = Color(0xFFB39DFF)
    val Cyan = Color(0xFF5CE1E6)
    val Text = Color(0xFFE8EBFF)
    val Muted = Color(0xFF8E95BF)
    val Online = Color(0xFF3DDC97)
    val Offline = Color(0xFFFF6B6B)
    val Warn = Color(0xFFFFB74D)

    val PrimaryGradient = Brush.horizontalGradient(listOf(Violet, Indigo))
    val GlowGradient = Brush.linearGradient(listOf(Lavender.copy(alpha = 0.45f), Cyan.copy(alpha = 0.10f)))
}

@Composable
fun AndromedaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Lavender,
            onPrimary = Palette.Void,
            primaryContainer = Palette.Violet,
            onPrimaryContainer = Color.White,
            secondary = Palette.Cyan,
            onSecondary = Palette.Void,
            background = Palette.Void,
            onBackground = Palette.Text,
            surface = Palette.Panel,
            onSurface = Palette.Text,
            surfaceVariant = Palette.PanelHigh,
            onSurfaceVariant = Palette.Muted,
            surfaceContainerHigh = Palette.PanelHigh,
            outline = Palette.Stroke,
            error = Palette.Offline
        ),
        content = content
    )
}

/** Fondo con degradado, nebulosas suaves y estrellas fijas. */
@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val rnd = java.util.Random(7)
        List(80) { Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Palette.Deep, Palette.Void)))
        val violetCenter = Offset(size.width * 0.9f, size.height * 0.04f)
        drawCircle(
            Brush.radialGradient(
                listOf(Palette.Violet.copy(alpha = 0.30f), Color.Transparent),
                center = violetCenter,
                radius = size.width
            ),
            radius = size.width,
            center = violetCenter
        )
        val cyanCenter = Offset(size.width * 0.05f, size.height * 0.8f)
        drawCircle(
            Brush.radialGradient(
                listOf(Palette.Cyan.copy(alpha = 0.10f), Color.Transparent),
                center = cyanCenter,
                radius = size.width * 0.9f
            ),
            radius = size.width * 0.9f,
            center = cyanCenter
        )
        val unit = 1.dp.toPx()
        stars.forEach { (x, y, s) ->
            drawCircle(
                Color.White.copy(alpha = 0.12f + s * 0.45f),
                radius = unit * (0.4f + s * 1.1f),
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}
