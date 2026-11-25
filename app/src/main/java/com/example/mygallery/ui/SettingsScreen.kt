package com.example.mygallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mygallery.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GalleryViewModel, onOpenClean: () -> Unit) {
    val s = vm.settings

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopAppBar(title = { Text("Setelan") })

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Saat menghapus foto")
            RadioRow(
                title = "Pindahkan ke sampah",
                description = "Foto disembunyikan dari galeri ini dan bisa dipulihkan kapan saja. " +
                    "File aslinya baru terhapus saat kamu menghapus permanen. " +
                    "Aplikasi galeri lain masih bisa menampilkannya.",
                selected = !s.permanentDelete,
            ) { vm.updateSettings { it.copy(permanentDelete = false) } }
            RadioRow(
                title = "Hapus permanen",
                description = "File langsung dihapus dari perangkat dan tidak bisa dipulihkan. " +
                    "Android akan meminta konfirmasi setiap kali.",
                selected = s.permanentDelete,
            ) { vm.updateSettings { it.copy(permanentDelete = true) } }

            SectionHeader("Sampah")
            Text(
                "Tawarkan hapus permanen otomatis setelah",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            ChipRow(
                options = listOf("7 hari" to 7, "30 hari" to 30, "60 hari" to 60, "Manual" to 0),
                selectedValue = s.trashDays,
            ) { v -> vm.updateSettings { it.copy(trashDays = v) } }
            Text(
                "Aturan Android mewajibkan konfirmasi untuk menghapus file, jadi saat aplikasi " +
                    "dibuka dan ada item yang sudah lewat batas, kamu akan ditanya dulu.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionHeader("Tampilan")
            Text(
                "Kolom per baris",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            ChipRow(
                options = listOf("2" to 2, "3" to 3, "4" to 4, "5" to 5),
                selectedValue = s.columns,
            ) { v -> vm.updateSettings { it.copy(columns = v) } }
            Text(
                "Tema",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            ChipRow(
                options = listOf("Ikut sistem" to 0, "Terang" to 1, "Gelap" to 2),
                selectedValue = s.theme,
            ) { v -> vm.updateSettings { it.copy(theme = v) } }

            SectionHeader("Alat")
            ListItem(
                headlineContent = { Text("Bersihkan duplikat") },
                supportingContent = { Text("Cari foto dan video kembar untuk menghemat ruang") },
                leadingContent = { Icon(Icons.Filled.ContentCopy, null) },
                modifier = Modifier.clickable(onClick = onOpenClean),
            )

            SectionHeader("Tentang")
            Text(
                "Galeriku 1.0\nTidak ada data yang dikirim ke internet. Semua favorit, album, dan " +
                    "sampah disimpan di perangkatmu saja.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioRow(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChipRow(options: List<Pair<String, Int>>, selectedValue: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = selectedValue == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
