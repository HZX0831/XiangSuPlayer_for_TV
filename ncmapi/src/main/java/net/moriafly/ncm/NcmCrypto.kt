@file:Suppress("FunctionName", "MemberVisibilityCanBePrivate", "unused")

package net.moriafly.ncm

import android.util.Base64
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 网易云音乐加密核心 —— Kotlin 纯 JDK 移植
 */
object NcmCrypto {

    private const val IV = "0102030405060708"
    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val LINUXAPI_KEY = "rFgB&h#%2?^eDg:Q"
    private const val EAPI_KEY = "e82ckenh8dichen8"
    private const val BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    private const val PUBLIC_KEY_PEM = """
-----BEGIN PUBLIC KEY-----
MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB
-----END PUBLIC KEY-----"""

    private val rsaPublicKey: RSAPublicKey by lazy {
        val pem = PUBLIC_KEY_PEM
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("-----") }
            .joinToString("")
        val der = Base64.decode(pem, Base64.DEFAULT)
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der)) as RSAPublicKey
    }

    fun weapi(obj: Map<String, Any?>): WeapiResult {
        val text = NcmJson.toJsonString(obj)
        val secretKey = randomBase62(16)
        val encFirst = aesCbcEncryptToBase64(text, PRESET_KEY, IV)
        val encSecond = aesCbcEncryptToBase64(encFirst, secretKey, IV)
        val encSecKey = rsaEncryptNoPadding(secretKey.reversed())
        return WeapiResult(params = encSecond, encSecKey = encSecKey)
    }

    fun linuxapi(obj: Map<String, Any?>): LinuxapiResult {
        val text = NcmJson.toJsonString(obj)
        val hex = aesEcbEncryptToHex(text, LINUXAPI_KEY)
        return LinuxapiResult(eparams = hex)
    }

    fun eapi(url: String, obj: Any?): EapiResult {
        val json = when (obj) {
            null -> ""
            is Map<*, *> -> NcmJson.toJsonString(obj as Map<String, Any?>)
            is String -> obj
            else -> obj.toString()
        }
        val message = "nobody${url}use${json}md5forencrypt"
        val digest = md5(message)
        val data = "${url}-36cd479b6b5-${json}-36cd479b6b5-${digest}"
        return EapiResult(params = aesEcbEncryptToHex(data, EAPI_KEY))
    }

    fun eapiResDecrypt(encryptedHex: String): Any? {
        val json = aesEcbDecryptFromHex(encryptedHex, EAPI_KEY)
        return runCatching { NcmJson.parseAny(json) }.getOrNull()
    }

    data class WeapiResult(val params: String, val encSecKey: String) {
        fun toFormBody(): String = "params=${uenc(params)}&encSecKey=${uenc(encSecKey)}"
    }

    data class LinuxapiResult(val eparams: String) {
        fun toFormBody(): String = "eparams=${uenc(eparams)}"
    }

    data class EapiResult(val params: String) {
        fun toFormBody(): String = "params=${uenc(params)}"
    }

    private fun randomBase62(len: Int): String {
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append(BASE62[Random.nextInt(62)])
        return sb.toString()
    }

    private fun aesCbcEncryptToBase64(plain: String, key: String, iv: String): String {
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv.toByteArray(Charsets.UTF_8)),
        )
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun aesEcbEncryptToHex(plain: String, key: String): String {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        return c.doFinal(plain.toByteArray(Charsets.UTF_8)).toHexUpper()
    }

    private fun aesEcbDecryptFromHex(hex: String, key: String): String {
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"))
        val bytes = c.doFinal(hex.hexToBytes())
        return String(bytes, Charsets.UTF_8)
    }

    private fun rsaEncryptNoPadding(plain: String): String {
        val modulusBytes = (rsaPublicKey.modulus.bitLength() + 7) / 8
        val plainBytes = plain.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= modulusBytes) { "RSA plain longer than modulus ($modulusBytes)" }
        val padded = ByteArray(modulusBytes)
        System.arraycopy(plainBytes, 0, padded, modulusBytes - plainBytes.size, plainBytes.size)
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        return cipher.doFinal(padded).toHexLower()
    }

    private fun md5(text: String): String {
        val d = MessageDigest.getInstance("MD5")
        return d.digest(text.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    private fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }
    private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex string length must be even" }
        return chunkedSequence(2).map { it.toInt(16).toByte() }.toList().toByteArray()
    }
    private fun uenc(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
