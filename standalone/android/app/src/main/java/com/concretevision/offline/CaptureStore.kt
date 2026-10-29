package com.concretevision.offline

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class CaptureFileProvider : FileProvider()

/** Only app-owned camera scratch files, never gallery photos or model files. */
class CaptureStore(private val context: Context) {
    private val directory get() = File(context.filesDir,"captures").apply {
        check(isDirectory || mkdirs()) { "Cannot create camera storage." }
    }
    private val authority get() = "${context.packageName}.captures"
    fun file(name: String): File {
        require(Regex("capture_[0-9a-f-]{36}\\.jpg").matches(name)) { "Invalid capture filename" }
        return File(directory,name)
    }
    fun create(): File = file("capture_${UUID.randomUUID()}.jpg").apply {
        check(createNewFile()) { "Cannot create camera output." }
    }
    fun uri(name: String): Uri = FileProvider.getUriForFile(context,authority,file(name))
    fun delete(name: String?) { if (name != null) runCatching { file(name).delete() } }
    fun deleteOwned(uri: Uri?) {
        if (uri?.scheme == "content" && uri.authority == authority) delete(uri.lastPathSegment)
    }
    fun cleanupOld(keep: Set<String>) {
        val cutoff = System.currentTimeMillis()-24*60*60*1000L
        directory.listFiles()?.filter { it.name !in keep && it.lastModified() < cutoff &&
            Regex("capture_[0-9a-f-]{36}\\.jpg").matches(it.name) }?.forEach { it.delete() }
    }
}
