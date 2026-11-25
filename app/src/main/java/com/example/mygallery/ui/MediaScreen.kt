package com.example.mygallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.MediaItem

/**
 * Layar grid serbaguna: dipakai untuk tab Foto, Favorit, Sampah, dan isi album.
 * Tekan lama untuk memilih banyak item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    title: String,
    items: List<MediaItem>,
    vm: GalleryViewModel,
    actions: GalleryActions,
    trashMode: Boolean = false,
    customAlbum: String? = null,
    subtitle: String? = null,
    emptyText: String = "Belum ada foto",
    onBack: (() -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selectedItems = remember(items, selected) {
        if (selected.isEmpty()) emptyList<MediaItem>() else items.filter { it.id in selected }
    }
    val selecting = selected.isNotEmpty()

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        if (selecting) {
            TopAppBar(
                title = { Text("${selected.size} dipilih") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, "Batal pilih")
                    }
                },
                actions = {
                    IconButton(onClick = { selected = items.map { it.id }.toSet() }) {
                        Icon(Icons.Filled.SelectAll, "Pilih semua")
                    }
                    if (trashMode) {
                        IconButton(onClick = {
                            vm.restore(selectedItems)
                            selected = emptySet()
                        }) { Icon(Icons.Filled.Restore, "Pulihkan") }
                        IconButton(onClick = {
                            actions.deleteForever(selectedItems)
                            selected = emptySet()
                        }) { Icon(Icons.Filled.DeleteForever, "Hapus permanen") }
                    } else {
                        val allFav = selectedItems.isNotEmpty() &&
                            selectedItems.all { it.id in vm.favorites }
                        IconButton(onClick = {
                            vm.setFavorite(selectedItems, !allFav)
                            selected = emptySet()
                        }) {
                            Icon(
                                if (allFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favorit",
                            )
                        }
                        IconButton(onClick = {
                            actions.addToAlbum(selectedItems)
                            selected = emptySet()
                        }) { Icon(Icons.Filled.PhotoAlbum, "Tambah ke album") }
                        if (customAlbum != null) {
                            IconButton(onClick = {
                                vm.removeFromAlbum(customAlbum, selectedItems)
                                selected = emptySet()
                            }) { Icon(Icons.Filled.RemoveCircleOutline, "Keluarkan dari album") }
                        }
                        IconButton(onClick = {
                            actions.delete(selectedItems)
                            selected = emptySet()
                        }) { Icon(Icons.Filled.Delete, "Hapus") }
                    }
                },
            )
        } else {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    }
                },
                actions = topActions,
            )
        }

        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(vm.settings.columns),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    Thumb(
                        item = item,
                        favorite = item.id in vm.favorites,
                        selected = item.id in selected,
                        selecting = selecting,
                        onClick = {
                            if (selecting) {
                                selected = if (item.id in selected) selected - item.id else selected + item.id
                            } else {
                                actions.open(items, index, trashMode)
                            }
                        },
                        onLongClick = { selected = selected + item.id },
                    )
                }
            }
        }
    }
}
