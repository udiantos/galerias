package com.example.mygallery.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.mygallery.GalleryViewModel
import com.example.mygallery.data.FileOps
import com.example.mygallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private fun mediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAccess(ctx: Context): Boolean =
    mediaPermissions().any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun GalleryRoot(vm: GalleryViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAccess(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasAccess(context)
        if (granted) vm.refresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasAccess(context)
        if (granted) vm.refresh()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (granted) {
            GalleryMain(vm)
        } else {
            PermissionScreen(onGrant = { permissionLauncher.launch(mediaPermissions()) })
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Izinkan akses foto dan video",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Galeri butuh izin untuk menampilkan foto dan video di perangkatmu. " +
                "Tidak ada data yang dikirim ke internet.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Beri izin") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            )
        }) { Text("Buka pengaturan aplikasi") }
    }
}

private class FolderOp(val items: List<MediaItem>, val move: Boolean)

private class PendingWrite(val items: List<MediaItem>, val dest: String?, val newName: String?)

private val tabs = listOf(
    "Foto" to Icons.Filled.PhotoLibrary,
    "Album" to Icons.Filled.PhotoAlbum,
    "Favorit" to Icons.Filled.Favorite,
    "Sampah" to Icons.Filled.Delete,
    "Setelan" to Icons.Filled.Settings,
)

@Composable
private fun GalleryMain(vm: GalleryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showClean by rememberSaveable { mutableStateOf(false) }
    var expiredAsked by rememberSaveable { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var openAlbum by remember { mutableStateOf<AlbumRef?>(null) }
    var pickerItems by remember { mutableStateOf<List<MediaItem>?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onPermanentlyDeleted()
        } else {
            vm.pendingDelete = emptyList()
        }
    }

    // Hapus permanen: Android menampilkan dialog konfirmasi bawaan sistem.
    fun doDeleteForever(items: List<MediaItem>) {
        if (items.isEmpty()) return
        // Satu permintaan ke sistem dibatasi supaya tidak macet / error kalau jumlahnya ribuan.
        val batch = items.take(500)
        if (batch.size < items.size) {
            scope.launch {
                snackbar.showSnackbar("Menghapus ${batch.size} dari ${items.size}. Ulangi untuk sisanya.")
            }
        }
        vm.pendingDelete = batch.map { it.id }
        val request = MediaStore.createDeleteRequest(context.contentResolver, batch.map { it.uri })
        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
    }

    // Hapus sesuai pengaturan: ke sampah (bisa diurungkan) atau permanen.
    fun doDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (vm.settings.permanentDelete) {
            doDeleteForever(items)
        } else {
            vm.moveToTrash(items)
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = "${items.size} item dipindah ke sampah",
                    actionLabel = "Urungkan",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) vm.restore(items)
            }
        }
    }

    // ---- Pindah / salin / ganti nama ----
    var folderOp by remember { mutableStateOf<FolderOp?>(null) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var pendingWrite by remember { mutableStateOf<PendingWrite?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    val writeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val p = pendingWrite
        pendingWrite = null
        if (result.resultCode == Activity.RESULT_OK && p != null) {
            scope.launch {
                val newName = p.newName
                busy = if (newName != null) "Mengganti nama…" else "Memindahkan ${p.items.size} item…"
                val msg = withContext(Dispatchers.IO) {
                    if (newName != null) {
                        if (FileOps.rename(context, p.items.first(), newName)) {
                            "Nama file diganti"
                        } else {
                            "Gagal mengganti nama (mungkin nama itu sudah dipakai)"
                        }
                    } else {
                        val r = FileOps.move(context, p.items, p.dest ?: "")
                        "${r.ok} item dipindahkan" + if (r.failed > 0) ", ${r.failed} gagal" else ""
                    }
                }
                busy = null
                vm.refresh()
                snackbar.showSnackbar(msg)
            }
        }
    }

    // Pindah dan ganti nama butuh izin tulis dari sistem Android.
    fun requestWrite(items: List<MediaItem>, dest: String?, newName: String?) {
        if (items.isEmpty()) return
        val batch = items.take(500)
        pendingWrite = PendingWrite(batch, dest, newName)
        val pi = MediaStore.createWriteRequest(context.contentResolver, batch.map { it.uri })
        writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        if (batch.size < items.size) {
            scope.launch {
                snackbar.showSnackbar("Memproses ${batch.size} dari ${items.size}. Ulangi untuk sisanya.")
            }
        }
    }

    fun doCopy(items: List<MediaItem>, dest: String) {
        if (items.isEmpty()) return
        scope.launch {
            busy = "Menyalin ${items.size} item…"
            val r = withContext(Dispatchers.IO) { FileOps.copy(context, items, dest) }
            busy = null
            vm.refresh()
            snackbar.showSnackbar("${r.ok} item disalin" + if (r.failed > 0) ", ${r.failed} gagal" else "")
        }
    }

    val actions = GalleryActions(
        open = { items, index, trash -> viewer = ViewerRequest(items, index, trash) },
        delete = { doDelete(it) },
        deleteForever = { doDeleteForever(it) },
        addToAlbum = { pickerItems = it },
        move = { folderOp = FolderOp(it, true) },
        copy = { folderOp = FolderOp(it, false) },
        rename = { renameTarget = it },
    )

    BackHandler(enabled = showClean || openAlbum != null) {
        if (showClean) showClean = false else openAlbum = null
    }

    val barVisible = !showClean && openAlbum == null
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = if (barVisible) navInset + FloatingBarHeight else navInset

    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalBottomInset provides bottomInset) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                val album = openAlbum
                when {
                    showClean -> CleanScreen(vm, actions, onBack = { showClean = false })
                    album != null -> AlbumDetailScreen(
                        ref = album,
                        vm = vm,
                        actions = actions,
                        onBack = { openAlbum = null },
                        onRenamed = { openAlbum = AlbumRef(it, true) },
                    )
                    tab == 0 -> MediaScreen(
                        title = "Foto",
                        items = vm.visible,
                        vm = vm,
                        actions = actions,
                        subtitle = if (vm.loaded) "${vm.visible.size} item" else null,
                        emptyText = if (vm.loaded) "Belum ada foto atau video" else "Memuat…",
                    )
                    tab == 1 -> AlbumsScreen(vm) { openAlbum = it }
                    tab == 2 -> MediaScreen(
                        title = "Favorit",
                        items = vm.favoriteItems,
                        vm = vm,
                        actions = actions,
                        emptyText = "Belum ada favorit. Buka foto lalu ketuk ikon hati.",
                    )
                    tab == 3 -> MediaScreen(
                        title = "Sampah",
                        items = vm.trashItems,
                        vm = vm,
                        actions = actions,
                        trashMode = true,
                        subtitle = if (vm.trashItems.isNotEmpty()) {
                            "Item di sini hanya disembunyikan. Pulihkan atau hapus permanen."
                        } else {
                            null
                        },
                        emptyText = "Sampah kosong",
                        topActions = {
                            if (vm.trashItems.isNotEmpty()) {
                                TextButton(onClick = { doDeleteForever(vm.trashItems) }) {
                                    Text("Kosongkan")
                                }
                            }
                        },
                    )
                    else -> SettingsScreen(vm, onOpenClean = { showClean = true })
                }
            }
        }
      }

        if (barVisible) {
            FloatingNavBar(
                tabs = tabs,
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )

        viewer?.let { request ->
            ViewerScreen(
                req = request,
                vm = vm,
                actions = actions,
                onClose = { viewer = null },
            )
        }
    }

    pickerItems?.let { items ->
        AlbumPickerDialog(vm, items) { message ->
            pickerItems = null
            if (message != null) scope.launch { snackbar.showSnackbar(message) }
        }
    }

    folderOp?.let { op ->
        val targets = remember(vm.folders) {
            vm.folders.mapNotNull { (name, list) ->
                val path = list.firstOrNull()?.relativePath.orEmpty()
                if (path.startsWith("DCIM/") || path.startsWith("Pictures/") || path.startsWith("Movies/")) {
                    FolderTarget(name, path)
                } else {
                    null
                }
            }.distinctBy { it.path }
        }
        FolderPickerDialog(
            title = if (op.move) "Pindahkan ke folder" else "Salin ke folder",
            targets = targets,
            onPick = { path ->
                folderOp = null
                if (op.move) requestWrite(op.items, path, null) else doCopy(op.items, path)
            },
            onDismiss = { folderOp = null },
        )
    }

    renameTarget?.let { item ->
        val dot = item.name.lastIndexOf('.')
        val base = if (dot > 0) item.name.substring(0, dot) else item.name
        val ext = if (dot > 0) item.name.substring(dot) else ""
        NameDialog(
            title = "Ganti nama",
            initial = base,
            confirmText = "Ganti",
            label = "Nama file",
            onConfirm = { n ->
                renameTarget = null
                val clean = FileOps.cleanName(n)
                if (clean.isNotBlank()) requestWrite(listOf(item), null, clean + ext)
            },
            onDismiss = { renameTarget = null },
        )
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(msg) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
        )
    }

    // Tawarkan hapus permanen untuk item sampah yang sudah melewati batas hari.
    val expired = remember(vm.trashItems, vm.settings.trashDays) { vm.expiredTrash() }
    if (vm.loaded && !expiredAsked && expired.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { expiredAsked = true },
            title = { Text("Bersihkan sampah?") },
            text = {
                Text(
                    "${expired.size} item sudah lebih dari ${vm.settings.trashDays} hari di sampah. " +
                        "Hapus permanen sekarang? Android akan meminta konfirmasi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    expiredAsked = true
                    doDeleteForever(expired)
                }) { Text("Hapus permanen") }
            },
            dismissButton = {
                TextButton(onClick = { expiredAsked = true }) { Text("Nanti saja") }
            },
        )
    }
}
