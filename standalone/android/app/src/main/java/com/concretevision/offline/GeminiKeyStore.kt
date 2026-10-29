package com.concretevision.offline
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
object GeminiKeyStore {
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey("concrete-vision-gemini",null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("concrete-vision-gemini",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(context: Context, value: String) {
        val prefs=context.getSharedPreferences("gemini-private",Context.MODE_PRIVATE)
        if(value.isBlank()) {prefs.edit().clear().commit();return}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val encrypted=cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
    }
    fun load(context: Context): String {
        val prefs=context.getSharedPreferences("gemini-private",Context.MODE_PRIVATE)
        val data=prefs.getString("data",null) ?: return ""
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)))}
        return String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),Charsets.UTF_8)
    }
    // Development-only provisioning through the app-private sandbox; immediately encrypt/delete.
    fun importPrivateFile(context: Context) {
        val file=java.io.File(context.filesDir,"gemini-import.txt")
        if(file.isFile) {try {save(context,file.readText())} finally {file.delete()}}
    }
}
