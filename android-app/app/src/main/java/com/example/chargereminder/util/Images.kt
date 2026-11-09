package com.example.chargereminder.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 图片都复制到 filesDir/photos 下，只存文件名，
 * 这样不用长期持有相册 URI 权限，备份/恢复也简单。
 */
object Images {

    private fun dir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    fun saveUri(context: Context, uri: Uri, maxSide: Int, square: Boolean): String? {
        val bitmap = runCatching { decodeSampled(context, uri, maxSide) }.getOrNull() ?: return null
        return saveBitmap(context, bitmap, maxSide, square)
    }

    fun saveBitmap(context: Context, source: Bitmap, maxSide: Int, square: Boolean): String? {
        val out = if (square) centerSquare(source) else scaleToMax(source, maxSide)
        val name = "img_${System.currentTimeMillis()}.jpg"
        val file = File(dir(context), name)
        return runCatching {
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        }.getOrNull()
    }

    fun load(context: Context, name: String?, maxSide: Int): Bitmap? {
        if (name.isNullOrBlank()) return null
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        }.getOrNull()
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { File(dir(context), name).delete() }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private fun centerSquare(src: Bitmap): Bitmap {
        val side = min(src.width, src.height)
        if (side == src.width && side == src.height) return src
        return Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    }

    private fun scaleToMax(src: Bitmap, maxSide: Int): Bitmap {
        val largest = max(src.width, src.height)
        if (largest <= maxSide) return src
        val scale = maxSide.toFloat() / largest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
