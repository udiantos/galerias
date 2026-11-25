package com.example.mygallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(vm: GalleryViewModel, onOpen: (AlbumRef) -> Unit) {
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            title = { Text("Album") },
            actions = {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, "Album baru")
                }
            },
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Album saya", style = MaterialTheme.typography.titleMedium)
            }
            if (vm.albums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Belum ada album. Tekan + untuk membuat album baru.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(vm.albums.keys.toList(), key = { "a:$it" }) { name ->
                val list = remember(vm.visible, vm.albums) { vm.albumItems(name) }
                AlbumCard(name, list.size, list.firstOrNull()) { onOpen(AlbumRef(name, true)) }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Folder di perangkat",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(vm.folders, key = { "f:${it.first}" }) { entry ->
                AlbumCard(entry.first, entry.second.size, entry.second.firstOrNull()) {
                    onOpen(AlbumRef(entry.first, false))
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Album baru",
            initial = "",
            confirmText = "Buat",
            onConfirm = { name ->
                vm.createAlbum(name)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun AlbumCard(name: String, count: Int, cover: MediaItem?, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                ThumbImage(cover, Modifier.fillMaxSize())
            } else {
                Icon(
                    Icons.Filled.PhotoAlbum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            name,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$count item",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Isi sebuah album (buatan sendiri atau folder perangkat). */
@Composable
fun AlbumDetailScreen(
    ref: AlbumRef,
    vm: GalleryViewModel,
    actions: GalleryActions,
    onBack: () -> Unit,
    onRenamed: (String) -> Unit,
) {
    val items = remember(vm.visible, vm.albums, ref) {
        if (ref.custom) vm.albumItems(ref.name) else vm.folderItems(ref.name)
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    MediaScreen(
        title = ref.name,
        items = items,
        vm = vm,
        actions = actions,
        customAlbum = if (ref.custom) ref.name else null,
        emptyText = if (ref.custom) {
            "Album masih kosong. Tekan lama foto di tab Foto, lalu tambahkan ke album ini."
        } else {
            "Folder kosong"
        },
        onBack = onBack,
        topActions = {
            if (ref.custom) {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "Menu")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ganti nama") },
                        onClick = {
                            menu = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus album") },
                        onClick = {
                            menu = false
                            confirmDelete = true
                        },
                    )
                }
            }
        },
    )

    if (renaming) {
        NameDialog(
            title = "Ganti nama album",
            initial = ref.name,
            onConfirm = { newName ->
                if (vm.renameAlbum(ref.name, newName)) {
                    renaming = false
                    onRenamed(newName)
                }
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus album \"${ref.name}\"?") },
            text = { Text("Hanya albumnya yang dihapus. Foto dan video di dalamnya tetap aman.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAlbum(ref.name)
                    onBack()
                }) { Text("Hapus album") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
