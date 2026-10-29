package com.concretevision.offline

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

/** All interaction is native Android. There is no network client or Python server. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var classifier: OfflineClassifier? = null
    private var currentBitmap: Bitmap? = null
    private var currentUri: Uri? = null
    private var currentName = ""
    private var lastPrediction: Prediction? = null
    private var generation = 0
    private var busy = false
    private lateinit var choose: Button
    private lateinit var takePhoto: Button
    private val captures by lazy { CaptureStore(applicationContext) }
    private var pendingCameraName: String? = null
    private lateinit var analyze: Button
    private lateinit var reset: Button
    private lateinit var status: TextView
    private lateinit var filename: TextView
    private lateinit var preview: ImageView
    private lateinit var progress: TextView
    private lateinit var report: LinearLayout
    private val bg = Color.rgb(13,27,42)
    private val card = Color.rgb(27,40,56)
    private val foreground = Color.rgb(232,237,242)
    private val muted = Color.rgb(156,177,198)
    private val accent = Color.rgb(0,170,255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        pendingCameraName = savedInstanceState?.getString("pending_capture")?.takeIf {
            runCatching { captures.file(it).isFile }.getOrDefault(false)
        }
        worker.execute {
            try {
                val model = classifier ?: OfflineClassifier(applicationContext).also { classifier = it }
                model.prepare()
            } catch (_: Exception) { /* Normal Analyze path retries and reports errors. */ }
            catch (_: OutOfMemoryError) { /* Normal Analyze path handles retry/errors. */ }
        }
        if (savedInstanceState != null) {
            lastPrediction = savedInstanceState.getString("prediction")?.let { decodePrediction(it) }
            val uri = savedInstanceState.getString("image_uri")
            if (uri != null) selectImage(Uri.parse(uri), savedInstanceState.getString("image_name") ?: "Photograph", true)
        }
        runCatching { captures.cleanupOld(setOfNotNull(pendingCameraName,currentUri?.lastPathSegment)) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun label(value: String, size: Float = 16f, color: Int = foreground): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0,dp(7),0,dp(7))
    }
    private fun background(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(16).toFloat()
    }
    private fun button(value: String, id: Int, color: Int): Button = Button(this).apply {
        this.id = id; text = value; isAllCaps = false; textSize = 16f
        setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        minHeight = dp(52)
    }
    private fun buildScreen() {
        val frame = FrameLayout(this).apply { setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(16),dp(18),dp(24))
        }
        frame.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        scroll.addView(content)
        @Suppress("DEPRECATION")
        frame.setOnApplyWindowInsetsListener { view,insets ->
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,
                insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageBitmap(android.graphics.BitmapFactory.decodeResource(resources, R.drawable.logo,
                android.graphics.BitmapFactory.Options().apply { inScaled = false; inSampleSize = 4 }))
            contentDescription = "Concrete Vision logo"
        },LinearLayout.LayoutParams(dp(64),dp(64)).apply { marginEnd = dp(12) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("Concrete Vision",23f).apply { setTypeface(null,Typeface.BOLD) })
            addView(label("OFFLINE • ON YOUR PHONE",12f,accent))
        })
        content.addView(header)
        content.addView(label("Take a photo or choose an existing concrete photograph to analyze on this device.",15f,muted))
        content.addView(label("No laptop or internet required",14f,Color.rgb(64,210,162)))
        takePhoto = button("Take photo",ID_CAMERA,Color.rgb(29,111,165))
        takePhoto.setOnClickListener { openCamera() }; content.addView(takePhoto)
        choose = button("Choose a photograph",ID_CHOOSE,Color.rgb(29,111,165))
        choose.setOnClickListener { openPicker() }; content.addView(choose)
        filename = label("JPEG, PNG or BMP • up to 15 MiB",13f,muted).apply { id = ID_FILENAME }
        content.addView(filename)
        preview = ImageView(this).apply {
            id = ID_PREVIEW; contentDescription = "Selected photograph"; scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE; background = background(card)
        }
        content.addView(preview,LinearLayout.LayoutParams(-1,dp(240)))
        analyze = button("Analyze photograph",ID_ANALYZE,Color.rgb(22,101,52)).apply { isEnabled = false }
        analyze.setOnClickListener { runAnalysis() }
        content.addView(analyze,LinearLayout.LayoutParams(-1,dp(56)).apply { topMargin = dp(12) })
        progress = label("Working...",14f,accent).apply { visibility = View.GONE; contentDescription = "Working" }
        content.addView(progress,LinearLayout.LayoutParams(-1,-2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        status = label("Ready to select a photograph",14f,muted).apply { id = ID_STATUS; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(status)
        reset = button("New inspection",ID_RESET,Color.rgb(72,85,106))
        reset.setOnClickListener { clearInspection() }; content.addView(reset)
        report = LinearLayout(this).apply {
            id = ID_REPORT; orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(16))
            background = background(card); visibility = View.GONE
        }
        content.addView(report,LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })
        content.addView(label("Model outputs one of four defect labels. It has no healthy/unknown category. The inherited severity label is based on confidence; it does not measure physical damage.",12f,muted))
        setContentView(frame)
        frame.requestApplyInsets()
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/jpeg","image/png","image/bmp","image/x-ms-bmp"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try { @Suppress("DEPRECATION") startActivityForResult(intent,PICK_IMAGE) }
        catch (_: android.content.ActivityNotFoundException) { showError("No image picker is installed on this device.") }
    }

    private fun openCamera() {
        if (busy || pendingCameraName != null) return
        try {
            val file = captures.create()
            pendingCameraName = file.name
            val output = captures.uri(file.name)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT,output)
                clipData = ClipData.newRawUri("Capture destination",output)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent,CAPTURE_IMAGE)
        } catch (_: android.content.ActivityNotFoundException) {
            discardPendingCapture(); showError("No camera app is installed. You can still choose a photograph.")
        } catch (e: Exception) {
            discardPendingCapture(); showError("Could not open the camera. ${e.message ?: "Try again."}")
        }
    }

    private fun discardPendingCapture() {
        val name = pendingCameraName ?: return
        runCatching { revokeUriPermission(captures.uri(name),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        captures.delete(name); pendingCameraName = null
    }

    @Deprecated("Uses the platform Activity result callback")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == CAPTURE_IMAGE) {
            val name = pendingCameraName ?: return
            pendingCameraName = null
            val uri = captures.uri(name)
            runCatching { revokeUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            if (resultCode != RESULT_OK) {
                captures.delete(name)
                return
            }
            if (!captures.file(name).isFile || captures.file(name).length() == 0L) {
                captures.delete(name); showError("The camera returned no photograph. Please take it again.")
                return
            }
            selectImage(uri,"Camera photograph")
            return
        }
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* Some document providers offer session-only access. */ }
        var name = "Photograph"
        try {
            contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
        } catch (_: Exception) { /* The provider may omit display metadata. */ }
        selectImage(uri,name)
    }

    private fun selectImage(uri: Uri,name: String,restoring: Boolean = false) {
        val token = ++generation
        if (currentUri != uri) captures.deleteOwned(currentUri)
        currentUri = uri; currentName = name; currentBitmap = null
        if (!restoring) lastPrediction = null
        report.visibility = View.GONE; preview.visibility = View.GONE
        filename.text = name
        setBusy(true,"Opening photograph…")
        worker.execute {
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { ImageLoader.decode(it) }
                    ?: throw IllegalArgumentException("This image is no longer accessible. Select it again.")
                val thumbnail = PhotoPreview.create(applicationContext,uri,bitmap)
                runOnUiThread {
                    if (isDestroyed || token != generation) return@runOnUiThread
                    currentBitmap = bitmap
                    preview.setImageBitmap(thumbnail); preview.visibility = View.VISIBLE
                    setBusy(false,"Photograph ready. Analysis runs entirely on this phone.")
                    lastPrediction?.let { showPrediction(it) }
                }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed && token == generation) {
                    captures.deleteOwned(uri); currentUri = null; lastPrediction = null; setBusy(false,""); showError(e.message ?: "Cannot open this image.")
                } }
            } catch (_: OutOfMemoryError) {
                runOnUiThread { if (!isDestroyed && token == generation) {
                    captures.deleteOwned(uri); currentUri = null; lastPrediction = null; setBusy(false,""); showError("Not enough memory. Choose a smaller photograph.")
                } }
            }
        }
    }

    private fun runAnalysis() {
        val bitmap = currentBitmap ?: return
        if (busy) return
        val token = generation
        lastPrediction = null; report.visibility = View.GONE
        setBusy(true,"Analyzing on this phone… First analysis prepares the bundled model.")
        worker.execute {
            try {
                val model = classifier ?: OfflineClassifier(applicationContext).also { classifier = it }
                val result = model.predict(bitmap)
                runOnUiThread { if (!isDestroyed && token == generation) {
                    lastPrediction = result; setBusy(false,"Analysis complete • ${result.elapsedMs} ms on this device")
                    showPrediction(result)
                } }
            } catch (e: Exception) {
                runOnUiThread { if (!isDestroyed && token == generation) {
                    setBusy(false,""); showError("Analysis failed. ${e.message ?: "Try again."}")
                } }
            } catch (_: OutOfMemoryError) {
                runOnUiThread { if (!isDestroyed && token == generation) {
                    setBusy(false,""); showError("Not enough memory to run the model. Close other apps and retry.")
                } }
            }
        }
    }

    private fun setBusy(value: Boolean,message: String) {
        busy = value; choose.isEnabled = !value; takePhoto.isEnabled = !value; analyze.isEnabled = !value && currentBitmap != null
        progress.visibility = if (value) View.VISIBLE else View.GONE
        status.text = message; status.setTextColor(muted)
    }
    private fun showError(message: String) { status.text = message; status.setTextColor(Color.rgb(255,139,139)) }
    private fun clearInspection() {
        discardPendingCapture()
        captures.deleteOwned(currentUri)
        generation++; currentBitmap = null; currentUri = null; lastPrediction = null; currentName = ""
        preview.setImageDrawable(null); preview.visibility = View.GONE
        report.removeAllViews(); report.visibility = View.GONE
        filename.text = "JPEG, PNG or BMP • up to 15 MiB"
        setBusy(false,"Ready to select a photograph")
    }
    private fun showPrediction(result: Prediction) {
        report.removeAllViews(); report.visibility = View.VISIBLE
        report.addView(label("ANALYSIS REPORT",13f,accent))
        report.addView(label(result.defect.replaceFirstChar { it.uppercase() },26f).apply { setTypeface(null,Typeface.BOLD) })
        report.addView(label("Confidence: ${percent(result.confidence)}",18f))
        report.addView(label("Severity: ${result.severity}",16f,when (result.severity) {
            "HIGH" -> Color.rgb(255,139,139); "MEDIUM" -> Color.rgb(255,200,86); else -> Color.rgb(64,210,162)
        }))
        report.addView(label("CLASS PROBABILITIES",13f,accent))
        val names = listOf("honeycombing","crack","spalling","void")
        result.probabilities.indices.sortedByDescending { result.probabilities[it] }.forEach { i ->
            report.addView(label("${names[i].replaceFirstChar { it.uppercase() }}: ${percent(result.probabilities[i])}",15f))
            report.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
                max = 10000; progress = (result.probabilities[i]*10000).toInt()
                contentDescription = "${names[i]} ${percent(result.probabilities[i])}"
            },LinearLayout.LayoutParams(-1,dp(8)))
        }
        report.addView(label("ORIGINAL ACTION RULES",13f,accent))
        report.addView(label(result.action,14f))
    }
    private fun percent(value: Double) = String.format(Locale.US,"%.1f%%",value*100)
    override fun onSaveInstanceState(outState: Bundle) {
        pendingCameraName?.let { outState.putString("pending_capture",it) }
        currentUri?.let { outState.putString("image_uri",it.toString()); outState.putString("image_name",currentName) }
        lastPrediction?.let { result ->
            outState.putString("prediction",JSONObject().apply {
                put("defect",result.defect); put("confidence",result.confidence); put("severity",result.severity)
                put("probabilities",JSONArray(result.probabilities)); put("action",result.action); put("elapsedMs",result.elapsedMs)
            }.toString())
        }
        super.onSaveInstanceState(outState)
    }
    private fun decodePrediction(value: String): Prediction? = try {
        JSONObject(value).let { j -> Prediction(j.getString("defect"),j.getDouble("confidence"),j.getString("severity"),
            j.getJSONArray("probabilities").let { a -> List(a.length()) { a.getDouble(it) } },
            j.getString("action"),j.getLong("elapsedMs")) }
    } catch (_: Exception) { null }
    override fun onDestroy() {
        generation++
        worker.execute { classifier?.close(); classifier = null }
        worker.shutdown()
        super.onDestroy()
    }
    companion object {
        private const val PICK_IMAGE = 100
        private const val CAPTURE_IMAGE = 101
        const val ID_CAMERA = 1008
        const val ID_CHOOSE = 1001
        const val ID_ANALYZE = 1002
        const val ID_RESET = 1003
        const val ID_STATUS = 1004
        const val ID_REPORT = 1005
        const val ID_PREVIEW = 1006
        const val ID_FILENAME = 1007
    }
}
