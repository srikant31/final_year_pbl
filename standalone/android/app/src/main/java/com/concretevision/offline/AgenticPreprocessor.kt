package com.concretevision.offline

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pillow's separable BICUBIC filter, including its downsampling support and 22-bit rounding.
 * Bitmap.createScaledBitmap uses a different downsampling filter and changes the model's inputs.
 * Alpha is discarded, as in PIL.Image.convert("RGB"); EXIF is intentionally not applied.
 */
object AgenticPreprocessor {
    private fun cubic(x: Double): Double = when { x < 1 -> ((1.5*x-2.5)*x)*x+1; x < 2 -> (((-0.5*x+2.5)*x-4)*x)+2; else -> 0.0 }
    private const val PRECISION = 22
    private const val ROUNDING = 1 shl (PRECISION - 1)
    private data class Filter(val start: Int, val weights: IntArray)

    private val filterCache = object : LinkedHashMap<Pair<Int,Int>,List<Filter>>(4,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int,Int>,List<Filter>>?) = size > 4
    }
    @Synchronized private fun filters(input: Int, output: Int): List<Filter> {
        filterCache[input to output]?.let { return it }
        val scale = input.toDouble() / output
        val filterScale = max(scale, 1.0)
        val support = 2.0 * filterScale
        return List(output) { x ->
            val center = (x + 0.5) * scale
            val start = max((center - support + 0.5).toInt(), 0)
            val end = min((center + support + 0.5).toInt(), input)
            val weights = DoubleArray(end - start) { i ->
                cubic(kotlin.math.abs((i + start - center + 0.5) / filterScale))
            }
            val sum = weights.sum()
            Filter(start, IntArray(weights.size) { i ->
                (weights[i] / sum * (1 shl PRECISION)).let { (it + if(it < 0) -0.5 else 0.5).toInt() }
            })
        }.also { filterCache[input to output] = it }
    }

    fun resizedRgb(bitmap: Bitmap, size: Int = 224): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val horizontal = IntArray(size * height)
        if (width == size) pixels.copyInto(horizontal) else {
            val filter = filters(width, size)
            for (y in 0 until height) for (x in 0 until size) {
                val f = filter[x]
                val weights = f.weights
                val rowOffset = y * width + f.start
                var red = ROUNDING
                var green = ROUNDING
                var blue = ROUNDING
                for (k in weights.indices) {
                    val p = pixels[rowOffset + k]
                    val w = weights[k]
                    red += ((p ushr 16) and 255) * w
                    green += ((p ushr 8) and 255) * w
                    blue += (p and 255) * w
                }
                horizontal[y * size + x] = (255 shl 24) or
                    ((red shr PRECISION).coerceIn(0,255) shl 16) or
                    ((green shr PRECISION).coerceIn(0,255) shl 8) or
                    (blue shr PRECISION).coerceIn(0,255)
            }
        }
        if (height == size) return horizontal
        val result = IntArray(size * size)
        val filter = filters(height, size)
        for (y in 0 until size) for (x in 0 until size) {
            val f = filter[y]
            val weights = f.weights
            val sourceOffset = f.start * size + x
            var red = ROUNDING
            var green = ROUNDING
            var blue = ROUNDING
            for (k in weights.indices) {
                val p = horizontal[sourceOffset + k * size]
                val w = weights[k]
                red += ((p ushr 16) and 255) * w
                green += ((p ushr 8) and 255) * w
                blue += (p and 255) * w
            }
            result[y * size + x] = (255 shl 24) or
                ((red shr PRECISION).coerceIn(0,255) shl 16) or
                ((green shr PRECISION).coerceIn(0,255) shl 8) or
                (blue shr PRECISION).coerceIn(0,255)
        }
        return result
    }

    fun tensor(bitmap: Bitmap, size: Int, mean: FloatArray, std: FloatArray): FloatArray {
        val rgb = resizedRgb(bitmap, size)
        val plane = size * size
        return FloatArray(plane * 3).also { tensor ->
            for (i in rgb.indices) {
                val p = rgb[i]
                tensor[i] = (((p ushr 16) and 255) / 255f - mean[0]) / std[0]
                tensor[plane + i] = (((p ushr 8) and 255) / 255f - mean[1]) / std[1]
                tensor[2 * plane + i] = ((p and 255) / 255f - mean[2]) / std[2]
            }
        }
    }
}
