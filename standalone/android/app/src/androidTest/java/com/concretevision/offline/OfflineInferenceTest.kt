@file:Suppress("DEPRECATION")
package com.concretevision.offline

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import android.widget.Button
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Real on-device CPU inference against the unchanged teacher-model reference. No server or network. */
class OfflineInferenceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun testResizeDiagnostic() {
        val assets = instrumentation.context.assets
        val reference = JSONObject(assets.open("reference.json").bufferedReader().use { it.readText() })
        val case = reference.getJSONArray("cases").getJSONObject(0)
        val png = assets.open(case.getString("png_file")).use { BitmapFactory.decodeStream(it) }!!
        val rgb = ImagePreprocessor.resizedRgb(png)
        val output = android.graphics.Bitmap.createBitmap(rgb,224,224,android.graphics.Bitmap.Config.ARGB_8888)
        val target = instrumentation.targetContext
        java.io.File(target.getExternalFilesDir(null),"qa-resize.png").outputStream().use { output.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val sample = JSONArray()
        for (y in listOf(0,1,10,png.height/2,png.height-1)) for (x in listOf(0,1,10,png.width/2,png.width-1)) {
            sample.put(JSONObject().put("x",x).put("y",y).put("argb",png.getPixel(x,y)))
        }
        java.io.File(target.getExternalFilesDir(null),"qa-decode-pixels.json").writeText(sample.toString(2))
        png.recycle(); output.recycle()
    }
    @Test fun testOriginalReferenceParity() {
        val assets = instrumentation.context.assets
        val reference = JSONObject(assets.open("reference.json").bufferedReader().use { it.readText() })
        val cases = reference.getJSONArray("cases")
        val rows = JSONArray()
        val target = instrumentation.targetContext
        OfflineClassifier(target).use { classifier ->
            assertEquals(listOf("honeycombing","crack","spalling","void"),classifier.classes)
            for (i in 0 until cases.length()) {
                val case = cases.getJSONObject(i)
                val probs = case.getJSONArray("probabilities").let { a -> List(a.length()) { a.getDouble(it) } }
                val bytes = assets.open(case.getString("tensor_file")).use { it.readBytes() }
                val tensor = FloatArray(bytes.size / 4)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(tensor)
                val tensorResult = classifier.predictTensor(tensor)
                val tensorError = tensorResult.probabilities.indices.maxOf { abs(tensorResult.probabilities[it]-probs[it]) }
                assertTrue("Export/runtime tensor probability drift for ${case.getString("file")}: $tensorError",tensorError < 0.00002)
                assertEquals(case.getString("defect"),tensorResult.defect)
                assertEquals(case.getString("severity"),tensorResult.severity)

                val png = assets.open(case.getString("png_file")).use { ImageLoader.decode(it) }
                val normalized = ImagePreprocessor.tensor(png,224,floatArrayOf(.485f,.456f,.406f),floatArrayOf(.229f,.224f,.225f))
                val preprocessingError = normalized.indices.maxOf { abs(normalized[it]-tensor[it]).toDouble() }
                val pngResult = classifier.predictTensor(normalized)
                png.recycle()
                val pngError = probs.indices.maxOf { abs(pngResult.probabilities[it]-probs[it]) }
                assertTrue("Pillow PNG preprocessing drift for ${case.getString("file")}: $preprocessingError",preprocessingError < 0.000002)
                assertTrue("PNG prediction drift for ${case.getString("file")}: $pngError",pngError < 0.00002)

                val jpeg = assets.open(case.getString("file")).use { ImageLoader.decode(it) }
                val jpegResult = classifier.predict(jpeg)
                jpeg.recycle()
                val jpegError = probs.indices.maxOf { abs(jpegResult.probabilities[it]-probs[it]) }
                rows.put(JSONObject().apply {
                    put("file",case.getString("file")); put("tensor_probability_error",tensorError)
                    put("png_tensor_error",preprocessingError); put("png_probability_error",pngError)
                    put("jpeg_probability_error",jpegError); put("jpeg_defect",jpegResult.defect)
                    put("jpeg_probabilities",JSONArray(jpegResult.probabilities)); put("elapsed_ms",jpegResult.elapsedMs)
                })
                java.io.File(target.filesDir,"android-parity-results.json").writeText(rows.toString(2))
                assertTrue("JPEG decoder probability drift for ${case.getString("file")}: $jpegError",jpegError < reference.getDouble("probability_tolerance"))
                assertEquals(case.getString("defect"),jpegResult.defect)
                assertEquals(case.getString("severity"),jpegResult.severity)
                assertEquals(classifier.metadata.getJSONObject("actions").getJSONObject(jpegResult.defect).getString(jpegResult.severity),jpegResult.action)
            }
        }
    }

    @Test fun testSeverityBoundaries() {
        assertEquals("LOW",OfflineClassifier.severity(0.0))
        assertEquals("LOW",OfflineClassifier.severity(0.60))
        assertEquals("MEDIUM",OfflineClassifier.severity(0.600001))
        assertEquals("MEDIUM",OfflineClassifier.severity(0.85))
        assertEquals("HIGH",OfflineClassifier.severity(0.850001))
        assertEquals("HIGH",OfflineClassifier.severity(1.0))
    }

    @Test fun testInvalidImagesAreRejected() {
        for (bytes in listOf(byteArrayOf(),"not an image".toByteArray(),byteArrayOf(-1,-40,-1))) {
            try { ImageLoader.decode(ByteArrayInputStream(bytes)); fail("Invalid image accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun testColorProfileRemovalIsBoundedAndNonMutating() {
        val strip = ImageLoader::class.java.getDeclaredMethod("withoutColorProfiles",ByteArray::class.java)
        strip.isAccessible = true
        for (name in listOf("honeycomb.jpg","honeycomb.png")) {
            val original = instrumentation.context.assets.open(name).use { it.readBytes() }
            val copy = original.copyOf()
            val stripped = strip.invoke(ImageLoader,original) as ByteArray
            assertArrayEquals("Source bytes changed for $name",copy,original)
            assertTrue("Expected a profile to be removed from $name",stripped.size < original.size)
        }
        val badPng = byteArrayOf(0x89.toByte(),80,78,71,13,10,26,10,127,127,127,127,105,67,67,80,0,0,0,0)
        val badJpeg = byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte(),0xe2.toByte(),0xff.toByte(),0xff.toByte())
        for (bad in listOf(badPng,badJpeg)) {
            val copy = bad.copyOf()
            assertArrayEquals(copy,strip.invoke(ImageLoader,bad) as ByteArray)
            assertArrayEquals(copy,bad)
            try { ImageLoader.decode(ByteArrayInputStream(bad)); fail("Malformed color-profile segment accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun testOnlineNetworkingIsRestrictedToLoopbackForCleartext() {
        val context = instrumentation.targetContext
        val info = context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions?.contains("android.permission.INTERNET") ?: false)
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1"))
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"))
    }

    @Test fun testInitialUiAndReset() {
        val target = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(target,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertTrue(activity.findViewById<Button>(MainActivity.ID_CHOOSE).isEnabled)
                assertFalse(activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled)
                activity.findViewById<Button>(MainActivity.ID_RESET).performClick()
                assertEquals("Ready to select a photograph",activity.findViewById<TextView>(MainActivity.ID_STATUS).text.toString())
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    @Test fun testFullActivityFlowAndRecreation() {
        val target = instrumentation.targetContext
        val flowStart = SystemClock.elapsedRealtime()
        var activity = instrumentation.startActivitySync(Intent(target,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val launchedAt = SystemClock.elapsedRealtime()
        try {
            val selectedAt = SystemClock.elapsedRealtime()
            // Supplies the normal callback result using a test-only provider, without a production test API.
            instrumentation.runOnMainSync {
                val callback = MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java)
                callback.isAccessible = true
                callback.invoke(activity,100,android.app.Activity.RESULT_OK,
                    Intent().setData(Uri.parse("content://com.concretevision.offline.test.fixtures/honeycomb.jpg")))
            }
            try { waitUntil(30_000) { activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled } }
            catch (e: AssertionError) {
                var status = ""
                instrumentation.runOnMainSync { status = activity.findViewById<TextView>(MainActivity.ID_STATUS).text.toString() }
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"ui-thread-stacks.txt").writeText(
                    Thread.getAllStackTraces().entries.joinToString("\n\n") { (t, frames) -> t.name + " " + t.state + "\n" + frames.joinToString("\n") })
                throw AssertionError("Photo preparation status: $status", e)
            }
            val analyzeAt = SystemClock.elapsedRealtime()
            instrumentation.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_ANALYZE).performClick() }
            waitUntil(90_000) { activity.findViewById<View>(MainActivity.ID_REPORT).visibility == View.VISIBLE }
            val reportAt = SystemClock.elapsedRealtime()
            java.io.File(target.getExternalFilesDir(null),"ui-performance.json").writeText(JSONObject().apply {
                put("activity_launch_ms",launchedAt-flowStart)
                put("photo_result_to_ready_ms",analyzeAt-selectedAt)
                put("analyze_to_report_ms",reportAt-analyzeAt)
                put("launch_to_report_ms",reportAt-flowStart)
                put("scope","Programmatically supplied URI, no picker interaction; 100ms polling granularity; no screen-present timestamp")
            }.toString(2))
            instrumentation.runOnMainSync {
                val text = allText(activity.findViewById(MainActivity.ID_REPORT))
                assertTrue("Unexpected native report: $text",text.contains("Honeycombing"))
                assertTrue("Unexpected native report: $text",text.contains("Severity: LOW"))
                val scroll = activity.findViewById<View>(MainActivity.ID_REPORT).parent.parent as ScrollView
                scroll.scrollTo(0,activity.findViewById<View>(MainActivity.ID_REPORT).top)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(500)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val evidence = java.io.File(target.getExternalFilesDir(null),"qa-native-report.png")
            evidence.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()

            val monitor = instrumentation.addMonitor(MainActivity::class.java.name,null,false)
            instrumentation.runOnMainSync { activity.recreate() }
            val recreated = monitor.waitForActivityWithTimeout(15_000) as? MainActivity
            instrumentation.removeMonitor(monitor)
            assertNotNull("Activity was not recreated",recreated)
            activity = recreated!!
            waitUntil(30_000) { activity.findViewById<View>(MainActivity.ID_REPORT).visibility == View.VISIBLE }
            instrumentation.runOnMainSync {
                assertTrue(allText(activity.findViewById(MainActivity.ID_REPORT)).contains("Honeycombing"))
                activity.findViewById<Button>(MainActivity.ID_RESET).performClick()
                assertFalse(activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled)
                assertEquals(View.GONE,activity.findViewById<View>(MainActivity.ID_REPORT).visibility)
                assertEquals(View.GONE,activity.findViewById<View>(MainActivity.ID_PREVIEW).visibility)
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        fail("Timed out waiting for native Activity state")
    }

    private fun allText(view: View): String = when (view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { allText(view.getChildAt(it)) }
        else -> ""
    }
}
