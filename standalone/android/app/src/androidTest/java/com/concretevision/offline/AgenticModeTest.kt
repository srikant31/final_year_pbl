@file:Suppress("DEPRECATION")
package com.concretevision.offline

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.InetAddress

/** Simulated HTTP responses only: validates mobile networking/UI, not live Gemini. */
class AgenticModeTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val target get()=ins.targetContext
    private val fixtureUri=Uri.parse("content://com.concretevision.offline.test.fixtures/honeycomb.jpg")
    private fun response(called: Boolean) = JSONObject().apply {
        put("mode","professor_agentic");put("gate_score",if(called) .91 else .4)
        put("gemini_called",called);put("gate_verdict",if(called) "Defect Detected" else "No Defect")
        put("gemini_model","gemini-2.5-flash");put("defect",if(called) "Honeycombing" else "No Defect")
        put("severity",if(called) "Medium" else "LOW");put("action","MOCK test action, never live cloud")
    }
    private class OneResponse(val body: String,val code: Int=200):AutoCloseable {
        private val socket=ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))
        val origin="http://127.0.0.1:${socket.localPort}"
        @Volatile var received=byteArrayOf()
        @Volatile var failure: Throwable?=null
        private fun line(input: InputStream): String {
            val bytes=ByteArrayOutputStream()
            while(true) { val n=input.read();check(n>=0);if(n==10)break;if(n!=13)bytes.write(n) }
            return bytes.toString("US-ASCII")
        }
        private val thread=Thread {
            try {
                socket.accept().use { peer ->
                    peer.soTimeout=20_000
                    val input=peer.getInputStream().buffered()
                    check(line(input).startsWith("POST /analyze"))
                    val headers=mutableMapOf<String,String>()
                    while(true) { val h=line(input);if(h.isEmpty())break
                        headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim() }
                    val output=ByteArrayOutputStream();val buffer=ByteArray(65536)
                    fun copy(count: Int) { var left=count
                        while(left>0) {val n=input.read(buffer,0,minOf(buffer.size,left));check(n>0);output.write(buffer,0,n);left-=n} }
                    if(headers["transfer-encoding"]=="chunked") {
                        while(true) {val size=line(input).substringBefore(';').toInt(16)
                            if(size==0) {line(input);break};copy(size);check(line(input).isEmpty()) }
                    } else copy(headers.getValue("content-length").toInt())
                    received=output.toByteArray()
                    val bytes=body.toByteArray(Charsets.UTF_8)
                    peer.getOutputStream().apply {
                        write("HTTP/1.1 $code Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes);flush()
                    }
                }
            } catch(e: Throwable) {failure=e}
        }.apply {isDaemon=true;start()}
        override fun close() {socket.close();thread.join(1000)}
    }
    private fun waitFor(condition:()->Boolean) {
        val end=SystemClock.uptimeMillis()+60_000
        while(SystemClock.uptimeMillis()<end) {var ok=false;ins.runOnMainSync {ok=condition()};if(ok)return;SystemClock.sleep(100)}
        fail("Timed out waiting for online UI")
    }
    private fun text(v: View):String=when(v) {
        is TextView -> v.text.toString()
        is ViewGroup -> (0 until v.childCount).joinToString("\n") {text(v.getChildAt(it))}
        else -> ""
    }

    @Test fun clientUploadsOriginalFileAndReadsBothGateBranches() {
        for(called in listOf(false,true)) OneResponse(response(called).toString()).use { server ->
            val result=AgenticClient(server.origin).analyze(target,fixtureUri)
            assertEquals(called,result.getBoolean("gemini_called"));assertNull(server.failure)
            val expected=ins.context.assets.open("honeycomb.jpg").use {it.readBytes()}
            val b=server.received
            val start=(0 until minOf(1000,b.size-4)).first {b[it]==13.toByte() && b[it+1]==10.toByte() && b[it+2]==13.toByte() && b[it+3]==10.toByte()}+4
            assertArrayEquals(expected,b.copyOfRange(start,start+expected.size))
        }
    }
    @Test fun serverFailureDoesNotBecomeAnOfflineResult() {
        OneResponse("{\"detail\":\"Configure key on laptop\"}",503).use { server ->
            try {AgenticClient(server.origin).analyze(target,fixtureUri);fail("Error became success")}
            catch(e: IllegalStateException) {assertTrue(e.message!!.contains("Configure key"))}
        }
    }
    @Test fun onlineSelectionReportRestoreAndOfflineSwitch() {
        var activity=ins.startActivitySync(Intent(target,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            OneResponse(response(true).toString()).use { server ->
                ins.runOnMainSync {
                    MainActivity::class.java.getDeclaredField("agenticClient").apply {isAccessible=true}.set(activity,AgenticClient(server.origin))
                    activity.findViewById<RadioButton>(MainActivity.ID_MODE_AGENTIC).performClick()
                    MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java)
                        .apply {isAccessible=true}.invoke(activity,100,android.app.Activity.RESULT_OK,Intent().setData(fixtureUri))
                }
                waitFor {activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled}
                ins.runOnMainSync {activity.findViewById<Button>(MainActivity.ID_ANALYZE).performClick()}
                waitFor {activity.findViewById<View>(MainActivity.ID_REPORT).visibility==View.VISIBLE}
                ins.runOnMainSync {
                    val report=text(activity.findViewById(MainActivity.ID_REPORT))
                    assertTrue(report.contains("HONEYCOMBING"));assertTrue(report.contains("0.910000"));assertTrue(report.contains("Gemini: called"))
                }
            }
            val observer=ins.addMonitor(MainActivity::class.java.name,null,false)
            ins.runOnMainSync {activity.recreate()}
            val restored=observer.waitForActivityWithTimeout(15_000) as? MainActivity
            ins.removeMonitor(observer);assertNotNull(restored);activity=restored!!
            waitFor {activity.findViewById<View>(MainActivity.ID_REPORT).visibility==View.VISIBLE}
            ins.runOnMainSync {
                assertTrue(activity.findViewById<RadioButton>(MainActivity.ID_MODE_AGENTIC).isChecked)
                activity.findViewById<RadioButton>(MainActivity.ID_MODE_OFFLINE).performClick()
                assertEquals(View.GONE,activity.findViewById<View>(MainActivity.ID_REPORT).visibility)
                assertTrue(activity.findViewById<Button>(MainActivity.ID_CAMERA).isEnabled)
                assertTrue(activity.findViewById<Button>(MainActivity.ID_CHOOSE).isEnabled)
            }
        } finally {ins.runOnMainSync {activity.findViewById<Button>(MainActivity.ID_RESET).performClick();activity.finish()} }
    }
}
