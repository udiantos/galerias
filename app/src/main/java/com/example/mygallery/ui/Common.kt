package com.example.mygallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.mygallery.data.thumbRequest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.MediaItem

/** Kumpulan aksi yang dipakai bersama oleh semua layar. */
class GalleryActions(
    val open: (items: List<MediaItem>, index: Int, trash: Boolean) -> Unit,
    val delete: (List<MediaItem>) -> Unit,
    val deleteForever: (List<MediaItem>) -> Unit,
    val addToAlbum: (List<MediaItem>) -> Unit,
    val move: (List<MediaItem>) -> Unit,
    val copy: (List<MediaItem>) -> Unit,
    val rename: (MediaItem) -> Unit,
)

class AlbumRef(val name: String, val custom: Boolean)

class ViewerRequest(val items: List<MediaItem>, val index: Int, val trash: Boolean)

fun fmtDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    favorite: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        val ctx = LocalContext.current
        val request = remember(item.id) { thumbRequest(ctx, item, 256) }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = if (selected) {
                Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
            } else {
                Modifier.fillMaxSize()
            },
        )

        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                Text(fmtDuration(item.durationMs), color = Color.White, fontSize = 11.sp)
            }
        }

        if (favorite && !selecting) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, null, Modifier.size(12.dp), tint = Color.White)
            }
        }

        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmText: String = "Simpan",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "Nama album",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(label) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Dialog pilih album untuk menambahkan foto. onDone(pesan) dipanggil saat selesai/batal (pesan null = batal). */
@Composable
fun AlbumPickerDialog(
    vm: GalleryViewModel,
    items: List<MediaItem>,
    onDone: (String?) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "Album baru",
            initial = "",
            confirmText = "Buat",
            onConfirm = { name ->
                vm.addToAlbum(name, items)
                onDone("Ditambahkan ke \"$name\"")
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("Tambahkan ke album") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.albums.isEmpty()) {
                    Text(
                        "Belum ada album. Buat yang baru dulu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                vm.albums.keys.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.addToAlbum(name, items)
                                onDone("Ditambahkan ke \"$name\"")
                            }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("Album baru") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Batal") } },
    )
}
