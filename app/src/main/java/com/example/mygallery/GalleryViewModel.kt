package com.example.mygallery

import android.app.Application
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val store = LocalStore(app)

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
        media.filter { it.id in trash }.sortedByDescending { trash[it.id] ?: 0L }
    }

    val folders: List<Pair<String, List<MediaItem>>> by derivedStateOf {
        visible
            .groupBy { it.folder.ifBlank { "Lainnya" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    // ---------------- Memuat ----------------

    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.load() }
            media = list
            loaded = true
            prune(list)
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
            store.saveFavorites(f)
        }
        val t = trash.filterKeys { it in ids }
        if (t.size != trash.size) {
            trash = t
            store.saveTrash(t)
        }
        val a = albums.mapValues { (_, v) -> v.filter { it in ids } }
        if (a != albums) {
            albums = a
            store.saveAlbums(a)
        }
    }

    // ---------------- Favorit ----------------

    fun toggleFavorite(item: MediaItem) {
        favorites = if (item.id in favorites) favorites - item.id else favorites + item.id
        store.saveFavorites(favorites)
    }

    fun setFavorite(items: List<MediaItem>, value: Boolean) {
        val ids = items.map { it.id }
        favorites = if (value) favorites + ids else favorites - ids.toSet()
        store.saveFavorites(favorites)
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
        store.saveAlbums(albums)
        return true
    }

    fun addToAlbum(name: String, items: List<MediaItem>) {
        val current = albums[name] ?: emptyList()
        albums = albums + (name to (current + items.map { it.id }).distinct())
        store.saveAlbums(albums)
    }

    fun removeFromAlbum(name: String, items: List<MediaItem>) {
        val remove = items.map { it.id }.toSet()
        val current = albums[name] ?: return
        albums = albums + (name to current.filter { it !in remove })
        store.saveAlbums(albums)
    }

    fun renameAlbum(old: String, new: String): Boolean {
        if (new.isBlank()) return false
        if (new == old) return true
        if (new in albums) return false
        albums = albums.entries.associate { (if (it.key == old) new else it.key) to it.value }
        store.saveAlbums(albums)
        return true
    }

    fun deleteAlbum(name: String) {
        albums = albums - name
        store.saveAlbums(albums)
    }

    // ---------------- Sampah ----------------

    fun moveToTrash(items: List<MediaItem>) {
        val now = System.currentTimeMillis()
        trash = trash + items.associate { it.id to now }
        store.saveTrash(trash)
    }

    fun restore(items: List<MediaItem>) {
        trash = trash - items.map { it.id }.toSet()
        store.saveTrash(trash)
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
        store.saveTrash(trash)
        store.saveFavorites(favorites)
        store.saveAlbums(albums)
        refresh()
    }

    // ---------------- Duplikat ----------------

    /** Kelompok file yang kemungkinan sama (ukuran + resolusi + durasi identik). */
    fun findDuplicates(): List<List<MediaItem>> =
        visible
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
