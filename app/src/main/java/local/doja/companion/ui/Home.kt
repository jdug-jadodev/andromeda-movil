package local.doja.companion.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val TAB_CHAT = 0
const val TAB_VOICE = 1
const val TAB_CAMERA = 2
const val TAB_LINK = 3

private class TabSpec(val label: String, val subtitle: String, val icon: ImageVector)

private val TABS = listOf(
    TabSpec("Chat", "Habla con tu casa", AppIcons.Chat),
    TabSpec("Voz", "Mensaje de voz", AppIcons.Mic),
    TabSpec("Cámara", "Cámara en vivo", AppIcons.Videocam),
    TabSpec("Enlace", "Conexión y ajustes", AppIcons.Link)
)

@Composable
fun HomeShell(
    tab: Int,
    onTab: (Int) -> Unit,
    status: String,
    busy: Boolean,
    onHealth: () -> Unit,
    recording: Boolean,
    watching: Boolean,
    content: @Composable (tab: Int, modifier: Modifier) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LogoMark(40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Andrómeda", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                    Text(TABS[tab].subtitle, fontSize = 12.sp, color = Palette.Muted)
                }
                StatusPill(houseState(status, busy), busy, onClick = onHealth)
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = Palette.Stroke, thickness = 1.dp)
                NavigationBar(containerColor = Palette.Panel.copy(alpha = 0.94f), tonalElevation = 0.dp) {
                    TABS.forEachIndexed { index, spec ->
                        val badge = when (index) {
                            TAB_VOICE -> if (recording) Palette.Offline else null
                            TAB_CAMERA -> if (watching) Palette.Online else null
                            else -> null
                        }
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { onTab(index) },
                            icon = {
                                Box {
                                    Icon(spec.icon, contentDescription = spec.label)
                                    if (badge != null) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-2).dp)
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(badge)
                                        )
                                    }
                                }
                            },
                            label = { Text(spec.label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Cyan,
                                selectedTextColor = Palette.Cyan,
                                indicatorColor = Palette.Violet.copy(alpha = 0.25f),
                                unselectedIconColor = Palette.Muted,
                                unselectedTextColor = Palette.Muted
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        Crossfade(
            targetState = tab,
            animationSpec = tween(220),
            label = "tabs",
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner)
        ) { current ->
            content(current, Modifier.fillMaxSize())
        }
    }
}
