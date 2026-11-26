package com.alpa.galeriku.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alpa.galeriku.data.InfoLine
import com.alpa.galeriku.data.MediaInfo
import com.alpa.galeriku.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dialog info lengkap: file, EXIF (foto), dan detail teknis (video). Teks bisa disalin dengan tekan lama. */
@Composable
fun InfoDialog(item: MediaItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var lines by remember(item.id) { mutableStateOf<List<InfoLine>?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    LaunchedEffect(item.id, tick) {
        lines = withContext(Dispatchers.IO) { MediaInfo.load(ctx, item) }
    }

    val hasLocation = remember(tick) { MediaInfo.hasLocationAccess(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Info") },
        text = {
            val l = lines
            if (l == null) {
                CircularProgressIndicator()
            } else {
                SelectionContainer {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        l.forEach { line ->
                            if (line.header) {
                                Text(
                                    line.label,
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Column {
                                    Text(
                                        line.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(line.value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        dismissButton = {
            if (!item.isVideo && !hasLocation) {
                TextButton(onClick = {
                    locationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
                }) { Text("Izinkan lokasi") }
            }
        },
    )
}

/** Satu pilihan folder tujuan untuk pindah/salin. */
class FolderTarget(val name: String, val path: String)

@Composable
fun FolderPickerDialog(
    title: String,
    targets: List<FolderTarget>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NameDialog(
            title = "Folder baru",
            initial = "",
            confirmText = "Buat",
            label = "Nama folder",
            onConfirm = { raw ->
                val name = com.alpa.galeriku.data.FileOps.cleanName(raw)
                if (name.isNotBlank()) onPick("Pictures/$name/")
            },
            onDismiss = { creating = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (targets.isEmpty()) {
                    Text(
                        "Belum ada folder tujuan. Buat folder baru.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                targets.forEach { t ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickableRow { onPick(t.path) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            t.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("Folder baru") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
