package com.alpa.galeriku.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.IOException

/** Operasi file: pindah, salin, ganti nama (lewat MediaStore, tanpa izin semua file). */
object FileOps {

    class Result(val ok: Int, val failed: Int)

    private const val TAG = "FileOps"

    fun cleanName(s: String): String = s.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()

    private fun norm(path: String): String = path.trim().trim('/') + "/"

    /** Folder tujuan yang dicoba berurutan (Pictures dan Movies saling menggantikan bila ditolak). */
    private fun candidates(dest: String): List<String> {
        val d = norm(dest)
        val alt = when {
            d.startsWith("Pictures/") -> "Movies/" + d.removePrefix("Pictures/")
            d.startsWith("Movies/") -> "Pictures/" + d.removePrefix("Movies/")
            else -> null
        }
        return listOfNotNull(d, alt)
    }

    /** Pindah. Pemanggil harus sudah mendapat izin tulis (createWriteRequest). */
    fun move(ctx: Context, items: List<MediaItem>, dest: String): Result {
        val resolver = ctx.contentResolver
        var ok = 0
        var failed = 0
        for (item in items) {
            if (item.relativePath == norm(dest)) {
                ok++
                continue
            }
            var done = false
            for (cand in candidates(dest)) {
                try {
                    val v = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, cand)
                    }
                    if (resolver.update(item.uri, v, null, null) > 0) {
                        done = true
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal memindah ke $cand", e)
                }
            }
            if (done) ok++ else failed++
        }
        return Result(ok, failed)
    }

    /** Salin ke folder baru. Tidak butuh izin tambahan. */
    fun copy(ctx: Context, items: List<MediaItem>, dest: String): Result {
        val resolver = ctx.contentResolver
        var ok = 0
        var failed = 0
        for (item in items) {
            val base = if (item.isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val mime = resolver.getType(item.uri)
                ?: if (item.isVideo) "video/mp4" else "image/jpeg"
            var done = false
            for (cand in candidates(dest)) {
                var created: Uri? = null
                try {
                    val v = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, cand)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        if (item.takenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, item.takenMs)
                    }
                    val target = resolver.insert(base, v) ?: throw IOException("insert gagal")
                    created = target
                    val input = resolver.openInputStream(item.uri) ?: throw IOException("tidak bisa membaca")
                    val output = resolver.openOutputStream(target) ?: throw IOException("tidak bisa menulis")
                    input.use { i -> output.use { o -> i.copyTo(o) } }
                    val finish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    resolver.update(target, finish, null, null)
                    done = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menyalin ke $cand", e)
                    val c = created
                    if (c != null) {
                        try {
                            resolver.delete(c, null, null)
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
            if (done) ok++ else failed++
        }
        return Result(ok, failed)
    }

    /** Ganti nama. Pemanggil harus sudah mendapat izin tulis. */
    fun rename(ctx: Context, item: MediaItem, newName: String): Boolean = try {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        ctx.contentResolver.update(item.uri, v, null, null) > 0
    } catch (e: Exception) {
        Log.w(TAG, "Gagal ganti nama", e)
        false
    }
}
