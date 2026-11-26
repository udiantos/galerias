package com.alpa.galeriku

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alpa.galeriku.data.AppSettings
import com.alpa.galeriku.data.LocalStore
import com.alpa.galeriku.data.MediaItem
import com.alpa.galeriku.data.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val store = LocalStore(app)

    // Satu thread khusus untuk menulis ke penyimpanan, supaya UI tidak ikut tertahan.
    private val ioScope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private fun persist(block: () -> Unit) { ioScope.launch { block() } }
    private var refreshJob: Job? = null

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

    /** id media yang sedang menunggu konfirmasi hapus permanen dari sistem Android. */
    var pendingDelete: List<Long> = emptyList()

    val visible: List<MediaItem> by derivedStateOf {
        media.filter { it.id !in trash }
    }

    val favoriteItems: List<MediaItem> by derivedStateOf {
        visible.filter { it.id in favorites }
    }

    val trashItems: List<MediaItem> by derivedStateOf {
        if (trash.isEmpty()) return@derivedStateOf emptyList<MediaItem>()
        media.filter { it.id in trash }.sortedByDescending { trash[it.id] ?: 0L }
    }

    val folders: List<Pair<String, List<MediaItem>>> by derivedStateOf {
        visible
            .groupBy { it.folder.ifBlank { "Lainnya" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    // ---------------- Memuat ----------------

    /** True hanya saat pengguna menarik layar untuk refresh (supaya indikator tidak muncul di refresh otomatis). */
    var refreshing by mutableStateOf(false)
        private set
    private var refreshGen = 0

    fun refresh(manual: Boolean = false) {
        refreshJob?.cancel()
        val gen = ++refreshGen
        if (manual) refreshing = true
        refreshJob = viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { repo.load() }
                media = list
                loaded = true
                prune(list)
            } finally {
                if (gen == refreshGen) refreshing = false
            }
        }
    }

    /** Buang jejak id yang filenya sudah tidak ada lagi. */
    private fun prune(list: List<MediaItem>) {
        if (list.isEmpty()) return
        val ids = HashSet<Long>(list.size * 2)
        for (m in list) ids.add(m.id)

        val f = favorites.filter { it in ids }.toSet()
        if (f.size != favorites.size) {
            favorites = f
            run { val v = f; persist { store.saveFavorites(v) } }
        }
        val t = trash.filterKeys { it in ids }
        if (t.size != trash.size) {
            trash = t
            run { val v = t; persist { store.saveTrash(v) } }
        }
        val a = albums.mapValues { (_, v) -> v.filter { it in ids } }
        if (a != albums) {
            albums = a
            run { val v = a; persist { store.saveAlbums(v) } }
        }
    }

    // ---------------- Favorit ----------------

    fun toggleFavorite(item: MediaItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        run { val v = favorites; persist { store.saveFavorites(v) } }
    }

    fun setFavorite(items: List<MediaItem>, value: Boolean) {
        val ids = items.map { it.id }
        favorites = if (value) favorites + ids else favorites - ids.toSet()
        run { val v = favorites; persist { store.saveFavorites(v) } }
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
        run { val v = albums; persist { store.saveAlbums(v) } }
        return true
    }

    fun addToAlbum(name: String, items: List<MediaItem>) {
        val current = albums[name] ?: emptyList()
        albums = albums + (name to (current + items.map { it.id }).distinct())
        run { val v = albums; persist { store.saveAlbums(v) } }
    }

    fun removeFromAlbum(name: String, items: List<MediaItem>) {
        val remove = items.map { it.id }.toSet()
        val current = albums[name] ?: return
        albums = albums + (name to current.filter { it !in remove })
        run { val v = albums; persist { store.saveAlbums(v) } }
    }

    fun renameAlbum(old: String, new: String): Boolean {
        if (new.isBlank()) return false
        if (new == old) return true
        if (new in albums) return false
        albums = albums.entries.associate { (if (it.key == old) new else it.key) to it.value }
        run { val v = albums; persist { store.saveAlbums(v) } }
        return true
    }

    fun deleteAlbum(name: String) {
        albums = albums - name
        run { val v = albums; persist { store.saveAlbums(v) } }
    }

    // ---------------- Sampah ----------------

    fun moveToTrash(items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        trash = trash + items.associate { it.id to now }
        run { val v = trash; persist { store.saveTrash(v) } }
    }

    fun restore(items: List<MediaItem>) {
        trash = trash - items.map { it.id }.toSet()
        run { val v = trash; persist { store.saveTrash(v) } }
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
        val gone = pendingDelete.toSet()
        pendingDelete = emptyList()
        if (gone.isEmpty()) return

        media = media.filter { it.id !in gone }
        trash = trash - gone
        favorites = favorites - gone
        albums = albums.mapValues { (_, v) -> v.filter { it !in gone } }
        run { val v = trash; persist { store.saveTrash(v) } }
        run { val v = favorites; persist { store.saveFavorites(v) } }
        run { val v = albums; persist { store.saveAlbums(v) } }
        refresh()
    }

    // ---------------- Pengaturan ----------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        run { val v = settings; persist { store.saveSettings(v) } }
    }
}
