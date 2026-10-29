package com.concretevision.offline

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Capacity benchmark, NOT a camera capture/preview FPS test. Debug build, emulator. */
class PerfBenchmarkTest {
    private fun now() = System.nanoTime()
    private fun ms(start: Long) = (now() - start) / 1e6
    private fun stats(values: List<Double>) = JSONObject().apply {
        val sorted = values.sorted()
        put("samples_ms", JSONArray(values)); put("mean_ms", values.average())
        put("median_ms", sorted[sorted.size / 2]); put("p95_ms", sorted[kotlin.math.ceil(sorted.size*.95).toInt()-1])
    }
    @Test fun benchmark() {
        val ins = InstrumentationRegistry.getInstrumentation()
        val target = ins.targetContext
        // Only this application's generated model extraction is removed; no user/source photos.
        target.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("flowx-") && it.extension == "onnx" }?.forEach { check(it.delete()) }
        val report = JSONObject()
        val first = now()
        val decodeStart = now()
        val photo = ins.context.assets.open("honeycomb.jpg").use { ImageLoader.decode(it) }
        report.put("first_decode_ms", ms(decodeStart))
        val construct = now()
        OfflineClassifier(target).use { classifier ->
            report.put("classifier_construction_ms", ms(construct))
            val cold = now()
            val firstResult = classifier.predict(photo)
            report.put("cold_first_predict_ms", ms(cold))
            report.put("first_decode_through_prediction_ms", ms(first))
            report.put("first_defect", firstResult.defect)
            val prep = mutableListOf<Double>(); val infer = mutableListOf<Double>(); val totals = mutableListOf<Double>()
            repeat(8) {
                val total = now(); val a = now()
                val tensor = ImagePreprocessor.tensor(photo,224,floatArrayOf(.485f,.456f,.406f),floatArrayOf(.229f,.224f,.225f))
                prep.add(ms(a)); val b = now(); classifier.predictTensor(tensor); infer.add(ms(b)); totals.add(ms(total))
            }
            report.put("photo_preprocess",stats(prep)); report.put("photo_inference",stats(infer)); report.put("photo_total",stats(totals))
            val frame = Bitmap.createScaledBitmap(photo,640,480,true)
            val frames = mutableListOf<Double>()
            repeat(3) { classifier.predict(frame) }
            repeat(30) { val t=now(); classifier.predict(frame); frames.add(ms(t)) }
            report.put("synthetic_640x480_processing",stats(frames))
            report.put("synthetic_processing_fps",1000.0/frames.average())
            report.put("scope","No camera capture/YUV conversion/preview/rendering; 30 sequential synthetic frames, not sustained thermal test. Cold test removes private model cache, not OS disk caches. Process/native libraries may be warm.")
            frame.recycle()
        }
        // Optimized UI schedule: prep can run during photo selection/opening. Report costs
        // separately rather than pretending moved work was eliminated. Native library is warm.
        target.noBackupFilesDir.listFiles()?.filter { it.name.startsWith("flowx-") && it.extension == "onnx" }?.forEach { check(it.delete()) }
        val prepareMethod = OfflineClassifier::class.java.methods.firstOrNull { it.name == "prepare" }
        if (prepareMethod != null) {
            val staged = now()
            OfflineClassifier(target).use { classifier ->
                val a = now(); prepareMethod.invoke(classifier); report.put("staged_model_prepare_ms",ms(a))
                val b = now()
                val input = OfflineClassifier::class.java.getMethod("prepareInput",Bitmap::class.java).invoke(classifier,photo) as FloatArray
                report.put("staged_image_prepare_ms",ms(b))
                val c = now(); val prediction = classifier.predictTensor(input); report.put("staged_first_analyze_ms",ms(c))
                report.put("staged_total_excluding_decode_ms",ms(staged)); report.put("staged_defect",prediction.defect)
            }
        }
        photo.recycle()
        File(target.getExternalFilesDir(null),"performance.json").writeText(report.toString(2))
    }
}
