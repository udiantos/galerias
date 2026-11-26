package com.alpa.galeriku.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

private val PillBg = Color(0xCC0B0F1A)
private val PillBorder = Color.White.copy(alpha = 0.15f)
private val ActionColor = Color(0xFF93C5FD)
private val OkColor = Color(0xFF22C55E)
private val DeleteColor = Color(0xFFEF4444)
private val WarnColor = Color(0xFFF59E0B)

/** Pengganti SnackbarHost: notifikasi berbentuk pil semi transparan. API pemanggilan tetap showSnackbar(). */
@Composable
fun PillSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data -> PillToast(data) },
    )
}

@Composable
private fun PillToast(data: SnackbarData) {
    val message = data.visuals.message
    val actionLabel = data.visuals.actionLabel
    val shape = RoundedCornerShape(50)
    var dragX by remember(data) { mutableFloatStateOf(0f) }

    // Tanpa aksi menghilang cepat; dengan "Urungkan" bertahan lebih lama supaya sempat ditekan.
    LaunchedEffect(data) {
        val wait = if (actionLabel != null) 4000L
        else (1500L + message.length * 40L).coerceIn(2200L, 5000L)
        delay(wait)
        data.dismiss()
    }

    val (icon, tint) = when {
        actionLabel != null -> Icons.Filled.Delete to DeleteColor
        message.contains("gagal", ignoreCase = true) -> Icons.Filled.Warning to WarnColor
        else -> Icons.Filled.Check to OkColor
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer { translationX = dragX }
                .alpha(1f - (abs(dragX) / 600f).coerceIn(0f, 0.8f))
                .pointerInput(data) {
                    detectHorizontalDragGestures(
                        onDragEnd = { if (abs(dragX) > 160f) data.dismiss() else dragX = 0f },
                        onDragCancel = { dragX = 0f },
                    ) { _, delta -> dragX += delta }
                }
                .background(PillBg, shape)
                .border(0.5.dp, PillBorder, shape)
                .padding(start = 12.dp, end = if (actionLabel != null) 6.dp else 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(22.dp).background(tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (actionLabel != null) {
                Box(Modifier.width(0.5.dp).height(18.dp).background(Color.White.copy(alpha = 0.25f)))
                Text(
                    text = actionLabel,
                    color = ActionColor,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { data.performAction() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
