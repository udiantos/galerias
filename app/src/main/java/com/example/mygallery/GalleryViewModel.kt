package com.example.mygallery

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mygallery.data.AppSettings
import com.example.mygallery.data.LocalStore
import com.example.mygallery.data.MediaItem
import com.example.mygallery.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private class Derived(
        val visible: List<MediaItem>,
        val favorites: List<MediaItem>,
        val trash: List<MediaItem>,
        val folders: List<Pair<String, List<MediaItem>>>,
    )

    private val repo = MediaRepository(app)
    private val store = LocalStore(app)

    // Penulisan ke penyimpanan dilakukan berurutan di thread lain, tidak di thread utama.
    private val ioSerial = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var rebuildJob: Job? = null
    private var refreshing = false

    var media by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var favorites by mutableStateOf<Set<Long>>(store.loadFavorites())
        private set
    var albums by mutableStateOf<Map<String, List<Long>>>(store.loadAlbums())
        private set
    var trash by mutableStateOf<Map<Long, Long>>(store.loadTrash())
        private set
    var settings by mutableStateOf(store.loadSettings())
        private set

    // Daftar turunan, dihitung di thread latar lalu dipasang sekaligus.
    var visible by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var favoriteItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var trashItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var folders by mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList())
        private set

    /** id yang sudah dihapus permanen pada sesi ini (dipakai viewer supaya langsung hilang). */
    var gone by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Antrean hapus permanen per potongan, karena Android membatasi jumlah file per permintaan. */
    var deleteQueue: List<List<MediaItem>> = emptyList()
    var pendingDelete: List<Long> = emptyList()

    // ---------------- Memuat ----------------

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.load() }
                val old = media
                val same = loaded && withContext(Dispatchers.Default) { list == old }
                if (!same) {
                    val ids = withContext(Dispatchers.Default) {
                        val s = HashSet<Long>(list.size * 2)
                        for (m in list) s.add(m.id)
                        s
                    }
                    media = list
                    loaded = true
                    if (list.isNotEmpty()) prune(ids)
                    rebuild()
                }
            } finally {
                refreshing = false
            }
        }
    }

    /** Buang jejak id yang filenya sudah tidak ada lagi. */
    private fun prune(ids: Set<Long>) {
        val f = favorites.filter { it in ids }.toSet()
        if (f.size != favorites.size) {
            favorites = f
            persistFavorites()
        }
        val t = trash.filterKeys { it in ids }
        if (t.size != trash.size) {
            trash = t
            persistTrash()
        }
        val a = albums.mapValues { (_, v) -> v.filter { it in ids } }
        if (a != albums) {
            albums = a
            persistAlbums()
        }
    }

    private fun rebuild() {
        val m = media
        val t = trash
        val f = favorites
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val d = withContext(Dispatchers.Default) {
                val vis = m.filter { it.id !in t }
                Derived(
                    visible = vis,
                    favorites = vis.filter { it.id in f },
                    trash = m.filter { it.id in t }.sortedByDescending { t[it.id] ?: 0L },
                    folders = vis
                        .groupBy { it.folder.ifBlank { "Lainnya" } }
                        .toList()
                        .sortedByDescending { it.second.size },
                )
            }
            visible = d.visible
            favoriteItems = d.favorites
            trashItems = d.trash
            folders = d.folders
        }
    }

    // ---------------- Penyimpanan (di thread latar) ----------------

    private fun persistFavorites() {
        val v = favorites
        viewModelScope.launch(ioSerial) { store.saveFavorites(v) }
    }

    private fun persistAlbums() {
        val v = albums
        viewModelScope.launch(ioSerial) { store.saveAlbums(v) }
    }

    private fun persistTrash() {
        val v = trash
        viewModelScope.launch(ioSerial) { store.saveTrash(v) }
    }

    // ---------------- Favorit ----------------

    fun toggleFavorite(item: MediaItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        persistFavorites()
        rebuild()
    }

    fun setFavorite(items: List<MediaItem>, value: Boolean) {
        val ids = items.map { it.id }
        favorites = if (value) favorites + ids else favorites - ids.toSet()
        persistFavorites()
        rebuild()
    }

    // ---------------- Album ----------------

    fun albumItems(name: String): List<MediaItem> {
        val ids = albums[name]?.toSet() ?: return emptyList()
        return visible.filter { it.id in ids }
    }

    fun folderItems(name: String): List<MediaItem> =
        visible.filter { it.folder.ifBlank { "Lainnya" } == name }

    fun createAlbum(name: String): Boolean {
        if (name.isBlank() || name in albums) return false
        albums = albums + (name to emptyList<Long>())
        persistAlbums()
        return true
    }

    fun addToAlbum(name: String, items: List<MediaItem>) {
        val current = albums[name] ?: emptyList()
        albums = albums + (name to (current + items.map { it.id }).distinct())
        persistAlbums()
    }

    fun removeFromAlbum(name: String, items: List<MediaItem>) {
        val remove = items.map { it.id }.toSet()
        val current = albums[name] ?: return
        albums = albums + (name to current.filter { it !in remove })
        persistAlbums()
    }

    fun renameAlbum(old: String, new: String): Boolean {
        if (new.isBlank()) return false
        if (new == old) return true
        if (new in albums) return false
        albums = albums.entries.associate { (if (it.key == old) new else it.key) to it.value }
        persistAlbums()
        return true
    }

    fun deleteAlbum(name: String) {
        albums = albums - name
        persistAlbums()
    }

    // ---------------- Sampah ----------------

    fun moveToTrash(items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        val add = HashMap<Long, Long>(items.size * 2)
        for (i in items) add[i.id] = now
        trash = trash + add
        persistTrash()
        rebuild()
    }

    fun restore(items: List<MediaItem>) {
        trash = trash - items.map { it.id }.toSet()
        persistTrash()
        rebuild()
    }

    /** Item di sampah yang sudah melewati batas hari pada pengaturan. */
    fun expiredTrash(): List<MediaItem> {
        val days = settings.trashDays
        if (days <= 0) return emptyList()
        val limit = System.currentTimeMillis() - days * 86_400_000L
        return trashItems.filter { (trash[it.id] ?: Long.MAX_VALUE) < limit }
    }

    /** Dipanggil setelah pengguna menyetujui dialog hapus permanen dari Android. */
    fun onPermanentlyDeleted() {
        val goneNow = pendingDelete.toSet()
        pendingDelete = emptyList()
        if (goneNow.isEmpty()) return

        gone = gone + goneNow
        media = media.filter { it.id !in goneNow }
        trash = trash - goneNow
        favorites = favorites - goneNow
        albums = albums.mapValues { (_, v) -> v.filter { it !in goneNow } }
        persistTrash()
        persistFavorites()
        persistAlbums()
        rebuild()
    }

    // ---------------- Duplikat ----------------

    /** Kelompok file yang kemungkinan sama (ukuran + resolusi + durasi identik). Berat: panggil di thread latar. */
    fun findDuplicates(source: List<MediaItem>): List<List<MediaItem>> =
        source
            .filter { it.size > 0 }
            .groupBy { "${it.isVideo}|${it.size}|${it.width}|${it.height}|${it.durationMs}" }
            .values
            .filter { it.size > 1 }
            .map { group -> group.sortedBy { it.takenMs } }
            .sortedByDescending { group -> (group.size - 1) * group[0].size }

    // ---------------- Pengaturan ----------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        store.saveSettings(settings)
    }
}
