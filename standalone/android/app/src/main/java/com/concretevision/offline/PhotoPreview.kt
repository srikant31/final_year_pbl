package com.concretevision.offline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** EXIF correction is DISPLAY ONLY. Original decoded pixels still feed the unchanged model. */
object PhotoPreview {
    fun create(context: Context, uri: Uri, original: Bitmap): Bitmap {
        val scale = minOf(1.0,1024.0/maxOf(original.width,original.height))
        val thumbnail = Bitmap.createScaledBitmap(original,
            maxOf(1,(original.width*scale).toInt()),maxOf(1,(original.height*scale).toInt()),true)
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f,1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f,-1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(90f); matrix.postScale(1f,-1f) }
            8 -> matrix.setRotate(270f)
            else -> return thumbnail
        }
        return Bitmap.createBitmap(thumbnail,0,0,thumbnail.width,thumbnail.height,matrix,true).also {
            if (thumbnail !== original && thumbnail !== it) thumbnail.recycle()
        }
    }
}
