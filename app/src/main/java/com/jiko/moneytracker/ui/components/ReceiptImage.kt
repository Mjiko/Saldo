package com.jiko.moneytracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.jiko.moneytracker.ui.theme.saldoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a downsampled bitmap from an app-private file path (receipt photos). */
@Composable
fun ReceiptImage(
    path: String,
    modifier: Modifier = Modifier,
    maxDim: Int = 1024,
    contentScale: ContentScale = ContentScale.Fit
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, maxDim) }
    }
    val pal = saldoColors
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Foto struk",
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(pal.surface3))
    }
}

private fun decodeSampled(path: String, maxDim: Int): Bitmap? = try {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (_: Exception) {
    null
}
