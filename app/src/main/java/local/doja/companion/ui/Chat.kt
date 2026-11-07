package local.doja.companion.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Una línea del historial de chat (solo en memoria de la sesión de la app). */
data class ChatEntry(val kind: Int, val at: Long, val text: String) {
    companion object {
        const val USER = 0
        const val HOUSE = 1
        const val ERROR = 2

        fun encode(entry: ChatEntry): String = "${entry.kind}|${entry.at}|${entry.text}"

        fun decode(raw: String): ChatEntry {
            val parts = raw.split('|', limit = 3)
            return ChatEntry(parts[0].toInt(), parts[1].toLong(), parts.getOrElse(2) { "" })
        }
    }
}

private val SUGGESTIONS = listOf("Apaga las luces", "Enciende las luces")

@Composable
fun ChatScreen(
    chat: List<ChatEntry>,
    order: String,
    onOrder: (String) -> Unit,
    sending: Boolean,
    awaitingReply: Boolean,
    pending: String,
    voiceStatus: String,
    replyPlaying: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val itemCount = (if (chat.isEmpty()) 1 else chat.size) + (if (awaitingReply) 1 else 0)
    LaunchedEffect(chat.size, awaitingReply) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chat.isEmpty()) {
                item { ChatWelcome(onSuggestion = onOrder) }
            }
            items(chat.size) { index -> ChatBubble(chat[index]) }
            if (awaitingReply) {
                item { TypingBubble(pending) }
            }
        }

        if (replyPlaying || voiceStatus.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (replyPlaying) {
                    NoticeChip("Reproduciendo la respuesta…", Palette.Cyan) { EqualizerBars() }
                }
                if (voiceStatus.isNotEmpty()) {
                    NoticeChip(voiceStatus)
                }
            }
        }

        ChatInput(order = order, onOrder = onOrder, sending = sending, onSend = onSend)
    }
}

@Composable
private fun ChatWelcome(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Orb(140.dp)
        Text(
            "¿Qué necesitas en casa?",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Text
        )
        Text(
            "Escribe una orden. Andrómeda la ejecuta y responde con su voz en el altavoz de casa.",
            fontSize = 14.sp,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTIONS.forEach { suggestion ->
                Text(
                    text = suggestion,
                    fontSize = 13.sp,
                    color = Palette.Lavender,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Palette.Violet.copy(alpha = 0.12f))
                        .border(1.dp, Palette.Stroke, CircleShape)
                        .clickable(role = Role.Button) { onSuggestion(suggestion) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun HouseAvatar(error: Boolean = false) {
    val color = if (error) Palette.Offline else Palette.Cyan
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(if (error) "!" else "A", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun clock(at: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val mine = entry.kind == ChatEntry.USER
    val error = entry.kind == ChatEntry.ERROR
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            HouseAvatar(error)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
        ) {
            val shape = if (mine) {
                RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
            } else {
                RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
            }
            val background = when {
                mine -> Palette.PrimaryGradient
                error -> SolidColor(Palette.Offline.copy(alpha = 0.10f))
                else -> SolidColor(Palette.Panel.copy(alpha = 0.92f))
            }
            val border = when {
                mine -> Color.Transparent
                error -> Palette.Offline.copy(alpha = 0.45f)
                else -> Palette.Stroke
            }
            Text(
                text = entry.text,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = if (mine) Color.White else Palette.Text,
                modifier = Modifier
                    .clip(shape)
                    .background(background)
                    .border(1.dp, border, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Text(
                text = if (mine) clock(entry.at) else "Andrómeda · ${clock(entry.at)}",
                fontSize = 10.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun TypingBubble(pending: String) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "typingPhase"
    )
    Row(verticalAlignment = Alignment.Bottom) {
        HouseAvatar()
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(Palette.Panel.copy(alpha = 0.92f))
                .border(1.dp, Palette.Stroke, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0..2) {
                    val on = phase.toInt() == i
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(if (on) 1f else 0.35f)
                            .clip(CircleShape)
                            .background(Palette.Cyan)
                    )
                }
            }
            if (pending.isNotEmpty()) {
                Text(pending, fontSize = 12.sp, color = Palette.Muted)
            }
        }
    }
}

@Composable
private fun ChatInput(
    order: String,
    onOrder: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit
) {
    val canSend = !sending && order.isNotBlank()
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            TextField(
                value = order,
                onValueChange = onOrder,
                placeholder = { Text("Orden para Andrómeda…") },
                maxLines = 4,
                shape = shape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Palette.Panel,
                    unfocusedContainerColor = Palette.Panel.copy(alpha = 0.88f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Palette.Cyan,
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                    focusedPlaceholderColor = Palette.Muted,
                    unfocusedPlaceholderColor = Palette.Muted
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Palette.Stroke, shape)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (canSend) Palette.PrimaryGradient else SolidColor(Palette.PanelHigh))
                    .clickable(enabled = canSend, role = Role.Button, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Cyan, strokeWidth = 2.dp)
                } else {
                    Icon(
                        AppIcons.Send,
                        contentDescription = "Enviar a la casa",
                        tint = if (canSend) Color.White else Palette.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 70.dp, top = 6.dp)
        ) {
            Text(
                "Órdenes por voz suspendidas por ahora · escribe tu orden.",
                fontSize = 11.sp,
                color = Palette.Muted,
                modifier = Modifier.weight(1f)
            )
            if (order.length > 400) {
                Text("${order.length}/500", fontSize = 11.sp, color = Palette.Warn)
            }
        }
    }
}
