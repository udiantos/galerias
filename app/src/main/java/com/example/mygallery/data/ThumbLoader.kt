package com.example.mygallery.data

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Memuat thumbnail kecil dari cache thumbnail sistem (MediaStore), bukan dari file aslinya.
 * Hanya 3 thread yang bekerja, dan permintaan untuk item yang sudah keluar layar dibatalkan
 * sebelum sempat diproses, jadi scroll cepat tidak menumpuk antrean.
 */
object ThumbLoader {
    private val cache = object : LruCache<Long, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt()
    ) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    private val pool = Executors.newFixedThreadPool(3).asCoroutineDispatcher()

    fun peek(id: Long): Bitmap? = cache.get(id)

    suspend fun load(ctx: Context, item: MediaItem): Bitmap? {
        cache.get(item.id)?.let { return it }
        val bmp = withContext(pool) {
            try {
                ctx.contentResolver.loadThumbnail(item.uri, Size(320, 320), null)
            } catch (e: Exception) {
                null
            }
        }
        if (bmp != null) cache.put(item.id, bmp)
        return bmp
    }
}
