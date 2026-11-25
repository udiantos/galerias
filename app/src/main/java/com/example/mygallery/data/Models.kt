package com.example.mygallery.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
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
    /** Contoh: "Pictures/Screenshots/" */
    val relativePath: String = "",
)

data class AppSettings(
    val permanentDelete: Boolean = false,
    val trashDays: Int = 30,
    val columns: Int = 3,
    /** 0 = ikut sistem, 1 = terang, 2 = gelap, 3 = AMOLED (hitam pekat) */
    val theme: Int = 0,
    /** 0 = tanggal, 1 = nama, 2 = ukuran */
    val sortBy: Int = 0,
    val sortAsc: Boolean = false,
    /** Latar penampil foto: 0 = hitam, 1 = putih */
    val viewerBg: Int = 0,
)
