package com.concretevision.offline

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

object ImageLoader {
    const val MAX_BYTES = 15 * 1024 * 1024
    private const val MAX_PIXELS = 40_000_000L

    fun decode(input: InputStream): Bitmap {
        val buffer = ByteArray(64 * 1024)
        val bytes = java.io.ByteArrayOutputStream().use { output ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size().toLong() + n <= MAX_BYTES) { "Choose an image smaller than 15 MiB." }
                output.write(buffer,0,n)
            }
            output.toByteArray()
        }
        require(bytes.isNotEmpty()) { "The selected file is empty." }
        // Pillow's convert("RGB") keeps raw RGB values and does not apply embedded ICC profiles.
        // Skia otherwise color-converts some phone JPEGs/PNGs, changing the teacher model's input.
        val rawRgbBytes = withoutColorProfiles(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawRgbBytes,0,rawRgbBytes.size,bounds)
        require(bounds.outMimeType in listOf("image/jpeg", "image/png", "image/bmp", "image/x-ms-bmp")) {
            "Choose a JPEG, PNG or BMP image."
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0 &&
            bounds.outWidth.toLong() * bounds.outHeight <= MAX_PIXELS &&
            bounds.outWidth <= 16384 && bounds.outHeight <= 16384) {
            "This image is too large to decode safely. Choose a smaller photograph."
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
            inScaled = false
        }
        return requireNotNull(BitmapFactory.decodeByteArray(rawRgbBytes,0,rawRgbBytes.size,options)) {
            "The image could not be decoded. Choose another photograph."
        }.apply { setHasAlpha(false) }
    }

    private fun withoutColorProfiles(bytes: ByteArray): ByteArray {
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) {
            val output = java.io.ByteArrayOutputStream(bytes.size)
            output.write(bytes,0,8)
            var offset = 8
            val colorChunks = setOf("iCCP","sRGB","gAMA","cHRM","cICP","mDCV","cLLI")
            while (offset + 12 <= bytes.size) {
                val length = ((bytes[offset].toInt() and 255) shl 24) or
                    ((bytes[offset+1].toInt() and 255) shl 16) or
                    ((bytes[offset+2].toInt() and 255) shl 8) or (bytes[offset+3].toInt() and 255)
                if (length < 0 || offset.toLong()+length+12 > bytes.size) return bytes
                val type = String(bytes,offset+4,4,Charsets.US_ASCII)
                if (type !in colorChunks) output.write(bytes,offset,length+12)
                offset += length + 12
            }
            if (offset != bytes.size) return bytes
            return output.toByteArray()
        }
        if (bytes.size >= 4 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) {
            val output = java.io.ByteArrayOutputStream(bytes.size)
            output.write(bytes,0,2)
            var offset = 2
            while (offset + 4 <= bytes.size) {
                if (bytes[offset] != 0xff.toByte()) return bytes
                val marker = bytes[offset+1].toInt() and 255
                if (marker == 0xda || marker == 0xd9) {
                    output.write(bytes,offset,bytes.size-offset)
                    return output.toByteArray()
                }
                // Standalone/fill markers need no segment length.
                if (marker == 0xff) {
                    output.write(bytes[offset].toInt()); offset++; continue
                }
                if (marker == 0x00 || marker in 0xd0..0xd8 || marker == 0x01) {
                    output.write(bytes,offset,2); offset += 2; continue
                }
                val length = ((bytes[offset+2].toInt() and 255) shl 8) or (bytes[offset+3].toInt() and 255)
                if (length < 2 || offset.toLong()+length+2 > bytes.size) return bytes
                val isIcc = marker == 0xe2 && length >= 14 &&
                    String(bytes,offset+4,12,Charsets.US_ASCII) == "ICC_PROFILE\u0000"
                if (!isIcc) output.write(bytes,offset,length+2)
                offset += length + 2
            }
        }
        return bytes
    }
}
