package com.example.mygallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log

class MediaRepository(private val context: Context) {

    fun load(): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            val args = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            )

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args,
                null,
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iRel = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded)
                    val base = if (video) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    out.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id),
                            name = c.getString(iName) ?: "",
                            isVideo = video,
                            takenMs = if (taken > 0) taken else added * 1000L,
                            size = c.getLong(iSize),
                            width = c.getInt(iW),
                            height = c.getInt(iH),
                            durationMs = if (video) c.getLong(iDur) else 0L,
                            folder = c.getString(iBucket) ?: "",
                            relativePath = c.getString(iRel) ?: "",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("MediaRepository", "Gagal memuat media", e)
        }
        out.sortByDescending { it.takenMs }
        return out
    }
}
