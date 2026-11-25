package com.example.mygallery.data

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Size as AndroidSize
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Model untuk thumbnail grid. Memakai thumbnail yang sudah di-cache sistem Android (jauh lebih cepat dari decode file asli). */
data class ThumbModel(val id: Long, val uri: Uri, val px: Int)

class ThumbFetcher(
    private val context: Context,
    private val model: ThumbModel,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            val bmp = context.contentResolver.loadThumbnail(
                model.uri,
                AndroidSize(model.px, model.px),
                null,
            )
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bmp),
                isSampled = true,
                dataSource = DataSource.DISK,
            )
        } catch (e: Exception) {
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<ThumbModel> {
        override fun create(data: ThumbModel, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(context, data)
    }
}

/** Permintaan thumbnail dengan kunci cache memori yang stabil. */
fun thumbRequest(context: Context, item: MediaItem, px: Int = 256): ImageRequest =
    ImageRequest.Builder(context)
        .data(ThumbModel(item.id, item.uri, px))
        .memoryCacheKey("thumb:${item.id}:$px")
        .build()
