@file:Suppress("unused", "MemberVisibilityCanBePrivate", "SpellCheckingInspection", "LongParameterList", "CanBeParameter")

package net.moriafly.ncm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * 网易云核心业务模块
 */
internal object NcmModules {

    suspend fun searchDefault(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = mapOf("scene" to "normal")
            NcmRequest.weapi(
                path = "/api/search/defaultkeyword",
                params = params,
            ).getOrThrow().body
        }
    }

    suspend fun search(
        keyword: String,
        type: Int = 1,
        limit: Int = 30,
        offset: Int = 0,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf(
                "s" to keyword,
                "type" to type,
                "limit" to limit,
                "offset" to offset,
            )
            NcmRequest.weapi(
                path = "/api/search/get",
                params = params,
            ).getOrThrow().body
        }
    }

    suspend fun songUrlV1(
        ids: List<String>,
        level: String = "standard",
        encodeType: String = "flac",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val idsParam = ids.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
            val params = linkedMapOf(
                "ids" to idsParam,
                "level" to level,
                "encodeType" to encodeType,
            )
            if (level == "sky") params["immerseType"] = "c51"
            NcmRequest.weapi(
                path = "/api/song/enhance/player/url/v1",
                params = params,
            ).getOrThrow().body
        }
    }

    suspend fun checkMusicPlayable(
        id: String,
        br: Int = 320_000,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/song/enhance/player/url",
                params = linkedMapOf(
                    "ids" to "[$id]",
                    "br" to br.toString(),
                ),
            ).getOrThrow().body
        }
    }

    suspend fun songDetail(ids: List<String>): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val c = ids.joinToString(",", "[", "]") { "{\"id\":$it}" }
            NcmRequest.weapi(
                path = "/api/v3/song/detail",
                params = linkedMapOf(
                    "c" to c,
                ),
            ).getOrThrow().body
        }
    }

    suspend fun songLyric(id: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/song/lyric",
                params = linkedMapOf(
                    "id" to id,
                    "lv" to "-1",
                    "tv" to "-1",
                    "rv" to "-1",
                    "kv" to "-1",
                ),
            ).getOrThrow().body
        }
    }

    suspend fun sentSmsCaptcha(
        phone: String,
        ctcode: String = "86",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf(
                "ctcode" to ctcode,
                "cellphone" to phone,
            )
            NcmRequest.weapi(
                path = "/api/sms/captcha/sent",
                params = params,
            ).getOrThrow().body
        }
    }

    suspend fun captchaVerify(
        phone: String,
        captcha: String,
        ctcode: String = "86",
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/sms/captcha/verify",
                params = linkedMapOf(
                    "ctcode" to ctcode,
                    "cellphone" to phone,
                    "captcha" to captcha,
                ),
            ).getOrThrow().body
        }
    }

    suspend fun loginCellphone(
        phone: String,
        captcha: String? = null,
        password: String? = null,
        ctcode: String = "86",
        countrycode: String? = null,
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = linkedMapOf<String, Any?>(
                "cellphone" to phone,
                "countrycode" to (countrycode ?: ctcode),
                "rememberLogin" to "true",
                "ctcode" to ctcode,
            )
            when {
                captcha != null -> params["captcha"] = captcha
                password != null -> params["password"] = passwordMd5(password)
                else -> error("loginCellphone: captcha and password required")
            }
            val r = NcmRequest.weapi(
                path = "/api/w/login/cellphone",
                params = params,
            ).getOrThrow()

            val bodyCookie = (r.body["cookie"] as? String)?.takeIf { it.isNotBlank() }
            if (bodyCookie != null) {
                val sess = NcmSession.INSTANCE
                if (sess != null) {
                    val map = bodyCookie.split(';')
                        .map { it.trim() }
                        .filter { '=' in it }
                        .associate { val (k, v) = it.split('=', limit = 2); k to v }
                    sess.merge(map)
                }
            }
            r.body
        }
    }

    suspend fun loginRefresh(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/w/login/status",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    suspend fun logout(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmSession.INSTANCE?.logout()
            NcmRequest.weapi(
                path = "/api/logout",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    data class QrKeyResult(val key: String, val code: Int)

    suspend fun qrLoginKey(): Result<QrKeyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val r1 = runCatching {
                NcmRequest.weapi(
                    path = "/api/login/qrcode/unikey",
                    params = linkedMapOf("type" to 1),
                ).getOrThrow()
            }.getOrNull()

            val k1 = ((r1?.body?.get("data") as? Map<*, *>) ?: r1?.body)?.get("unikey") as? String
                ?: (r1?.body?.get("unikey") as? String)
            if (!k1.isNullOrBlank()) {
                return@runCatching QrKeyResult(k1, r1?.code ?: 200)
            }

            val unikey = UUID.randomUUID().toString().replace("-", "")
            val r2 = NcmRequest.weapi(
                path = "/api/w/login/qr/unikey",
                params = linkedMapOf(
                    "type" to 1,
                    "unikey" to unikey,
                ),
            ).getOrThrow()

            val body2 = r2.body
            val data2 = (body2["data"] as? Map<*, *>) ?: body2
            val k2 = (data2["unikey"] as? String)
                ?: (body2["unikey"] as? String)
                ?: error("Failed to fetch unikey")
            QrKeyResult(k2, r2.code)
        }
    }

    suspend fun qrLoginImage(key: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://music.163.com/login?codekey=$key"
            val resp = runCatching {
                NcmRequest.weapi(
                    path = "/api/w/login/qr/create",
                    params = linkedMapOf("key" to key, "url" to url, "qrimg" to true),
                ).getOrThrow().body
            }.getOrNull()

            val d = resp?.get("data") as? Map<*, *> ?: resp
            val qrimg = d?.get("qrimg") as? String
            if (!qrimg.isNullOrBlank()) {
                qrimg
            } else {
                generateQrCodeBase64(url)
            }
        }
    }

    private fun generateQrCodeBase64(content: String, width: Int = 300, height: Int = 300): String {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, width, height)
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        val stream = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        return "data:image/png;base64," + android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    suspend fun qrLoginCheck(key: String): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val r = runCatching {
                NcmRequest.weapi(
                    path = "/api/login/qrcode/client/login",
                    params = linkedMapOf("type" to 1, "key" to key),
                ).getOrThrow()
            }.getOrElse {
                NcmRequest.weapi(
                    path = "/api/w/login/qr/check",
                    params = linkedMapOf("type" to 1, "key" to key),
                ).getOrThrow()
            }

            val code = r.body.ncmInt("code", -1)
            if (code == 803) {
                val cookie = r.body.ncmString("cookie")
                if (cookie.isNotBlank()) {
                    val sess = NcmSession.INSTANCE
                    if (sess != null) {
                        val map = cookie.split(';')
                            .map { it.trim() }
                            .filter { '=' in it }
                            .associate { val (k, v) = it.split('=', limit = 2); k to v }
                        sess.merge(map)
                    }
                }
            }
            r.body
        }
    }

    suspend fun qrLoginAwait(key: String, onStatus: suspend (Int, Map<String, Any?>) -> Unit = { _, _ -> }): Result<Map<String, Any?>> = coroutineScope {
        withTimeout(120_000L) {
            var last: Map<String, Any?> = emptyMap()
            while (true) {
                val r = qrLoginCheck(key).getOrThrow()
                last = r
                val code = r.ncmInt("code", -1)
                onStatus(code, r)
                when (code) {
                    803 -> return@withTimeout Result.success(r)
                    800 -> return@withTimeout Result.failure(IllegalStateException("QR expired"))
                    801, 802 -> {}
                    else -> return@withTimeout Result.failure(IllegalStateException("Unknown status code=$code"))
                }
                delay(1_500L)
            }
            @Suppress("UNREACHABLE_CODE")
            Result.success(last)
        }
    }

    suspend fun banner(type: Int = 1): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/banner/get/v3",
                params = linkedMapOf(
                    "type" to type.toString(),
                    "clientType" to "pc",
                    "time" to System.currentTimeMillis().toString(),
                ),
            ).getOrThrow().body
        }
    }

    suspend fun playlistDetail(
        id: String,
        s: Int = 8,
        n: Int = 100000,
        k: Long = System.currentTimeMillis(),
    ): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                val d1 = async {
                    NcmRequest.weapi(
                        path = "/api/v6/playlist/detail",
                        params = linkedMapOf(
                            "id" to id,
                            "n" to n.toString(),
                            "s" to s.toString(),
                            "k" to k.toString(),
                        ),
                    ).getOrThrow().body
                }
                val d2 = async {
                    val ids = d1.await()["playlist"]?.let { p ->
                        (p as? Map<*, *>)?.get("trackIds") as? List<*>
                    }?.take(200)
                        ?.mapNotNull { (it as? Map<*, *>)?.get("id").toString() }
                        ?: emptyList()
                    if (ids.isEmpty()) emptyMap<String, Any?>()
                    else songDetail(ids).getOrDefault(emptyMap())
                }
                val a = d1.await()
                val b = d2.await()
                buildMap<String, Any?>(a.size + b.size + 1) {
                    putAll(a)
                    if (b.isNotEmpty()) put("_songs_detail", b)
                }
            }
        }
    }

    suspend fun recommendSongs(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/v3/discovery/recommend/songs",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    suspend fun recommendPlaylists(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/v1/discovery/recommend/resource",
                params = emptyMap(),
            ).getOrThrow().body
        }
    }

    suspend fun personalizedPlaylists(limit: Int = 10): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/personalized/playlist",
                params = mapOf("limit" to limit, "total" to true, "n" to 1000),
            ).getOrThrow().body
        }
    }

    suspend fun userAccount(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            val res1 = runCatching {
                NcmRequest.weapi(
                    path = "/api/nuser/account/get",
                    params = emptyMap(),
                ).getOrThrow()
            }.getOrNull()

            if (res1 != null && res1.body.ncmInt("code") == 200) {
                res1.body
            } else {
                NcmRequest.weapi(
                    path = "/api/w/nuser/account/get",
                    params = emptyMap(),
                ).getOrThrow().body
            }
        }
    }

    suspend fun userPlaylist(uid: String, limit: Int = 30, offset: Int = 0): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        runCatching {
            NcmRequest.weapi(
                path = "/api/user/playlist",
                params = linkedMapOf(
                    "uid" to uid,
                    "limit" to limit.toString(),
                    "offset" to offset.toString(),
                ),
            ).getOrThrow().body
        }
    }

    private fun passwordMd5(raw: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
