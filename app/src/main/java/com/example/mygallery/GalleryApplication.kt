package com.example.mygallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.example.mygallery.data.ThumbFetcher

class GalleryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ThumbFetcher.Factory(this@GalleryApplication)) }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .crossfade(false)
            .build()
}
