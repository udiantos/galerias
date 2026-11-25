package com.example.mygallery.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val takenMs: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val folder: String,
)

data class AppSettings(
    val permanentDelete: Boolean = false,
    val trashDays: Int = 30,
    val columns: Int = 3,
    /** 0 = ikut sistem, 1 = terang, 2 = gelap */
    val theme: Int = 0,
)
