package com.example.mygallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.MediaItem

private fun toggleDup(selected: Set<Long>, group: List<MediaItem>, id: Long): Set<Long> {
    if (id in selected) return selected - id
    // Minimal satu file di tiap kelompok harus tetap disimpan.
    val alreadyMarked = group.count { it.id in selected }
    return if (alreadyMarked < group.size - 1) selected + id else selected
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(vm: GalleryViewModel, actions: GalleryActions, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(vm.visible) { vm.findDuplicates() }
    // Bawaan: buang semua kecuali yang paling tua di tiap kelompok.
    var selected by remember(groups) {
        mutableStateOf(groups.flatMap { g -> g.drop(1) }.map { it.id }.toSet())
    }
    val chosen = remember(groups, selected) {
        groups.flatten().filter { it.id in selected }
    }
    val chosenBytes = chosen.sumOf { it.size }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(
            title = { Text("Bersihkan duplikat") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                }
            },
        )

        if (groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Tidak ada duplikat ditemukan",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "Dianggap duplikat jika ukuran file dan resolusinya sama. File tertua dipertahankan " +
                    "secara bawaan. Ketuk foto untuk mengubah pilihan.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(groups) { _, group ->
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "${group.size} file • ${Formatter.formatShortFileSize(ctx, group[0].size)} per file",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LazyRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(group, key = { it.id }) { item ->
                                DupThumb(item = item, remove = item.id in selected) {
                                    selected = toggleDup(selected, group, item.id)
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { actions.delete(chosen) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                val size = Formatter.formatShortFileSize(ctx, chosenBytes)
                Text(
                    if (vm.settings.permanentDelete) {
                        "Hapus permanen ${chosen.size} file ($size)"
                    } else {
                        "Pindahkan ${chosen.size} file ke sampah ($size)"
                    }
                )
            }
        }
    }
}

@Composable
private fun DupThumb(item: MediaItem, remove: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (remove) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        Text(
            if (remove) "Buang" else "Simpan",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .background(
                    if (remove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 11.sp,
        )
    }
}
