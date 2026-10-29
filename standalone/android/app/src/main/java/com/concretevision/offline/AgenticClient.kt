package com.concretevision.offline

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Original file bytes are sent to the laptop, not the phone's preview or resized tensor. */
class AgenticClient(private val origin: String = "http://127.0.0.1:8001") {
    @Volatile private var active: HttpURLConnection? = null
    fun cancel() { active?.disconnect() }

    fun analyze(context: Context,uri: Uri): JSONObject {
        val connection = URL("$origin/analyze").openConnection() as HttpURLConnection
        active=connection
        try {
            val boundary="ConcreteVision-${UUID.randomUUID()}"
            connection.requestMethod="POST"; connection.doOutput=true
            connection.connectTimeout=10_000; connection.readTimeout=150_000
            connection.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
            connection.setChunkedStreamingMode(64*1024)
            connection.outputStream.use { output ->
                output.write(("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"photo\"\r\n"+
                    "Content-Type: application/octet-stream\r\n\r\n").toByteArray(Charsets.UTF_8))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer=ByteArray(64*1024); var total=0
                    while (true) {
                        val count=input.read(buffer); if (count<0) break
                        total+=count; require(total<=ImageLoader.MAX_BYTES) { "Photo exceeds15MiB." }
                        output.write(buffer,0,count)
                    }
                } ?: error("Photo is no longer accessible. Select it again.")
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code=connection.responseCode
            val stream=if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes=stream?.use { it.readBytesLimited(1024*1024) } ?: byteArrayOf()
            val body=runCatching { JSONObject(String(bytes,Charsets.UTF_8)) }.getOrNull()
            if (code !in 200..299) error(body?.optString("detail")?.takeIf { it.isNotBlank() }
                ?: "Laptop server returned HTTP $code.")
            return requireNotNull(body) { "Invalid response from laptop." }.also {
                require(it.getString("mode")=="professor_agentic") { "Wrong analysis server." }
                it.getDouble("gate_score");it.getString("defect");it.getString("severity");it.getString("action")
            }
        } finally { connection.disconnect(); if(active===connection) active=null }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=read(buffer);if(n<0)break
            require(output.size()+n<=limit) { "Laptop response is too large." };output.write(buffer,0,n) }
        return output.toByteArray()
    }
}
