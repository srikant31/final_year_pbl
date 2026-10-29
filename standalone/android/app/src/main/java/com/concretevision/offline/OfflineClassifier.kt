package com.concretevision.offline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.exp

data class Prediction(val defect: String, val confidence: Double, val severity: String,
    val probabilities: List<Double>, val action: String, val elapsedMs: Long)

/** Offline CPU inference. Call only on a worker thread. Original source/checkpoint are never opened here. */
class OfflineClassifier(private val context: Context) : AutoCloseable {
    val metadata = JSONObject(context.assets.open("model_metadata.json").bufferedReader().use { it.readText() })
    val classes = metadata.getJSONArray("classes").let { a -> List(a.length()) { a.getString(it) } }
    private val mean = metadata.getJSONArray("mean").let { a -> FloatArray(3) { a.getDouble(it).toFloat() } }
    private val std = metadata.getJSONArray("std").let { a -> FloatArray(3) { a.getDouble(it).toFloat() } }
    private val size = metadata.getInt("image_size")
    private val environment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var mappedModel: MappedByteBuffer? = null

    private fun modelSession(): OrtSession {
        session?.let { return it }
        val expected = metadata.getString("model_sha256")
        require(expected.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid model metadata" }
        // Model asset is stored uncompressed in the signed APK. Map its exact bytes;
        // avoid copying 94 MB to app storage on first use. Still verify SHA-256.
        val mapped = context.assets.openFd("flowx.onnx").use { asset ->
            asset.createInputStream().use { input ->
                input.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(mapped.duplicate())
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        check(actual.equals(expected, ignoreCase = true)) { "Bundled model checksum does not match" }
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            environment.createSession(mapped, options).also { session = it; mappedModel = mapped }
        }
    }

    /** Call on the Activity's worker: keeps extraction/session creation out of Analyze taps. */
    fun prepare() { modelSession() }

    fun prepareInput(bitmap: Bitmap): FloatArray = ImagePreprocessor.tensor(bitmap, size, mean, std)

    fun predict(bitmap: Bitmap): Prediction {
        val started = System.nanoTime()
        val tensor = prepareInput(bitmap)
        return predictTensor(tensor, started)
    }

    fun predictTensor(values: FloatArray, started: Long = System.nanoTime()): Prediction {
        val model = modelSession()
        val logits = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values),
            longArrayOf(1,3,size.toLong(),size.toLong())).use { input ->
            model.run(mapOf("image" to input)).use { output ->
                @Suppress("UNCHECKED_CAST")
                (output[0].value as Array<FloatArray>)[0].copyOf()
            }
        }
        val max = logits.maxOrNull()!!.toDouble()
        val exps = logits.map { exp(it.toDouble() - max) }
        val sum = exps.sum()
        val probabilities = exps.map { it / sum }
        val selected = probabilities.indices.maxBy { probabilities[it] }
        val confidence = probabilities[selected]
        val severity = severity(confidence)
        val defect = classes[selected]
        val action = metadata.getJSONObject("actions").getJSONObject(defect).getString(severity)
        return Prediction(defect, confidence, severity, probabilities, action,
            (System.nanoTime() - started) / 1_000_000)
    }

    override fun close() { session?.close(); session = null; mappedModel = null }

    companion object {
        fun severity(confidence: Double): String = when {
            confidence > 0.85 -> "HIGH"
            confidence > 0.60 -> "MEDIUM"
            else -> "LOW"
        }
    }
}
