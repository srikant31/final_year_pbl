@file:Suppress("DEPRECATION")
package com.concretevision.offline

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class CameraCaptureTest {
    private val ins get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = ins.targetContext
    private fun launch() = ins.startActivitySync(Intent(target,MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun pending(activity: MainActivity): String? = MainActivity::class.java.getDeclaredField("pendingCameraName").let {
        it.isAccessible=true; it.get(activity) as? String
    }
    private fun reply(activity: MainActivity,code: Int) {
        MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }.invoke(activity,101,code,null)
    }
    private fun waitFor(activity: MainActivity,condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis()+60_000
        while (SystemClock.uptimeMillis()<end) {
            var ready=false; ins.runOnMainSync { ready=condition() }
            if (ready) return
            SystemClock.sleep(100)
        }
        var status=""; ins.runOnMainSync { status=activity.findViewById<TextView>(MainActivity.ID_STATUS).text.toString() }
        fail("Camera flow timed out: $status")
    }
    private fun monitor(mode: String,seen: (Intent) -> Unit): Instrumentation.ActivityMonitor {
        val result=object: Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action==MediaStore.ACTION_IMAGE_CAPTURE) {
                    seen(Intent(intent))
                    intent.component=ComponentName(ins.context.packageName,FixtureCameraActivity::class.java.name)
                    intent.putExtra("fixture_mode",mode)
                }
                return null
            }
        }
        ins.addMonitor(result); return result
    }
    private fun text(view: View): String = when(view) {
        is TextView -> view.text.toString()
        is ViewGroup -> (0 until view.childCount).joinToString("\n") { text(view.getChildAt(it)) }
        else -> ""
    }
    private fun finish(activity: MainActivity) = ins.runOnMainSync {
        activity.findViewById<Button>(MainActivity.ID_RESET).performClick(); activity.finish()
    }

    @Test fun cameraWritesFullPhotoAnalyzesAndResetDeletesIt() {
        val activity=launch(); var launched: Intent?=null
        val observer=monitor("success") { launched=it }
        var uri: Uri?=null
        try {
            ins.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_CAMERA).performClick() }
            waitFor(activity) { activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled }
            assertNotNull(launched)
            uri=launched!!.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
            assertEquals("content",uri!!.scheme)
            assertTrue(launched!!.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
            assertTrue(launched!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri,launched!!.clipData!!.getItemAt(0).uri)
            val file=CaptureStore(target).file(uri!!.lastPathSegment!!)
            assertTrue(file.length()>1_000_000) // Full-resolution fixture, not camera thumbnail.
            target.contentResolver.openInputStream(uri!!).use { input ->
                val bitmap=ImageLoader.decode(input!!)
                assertEquals(3072,bitmap.width); assertEquals(3072,bitmap.height); bitmap.recycle()
            }
            ins.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_ANALYZE).performClick() }
            waitFor(activity) { activity.findViewById<View>(MainActivity.ID_REPORT).visibility==View.VISIBLE }
            ins.runOnMainSync {
                val report=text(activity.findViewById(MainActivity.ID_REPORT))
                assertTrue(report.contains("Honeycombing")); assertTrue(report.contains("Severity: LOW"))
                activity.findViewById<Button>(MainActivity.ID_RESET).performClick()
                assertFalse(activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled)
            }
            assertFalse(file.exists())
        } finally {
            ins.removeMonitor(observer); finish(activity); CaptureStore(target).deleteOwned(uri)
        }
    }

    @Test fun cameraCancelKeepsExistingSelectionAndRemovesEmptyOutput() {
        val activity=launch(); var uri: Uri?=null
        val observer=monitor("cancel") { uri=it.getParcelableExtra(MediaStore.EXTRA_OUTPUT) }
        try {
            ins.runOnMainSync {
                MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }.invoke(activity,100,Activity.RESULT_OK,
                    Intent().setData(Uri.parse("content://com.concretevision.offline.test.fixtures/void.jpg")))
            }
            waitFor(activity) { activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled }
            ins.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_CAMERA).performClick() }
            waitFor(activity) { uri!=null && pending(activity)==null }
            ins.runOnMainSync {
                assertTrue(activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled)
                assertEquals("void.jpg",activity.findViewById<TextView>(MainActivity.ID_FILENAME).text.toString())
            }
            assertFalse(CaptureStore(target).file(uri!!.lastPathSegment!!).exists())
        } finally { ins.removeMonitor(observer); finish(activity) }
    }

    @Test fun emptySuccessfulCaptureIsRejectedAndRetryAvailable() {
        val activity=launch(); var uri: Uri?=null
        val observer=monitor("empty") { uri=it.getParcelableExtra(MediaStore.EXTRA_OUTPUT) }
        try {
            ins.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_CAMERA).performClick() }
            waitFor(activity) { uri!=null && pending(activity)==null }
            ins.runOnMainSync {
                assertFalse(activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled)
                assertTrue(activity.findViewById<Button>(MainActivity.ID_CAMERA).isEnabled)
                assertTrue(activity.findViewById<TextView>(MainActivity.ID_STATUS).text.contains("no photograph"))
            }
            assertFalse(CaptureStore(target).file(uri!!.lastPathSegment!!).exists())
        } finally { ins.removeMonitor(observer); finish(activity) }
    }

    @Test fun missingCameraAppHasPickerFallbackAndCleansOutput() {
        val activity=launch(); var uri: Uri?=null
        val observer=object: Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action==MediaStore.ACTION_IMAGE_CAPTURE) {
                    uri=intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
                    throw android.content.ActivityNotFoundException("Simulated missing camera")
                }
                return null
            }
        }
        ins.addMonitor(observer)
        try {
            ins.runOnMainSync {
                activity.findViewById<Button>(MainActivity.ID_CAMERA).performClick()
                assertNull(pending(activity))
                assertTrue(activity.findViewById<Button>(MainActivity.ID_CHOOSE).isEnabled)
                assertTrue(activity.findViewById<TextView>(MainActivity.ID_STATUS).text.contains("No camera app"))
            }
            assertNotNull(uri); assertFalse(CaptureStore(target).file(uri!!.lastPathSegment!!).exists())
        } finally { ins.removeMonitor(observer); finish(activity) }
    }

    @Test fun pendingCaptureSurvivesActivityRecreation() {
        var activity=launch(); val store=CaptureStore(target); val file=store.create()
        try {
            ins.runOnMainSync {
                MainActivity::class.java.getDeclaredField("pendingCameraName").apply { isAccessible=true }.set(activity,file.name)
            }
            val observer=ins.addMonitor(MainActivity::class.java.name,null,false)
            ins.runOnMainSync { activity.recreate() }
            val restored=observer.waitForActivityWithTimeout(15_000) as? MainActivity
            ins.removeMonitor(observer); assertNotNull(restored); activity=restored!!
            ins.runOnMainSync { assertEquals(file.name,pending(activity)) }
            ins.context.assets.open("honeycomb.jpg").use { input -> file.outputStream().use { input.copyTo(it) } }
            ins.runOnMainSync { reply(activity,Activity.RESULT_OK) }
            waitFor(activity) { activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled }
            assertTrue(file.isFile)
            ins.runOnMainSync { activity.findViewById<Button>(MainActivity.ID_RESET).performClick() }
            assertFalse(file.exists())
        } finally { finish(activity); store.delete(file.name) }
    }

    @Test fun previewRotationDoesNotModifyAnalysisPixelsAndProviderIsRestricted() {
        val store=CaptureStore(target); val file=store.create()
        try {
            val original=Bitmap.createBitmap(24,12,Bitmap.Config.ARGB_8888)
            original.eraseColor(android.graphics.Color.RED)
            file.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG,100,it) }; original.recycle()
            ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION,"6"); saveAttributes() }
            val bitmap=file.inputStream().use { ImageLoader.decode(it) }
            val before=ImagePreprocessor.resizedRgb(bitmap)
            val display=PhotoPreview.create(target,store.uri(file.name),bitmap)
            assertEquals(12,display.width); assertEquals(24,display.height)
            assertEquals(24,bitmap.width); assertEquals(12,bitmap.height)
            assertArrayEquals(before,ImagePreprocessor.resizedRgb(bitmap))
            display.recycle(); bitmap.recycle()
            try { store.file("../model.onnx"); fail("Traversal accepted") } catch (_: IllegalArgumentException) { }
            val info=target.packageManager.resolveContentProvider("${target.packageName}.captures",0)!!
            assertFalse(info.exported); assertTrue(info.grantUriPermissions)
        } finally { store.delete(file.name) }
    }
}
