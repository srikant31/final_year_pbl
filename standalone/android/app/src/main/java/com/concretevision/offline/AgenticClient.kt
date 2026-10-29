package com.concretevision.offline
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
/** Phone gate -> direct HTTPS Gemini -> original parser/action rules. No laptop endpoint. */
class AgenticClient(
    private val cloud: ((String,JSONObject,String,Int)->JSONObject)?=null,
    private val gateOverride: ((Bitmap)->Float)?=null
):AutoCloseable {
    @Volatile private var active:HttpURLConnection?=null
    @Volatile private var cancelled=false
    private var gate:PhoneGate?=null
    fun cancel() {cancelled=true;active?.disconnect()}
    private fun checkCancelled() {check(!cancelled) {"Analysis cancelled"}}
    override fun close() {cancel();gate?.close();gate=null}
    private class Availability(val status:Int):Exception()
    private fun request(model:String,payload:JSONObject,key:String,timeout:Int):JSONObject {
        cloud?.let {return it(model,payload,key,timeout)}
        checkCancelled()
        val conn=URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        active=conn
        try {
            conn.requestMethod="POST";conn.doOutput=true;conn.connectTimeout=10_000;conn.readTimeout=timeout
            conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("x-goog-api-key",key)
            val bytes=payload.toString().toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size);conn.outputStream.use {it.write(bytes)}
            val code=conn.responseCode
            if(code !in 200..299) {
                if(code in listOf(404,408,429,500,502,503,504)) throw Availability(code)
                error("Gemini rejected the request (HTTP $code). Check the phone API key and account access.")
            }
            return conn.inputStream.use {input ->
                val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {checkCancelled();val n=input.read(buffer);if(n<0)break
                    check(output.size()+n<=1024*1024) {"Gemini response is too large"};output.write(buffer,0,n)}
                JSONObject(output.toString("UTF-8"))
            }
        } finally {conn.disconnect();if(active===conn)active=null}
    }
    fun analyze(context:Context,uri:Uri):JSONObject {
        cancelled=false
        val start=SystemClock.elapsedRealtime()
        val contract=JSONObject(context.assets.open("agentic_contract.json").bufferedReader().use {it.readText()})
        val bitmap=context.contentResolver.openInputStream(uri)?.use {ImageLoader.decode(it)} ?: error("Select the photo again")
        try {
            checkCancelled()
            val gateStart=SystemClock.elapsedRealtime()
            val score=gateOverride?.invoke(bitmap) ?: (gate ?: PhoneGate(context).also {gate=it}).score(bitmap)
            val gateMs=SystemClock.elapsedRealtime()-gateStart
            var defect="No Defect";var severity="LOW";var actual="gemini-3.8-flash";var raw:String?=null
            val attempts=JSONArray()
            if(score>0.75f) {
                val key=GeminiKeyStore.load(context)
                check(key.isNotBlank()) {"Save your Gemini API key using Gemini key settings."}
                val jpeg=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,75,it)}.toByteArray()
                val parts=JSONArray().put(JSONObject().put("text",contract.getString("prompt"))).put(JSONObject().put("inline_data",
                    JSONObject().put("mime_type","image/jpeg").put("data",Base64.encodeToString(jpeg,Base64.NO_WRAP))))
                val payload=JSONObject().put("contents",JSONArray().put(JSONObject().put("role","user").put("parts",parts)))
                for((model,timeout) in listOf("gemini-3.8-flash" to 25000,"gemini-3.6-flash" to 45000)) {
                    checkCancelled();val attemptStart=SystemClock.elapsedRealtime()
                    val reply=try {request(model,payload,key,timeout)} catch(e:Exception) {
                        checkCancelled()
                        val transient=e is Availability || e is java.io.IOException
                        if(!transient) throw e
                        attempts.put(JSONObject().put("model",model).put("status",if(e is Availability) "http_${e.status}" else "network_timeout")
                            .put("elapsed_ms",SystemClock.elapsedRealtime()-attemptStart))
                        continue
                    }
                    val candidates=reply.optJSONArray("candidates")
                    val content=candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val text=if(content==null) "" else (0 until content.length()).joinToString("") {content.getJSONObject(it).optString("text","")}
                    check(text.isNotBlank()) {"Gemini returned an empty or blocked response. Try another image."}
                    raw=text;actual=model
                    attempts.put(JSONObject().put("model",model).put("status","success").put("elapsed_ms",SystemClock.elapsedRealtime()-attemptStart))
                    break
                }
                check(raw!=null) {"Both Gemini models are unavailable. Check the phone internet and retry."}
                val parsed=parse(raw!!);defect=parsed.first;severity=parsed.second
            }
            checkCancelled()
            return JSONObject().put("mode","professor_agentic").put("gate_score",score.toDouble()).put("gate_threshold",0.75)
                .put("gate_verdict",if(score>0.75f) "Defect Detected" else "No Defect").put("gemini_called",score>0.75f)
                .put("gemini_model",actual).put("gemini_fallback_used",actual!="gemini-3.8-flash").put("gemini_attempts",attempts)
                .put("defect",defect).put("severity",severity).put("action",action(contract,defect,severity))
                .put("gate_ms",gateMs).put("elapsed_ms",SystemClock.elapsedRealtime()-start).put("raw_response",raw ?: JSONObject.NULL)
        } finally {bitmap.recycle()}
    }
    companion object {
        fun parse(text:String):Pair<String,String> {
            val d=Regex("Defect Type\\s*:\\s*(.*)",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
            val s=Regex("Severity\\s*:\\s*(.*)",RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
            return if(d==null || s==null) "Unspecified" to "Unknown" else d.substringBefore(',').trim() to s
        }
        fun action(contract:JSONObject,defect:String,severity:String):String {
            val d=defect.lowercase(java.util.Locale.ROOT);val s=severity.lowercase(java.util.Locale.ROOT)
            val kind=when {"spalling" in d->"spalling";"crack" in d->"crack";"void" in d->"void";"honeycomb" in d->"honeycombing";listOf("no defect","plain","normal").any {it in d}->"no defect";else->"unspecified"}
            val level=if("high" in s) "high" else if("medium" in s) "medium" else "low"
            return contract.getJSONObject("actions").getJSONObject(kind).getString(level)
        }
    }
}
