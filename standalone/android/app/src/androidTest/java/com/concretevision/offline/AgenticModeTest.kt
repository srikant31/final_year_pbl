package com.concretevision.offline
import android.net.Uri
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
class AgenticModeTest {
    private val ins get()=InstrumentationRegistry.getInstrumentation()
    private val target get()=ins.targetContext
    private val photo=Uri.parse("content://com.concretevision.offline.test.fixtures/honeycomb.jpg")
    private fun response()=JSONObject("""{"candidates":[{"content":{"parts":[{"text":"Defect Type: Honeycombing\nSeverity: Low"}]}}]}""")
    @Test fun phoneBicubicMatchesOriginalPillowPixelsAndGateLoads() {
        PhoneGate(target).use {gate->
            for(name in listOf("crack","honeycomb","spalling","void")) {
                val bitmap=ins.context.assets.open("$name.png").use {ImageLoader.decode(it)}
                try {
                    val actual=AgenticPreprocessor.resizedRgb(bitmap)
                    val expected=ins.context.assets.open("$name.agentic.rgb").use {it.readBytes()}
                    for(i in actual.indices) {
                        assertEquals("$name red $i",expected[i*3].toInt() and 255,(actual[i] ushr 16) and 255)
                        assertEquals("$name green $i",expected[i*3+1].toInt() and 255,(actual[i] ushr 8) and 255)
                        assertEquals("$name blue $i",expected[i*3+2].toInt() and 255,actual[i] and 255)
                    }
                    assertEquals(1.0f,gate.score(bitmap),0.00001f)
                } finally {bitmap.recycle()}
            }
        }
    }
    @Test fun originalParserActionsAndPhoneKeyEncryption() {
        val contract=JSONObject(target.assets.open("agentic_contract.json").bufferedReader().use {it.readText()})
        val cases=JSONArray(ins.context.assets.open("agentic-rules.json").bufferedReader().use {it.readText()})
        for(i in 0 until cases.length()) {val c=cases.getJSONObject(i);assertEquals(c.getString("action"),AgenticClient.action(contract,c.getString("defect"),c.getString("severity")))}
        assertEquals("Crack" to "Low",AgenticClient.parse("Defect Type: Crack, extra\nSeverity: Low"))
        assertEquals("Unspecified" to "Unknown",AgenticClient.parse("bad output"))
        GeminiKeyStore.save(target,"test-not-a-real-key")
        assertEquals("test-not-a-real-key",GeminiKeyStore.load(target))
        val prefs=target.getSharedPreferences("gemini-private",0)
        assertFalse(prefs.getString("data","")!!.contains("test-not-a-real-key"))
        GeminiKeyStore.save(target,"")
    }
    @Test fun directCloudFallbackKeepsOriginalPayloadAndSkippedGateDoesNotNeedKey() {
        GeminiKeyStore.save(target,"test-not-a-real-key")
        try {
            var first:JSONObject?=null;val models=mutableListOf<String>()
            AgenticClient(cloud={model,payload,_,_->
                models.add(model)
                if(first==null) {first=payload;throw java.net.SocketTimeoutException("test")}
                assertSame(first,payload);response()
            },gateOverride={.9f}).use {client ->
                val result=client.analyze(target,photo)
                assertEquals(listOf("gemini-3.8-flash","gemini-3.6-flash"),models)
                assertTrue(result.getBoolean("gemini_fallback_used"));assertEquals("Honeycombing",result.getString("defect"))
            }
            GeminiKeyStore.save(target,"")
            AgenticClient(cloud={_,_,_,_->error("Gate must skip cloud")},gateOverride={.75f}).use {client->assertFalse(client.analyze(target,photo).getBoolean("gemini_called"))}
        } finally {GeminiKeyStore.save(target,"")}
    }
    @Test fun phoneUiRetainsOfflineCameraPickerAndShowsAgenticReport() {
        val activity=ins.startActivitySync(Intent(target,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        GeminiKeyStore.save(target,"test-not-a-real-key")
        try {
            ins.runOnMainSync {
                MainActivity::class.java.getDeclaredField("agenticClient").apply {isAccessible=true}.set(activity,AgenticClient(cloud={_,_,_,_->response()},gateOverride={.9f}))
                activity.findViewById<RadioButton>(MainActivity.ID_MODE_AGENTIC).performClick()
                MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply {isAccessible=true}
                    .invoke(activity,100,android.app.Activity.RESULT_OK,Intent().setData(photo))
            }
            val end=SystemClock.elapsedRealtime()+60000
            while(!activity.findViewById<Button>(MainActivity.ID_ANALYZE).isEnabled && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            ins.runOnMainSync {activity.findViewById<Button>(MainActivity.ID_ANALYZE).performClick()}
            while(activity.findViewById<View>(MainActivity.ID_REPORT).visibility!=View.VISIBLE && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100)
            assertEquals(View.VISIBLE,activity.findViewById<View>(MainActivity.ID_REPORT).visibility)
            ins.runOnMainSync {activity.findViewById<RadioButton>(MainActivity.ID_MODE_OFFLINE).performClick();assertTrue(activity.findViewById<Button>(MainActivity.ID_CAMERA).isEnabled);assertTrue(activity.findViewById<Button>(MainActivity.ID_CHOOSE).isEnabled)}
        } finally {ins.runOnMainSync {activity.finish()};GeminiKeyStore.save(target,"")}
    }
    @Test fun livePhoneDirectGeminiWithoutLaptopEndpoint() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePhone")=="yes")
        GeminiKeyStore.importPrivateFile(target)
        AgenticClient().use {client ->
            val result=client.analyze(target,photo)
            File(target.filesDir,"phone-live-result.json").writeText(result.toString(2))
            assertTrue(result.getBoolean("gemini_called"));assertTrue(result.getString("defect").isNotBlank())
        }
    }
}
