package net.moriafly.ncm

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import kotlin.coroutines.resumeWithException

/** Read the response while the coroutine can still cancel the network call. */
suspend fun <T> Call.readResponse(read: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(read) })
            }
        })
    }

/** Limits bytes actually read, including automatically decompressed OkHttp responses. */
fun ResponseBody.readBounded(maxBytes: Int): ByteArray {
    if (contentLength() > maxBytes) throw IOException("Response exceeds $maxBytes bytes")
    return byteStream().readBounded(maxBytes)
}

internal fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(8192, maxBytes))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, maxBytes - output.size() + 1))
        if (count == -1) return output.toByteArray()
        if (count > maxBytes - output.size()) throw IOException("Response exceeds $maxBytes bytes")
        output.write(buffer, 0, count)
    }
}
