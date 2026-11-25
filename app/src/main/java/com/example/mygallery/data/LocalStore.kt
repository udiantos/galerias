package com.example.mygallery.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Penyimpanan lokal sederhana: favorit, album, sampah, dan pengaturan. */
class LocalStore(context: Context) {
    private val sp = context.getSharedPreferences("gallery_store", Context.MODE_PRIVATE)

    // ---- Favorit ----
    fun loadFavorites(): Set<Long> =
        (sp.getStringSet("favorites", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun saveFavorites(value: Set<Long>) {
        sp.edit().putStringSet("favorites", value.map { it.toString() }.toSet()).apply()
    }

    // ---- Album buatan sendiri (nama -> daftar id media) ----
    fun loadAlbums(): Map<String, List<Long>> {
        val out = LinkedHashMap<String, List<Long>>()
        try {
            val obj = JSONObject(sp.getString("albums", "{}") ?: "{}")
            for (key in obj.keys()) {
                val arr = obj.getJSONArray(key)
                out[key] = List(arr.length()) { arr.getLong(it) }
            }
        } catch (e: Exception) {
            Log.w("LocalStore", "Gagal membaca album", e)
        }
        return out
    }

    fun saveAlbums(value: Map<String, List<Long>>) {
        val obj = JSONObject()
        for ((name, ids) in value) obj.put(name, JSONArray(ids))
        sp.edit().putString("albums", obj.toString()).apply()
    }

    // ---- Sampah (id media -> waktu dibuang, ms) ----
    fun loadTrash(): Map<Long, Long> {
        val out = LinkedHashMap<Long, Long>()
        try {
            val obj = JSONObject(sp.getString("trash", "{}") ?: "{}")
            for (key in obj.keys()) {
                val id = key.toLongOrNull() ?: continue
                out[id] = obj.getLong(key)
            }
        } catch (e: Exception) {
            Log.w("LocalStore", "Gagal membaca sampah", e)
        }
        return out
    }

    fun saveTrash(value: Map<Long, Long>) {
        val obj = JSONObject()
        for ((id, time) in value) obj.put(id.toString(), time)
        sp.edit().putString("trash", obj.toString()).apply()
    }

    // ---- Pengaturan ----
    fun loadSettings(): AppSettings = AppSettings(
        permanentDelete = sp.getBoolean("perm_delete", false),
        trashDays = sp.getInt("trash_days", 30),
        columns = sp.getInt("columns", 3),
        theme = sp.getInt("theme", 0),
    )

    fun saveSettings(s: AppSettings) {
        sp.edit()
            .putBoolean("perm_delete", s.permanentDelete)
            .putInt("trash_days", s.trashDays)
            .putInt("columns", s.columns)
            .putInt("theme", s.theme)
            .apply()
    }
}
