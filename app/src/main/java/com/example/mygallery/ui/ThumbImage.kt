package com.example.mygallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.mygallery.data.MediaItem
import com.example.mygallery.data.ThumbLoader

@Composable
fun ThumbImage(item: MediaItem, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current.applicationContext
    var bitmap by remember(item.id) { mutableStateOf(ThumbLoader.peek(item.id)) }

    LaunchedEffect(item.id) {
        if (bitmap == null) bitmap = ThumbLoader.load(ctx, item)
    }

    val bmp = bitmap
    if (bmp != null) {
        val image = remember(bmp) { bmp.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
