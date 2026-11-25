package com.example.mygallery.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

private fun shareItem(ctx: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (item.isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, item.uri)
        clipData = ClipData.newRawUri(null, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "Bagikan"))
}

@Composable
fun ViewerScreen(
    req: ViewerRequest,
    vm: GalleryViewModel,
    actions: GalleryActions,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    // Daftar biasa (tidak dimutasi satu per satu): item yang dibuang/dipulihkan/dihapus langsung tersaring.
    val list = remember(req.items, vm.trash, vm.gone) {
        req.items.filter { it.id !in vm.gone && ((it.id in vm.trash) == req.trash) }
    }
    val pager = rememberPagerState(
        initialPage = req.index.coerceIn(0, (req.items.size - 1).coerceAtLeast(0))
    ) { list.size }

    var showBars by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    LaunchedEffect(list.size) { if (list.isEmpty()) onClose() }
    LaunchedEffect(pager.currentPage) { zoomed = false }

    if (list.isEmpty()) return

    val item = list[pager.currentPage.coerceIn(0, list.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            key = { index -> list.getOrNull(index)?.id ?: "x$index" },
        ) { page ->
            val m = list.getOrNull(page)
            if (m != null) {
                if (m.isVideo) {
                    VideoPage(m, onTap = { showBars = !showBars })
                } else {
                    ZoomableImage(
                        uri = m.uri,
                        onTap = { showBars = !showBars },
                        onZoomed = { zoomed = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        formatDate(item.takenMs),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${pager.currentPage + 1} / ${list.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (req.trash) {
                    BarButton(Icons.Filled.Restore, "Pulihkan") { vm.restore(listOf(item)) }
                    BarButton(Icons.Filled.Info, "Info") { showInfo = true }
                    BarButton(Icons.Filled.DeleteForever, "Hapus") { actions.deleteForever(listOf(item)) }
                } else {
                    val fav = item.id in vm.favorites
                    BarButton(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Favorit",
                    ) { vm.toggleFavorite(item) }
                    BarButton(Icons.Filled.Share, "Bagikan") { shareItem(ctx, item) }
                    BarButton(Icons.Filled.PhotoAlbum, "Album") { actions.addToAlbum(listOf(item)) }
                    BarButton(Icons.Filled.Info, "Info") { showInfo = true }
                    BarButton(Icons.Filled.Delete, "Hapus") { actions.delete(listOf(item)) }
                }
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Info") },
            text = {
                Column {
                    Text(item.name)
                    Text("Tanggal: ${formatDate(item.takenMs)}")
                    Text("Ukuran: ${Formatter.formatShortFileSize(ctx, item.size)}")
                    if (item.width > 0 && item.height > 0) {
                        Text("Resolusi: ${item.width} × ${item.height}")
                    }
                    if (item.isVideo) Text("Durasi: ${fmtDuration(item.durationMs)}")
                    Text("Folder: ${item.folder.ifBlank { "Lainnya" }}")
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun VideoPage(item: MediaItem, onTap: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(item.uri, "video/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        ctx.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        // tidak ada pemutar video
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, "Putar", tint = Color.White, modifier = Modifier.size(44.dp))
        }
    }
}

/** Gambar dengan cubit-untuk-zoom, geser saat di-zoom, dan ketuk dua kali. */
@Composable
private fun ZoomableImage(uri: Uri, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                        onZoomed(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = box.width * (scale - 1f) / 2f
                            val maxY = box.height * (scale - 1f) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                            onZoomed(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(uri).size(2048).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
