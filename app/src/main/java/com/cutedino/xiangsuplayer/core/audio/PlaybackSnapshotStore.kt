package com.cutedino.xiangsuplayer.core.audio

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import java.io.File

internal data class PlaybackSnapshot(
    val queue: List<PlaybackQueueEntry>,
    val currentId: String,
    val positionMs: Long,
    val mode: PlayMode,
    val version: Int = 1
)

internal class PlaybackSnapshotStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "playback-resume.json"))
    private val gson = Gson()

    private companion object {
        const val MAX_ENTRIES = 500
        const val MAX_BYTES = 2_097_152
    }

    fun read(): PlaybackSnapshot? = runCatching {
        val bytes = file.openRead().use { input ->
            require(input.channel.size() <= MAX_BYTES)
            input.readBytes()
        }
        gson.fromJson(bytes.toString(Charsets.UTF_8), PlaybackSnapshot::class.java)
            ?.takeIf { it.version == 1 && it.mode in PlayMode.entries && it.positionMs >= 0L && it.queue.size in 1..MAX_ENTRIES &&
                it.queue.map(PlaybackQueueEntry::id).distinct().size == it.queue.size &&
                it.queue.any { entry -> entry.id == it.currentId } &&
                it.queue.all { entry -> entry.id.isNotBlank() && entry.song.key.sourceId.isNotBlank() &&
                    entry.song.key.trackId.isNotBlank() && entry.song.title.isNotBlank() } }
    }.getOrNull()

    fun write(snapshot: PlaybackSnapshot?) {
        if (snapshot == null) {
            file.delete()
            return
        }
        try {
            val bytes = encode(snapshot)
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (failure: Exception) {
                file.failWrite(out)
                throw failure
            }
        } catch (failure: Exception) {
            Log.w("PlaybackSnapshot", "Could not save playback state", failure)
        }
    }

    private fun encode(snapshot: PlaybackSnapshot): ByteArray {
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.currentId }
        check(index >= 0)
        val start = (index - MAX_ENTRIES / 2).coerceAtLeast(0)
        val queue = snapshot.queue.drop(start).take(MAX_ENTRIES).map { entry ->
            val song = entry.song
            entry.copy(song = song.copy(coverUrl = "", songmid = song.stableSongMid, playbackHintUrl = null))
        }
        val current = index - start
        var count = queue.size
        while (count > 1) {
            val first = (current - count / 2).coerceIn(0, queue.size - count)
            val bytes = gson.toJson(snapshot.copy(queue = queue.subList(first, first + count))).toByteArray(Charsets.UTF_8)
            if (bytes.size <= MAX_BYTES) return bytes
            count /= 2
        }
        val entry = queue[current]
        val single = snapshot.copy(queue = listOf(entry))
        val bytes = gson.toJson(single).toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_BYTES) return bytes
        val song = entry.song
        val compact = entry.copy(song = song.copy(
            title = song.title.take(2048).ifBlank { song.id.take(2048) },
            artist = song.artist.take(2048),
            album = song.album.take(2048)
        ))
        return gson.toJson(single.copy(queue = listOf(compact))).toByteArray(Charsets.UTF_8).also {
            check(it.size <= MAX_BYTES) { "Current track identifier exceeds resume storage limit" }
        }
    }
}
