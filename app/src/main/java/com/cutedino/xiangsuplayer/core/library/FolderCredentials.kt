package com.cutedino.xiangsuplayer.core.library

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import okhttp3.Credentials
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object FolderCredentials {
    private const val ALIAS = "pixelplayer.webdav.credentials"
    private const val CIPHER = "AES/GCM/NoPadding"
    private val memory = ConcurrentHashMap<String, String>()
    private fun prefs(context: Context) = context.getSharedPreferences("webdav_credentials", Context.MODE_PRIVATE)

    fun save(context: Context, id: String, username: String, password: String) {
        require(':' !in username)
        saveAuthorization(context, id, if (username.isBlank()) null else Credentials.basic(username, password, Charsets.UTF_8))
    }

    fun saveAuthorization(context: Context, id: String, value: String?) {
        if (value == null) {
            remove(context, id)
            return
        }
        if (Build.VERSION.SDK_INT >= 23) {
            val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val bytes = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            check(prefs(context).edit().putString(id, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit())
        } else memory[id] = value
    }

    fun get(context: Context, id: String): String? {
        if (Build.VERSION.SDK_INT < 23) return memory[id]
        val encrypted = prefs(context).getString(id, null) ?: return null
        val bytes = Base64.decode(encrypted, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        }
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }

    fun remove(context: Context, id: String) {
        memory.remove(id)
        prefs(context).edit().remove(id).apply()
    }

    @RequiresApi(23)
    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }
}
