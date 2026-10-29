package com.concretevision.offline
import android.content.Context
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import org.tensorflow.lite.Interpreter
class PhoneGate(private val context: Context):AutoCloseable {
    private var interpreter:Interpreter?=null
    fun input(bitmap:Bitmap): ByteBuffer {
        val pixels=AgenticPreprocessor.resizedRgb(bitmap)
        return ByteBuffer.allocateDirect(224*224*3*4).order(ByteOrder.nativeOrder()).apply {
            for(p in pixels) {putFloat(((p ushr 16) and 255)/255f);putFloat(((p ushr 8) and 255)/255f);putFloat((p and 255)/255f)}
            rewind()
        }
    }
    fun score(bitmap:Bitmap):Float {
        val runtime=interpreter ?: context.assets.openFd("spalling_model.tflite").use {asset ->
            asset.createInputStream().use {stream ->
                val model=stream.channel.map(FileChannel.MapMode.READ_ONLY,asset.startOffset,asset.declaredLength)
                val hash=MessageDigest.getInstance("SHA-256").apply {update(model.duplicate())}.digest().joinToString("") {"%02x".format(it.toInt() and 255)}
                check(hash=="520a763549b69b3310e5e082ff5520df03e14eb81bd05646ecb47a98071c747e") {"Gate model checksum mismatch"}
                Interpreter(model,Interpreter.Options().setNumThreads(2)).also {interpreter=it}
            }
        }
        val output=Array(1) {FloatArray(1)}
        runtime.run(input(bitmap),output)
        return output[0][0]
    }
    override fun close() {interpreter?.close();interpreter=null}
}
