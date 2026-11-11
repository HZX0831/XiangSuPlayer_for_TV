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

    fun read(): PlaybackSnapshot? = runCatching {
        require(file.baseFile.length() <= 2_097_152L)
        gson.fromJson(file.readFully().toString(Charsets.UTF_8), PlaybackSnapshot::class.java)
            ?.takeIf { it.version == 1 && it.mode in PlayMode.entries && it.positionMs >= 0L && it.queue.size in 1..500 &&
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
        val safe = snapshot.copy(queue = snapshot.queue.map { entry ->
            val song = entry.song
            entry.copy(song = song.copy(coverUrl = "", songmid = song.songmid.takeUnless { "://" in it }.orEmpty()))
        })
        try {
            val out = file.startWrite()
            try {
                out.write(gson.toJson(safe).toByteArray(Charsets.UTF_8))
                file.finishWrite(out)
            } catch (failure: Exception) {
                file.failWrite(out)
                throw failure
            }
        } catch (failure: Exception) {
            Log.w("PlaybackSnapshot", "Could not save playback state", failure)
        }
    }
}
