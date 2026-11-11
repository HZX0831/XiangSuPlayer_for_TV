package com.cutedino.xiangsuplayer.core.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.source.StreamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID


data class PlaybackQueueEntry(val id: String, val song: Song) {
    constructor(song: Song) : this(UUID.randomUUID().toString(), song)
}

data class PlaybackUiState(
    val queue: List<PlaybackQueueEntry> = emptyList(),
    val currentEntryId: String? = null,
    val song: Song? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val seekable: Boolean = false
)

internal class PlaybackState {
    val currentSong = MutableLiveData<Song?>()
    val isPlaying = MutableLiveData(false)
    val playMode = MutableLiveData(PlayMode.SEQUENCE)
    val currentPosition = MutableStateFlow(0L)
    val duration = MutableStateFlow(0L)
    val lyrics = MutableLiveData<List<LyricLine>>(emptyList())
    val activeLyricIndex = MutableLiveData(-1)
    val quality = MutableLiveData(AudioQuality.LOSSLESS)
    val activeStreamResult = MutableStateFlow<StreamResult?>(null)
    val isCurrentFavorite = MutableStateFlow(false)
    val isPersonalFmMode = MutableStateFlow(false)
    val favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val playHistory = MutableLiveData<List<Song>>(emptyList())
    val playlistLiveData = MutableLiveData<List<Song>>(emptyList())
    val currentQueueIndex = MutableStateFlow(-1)
    val playbackError = MutableStateFlow<String?>(null)
    val ui = MutableStateFlow(PlaybackUiState())
}

object PlayerController {
    private val state = PlaybackState()
    private var engine: PlaybackEngine? = null
    private val pending = ArrayDeque<(PlaybackEngine) -> Unit>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val playback = (binder as PlaybackService.LocalBinder).engine
            engine = playback
            while (pending.isNotEmpty()) pending.removeFirst().invoke(playback)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            engine = null
        }
    }

    val uiState: StateFlow<PlaybackUiState> = state.ui
    val currentSong: LiveData<Song?> = state.currentSong
    val isPlaying: LiveData<Boolean> = state.isPlaying
    val playMode: LiveData<PlayMode> = state.playMode
    val currentPosition: StateFlow<Long> = state.currentPosition
    val duration: StateFlow<Long> = state.duration
    val lyrics: LiveData<List<LyricLine>> = state.lyrics
    val activeLyricIndex: LiveData<Int> = state.activeLyricIndex
    val quality: LiveData<AudioQuality> = state.quality
    val activeStreamResult: StateFlow<StreamResult?> = state.activeStreamResult
    val isCurrentFavorite: StateFlow<Boolean> = state.isCurrentFavorite
    val isPersonalFmMode: StateFlow<Boolean> = state.isPersonalFmMode
    val favoriteSongs: StateFlow<List<Song>> = state.favoriteSongs
    val playHistory: LiveData<List<Song>> = state.playHistory
    val playlistLiveData: LiveData<List<Song>> = state.playlistLiveData
    val currentQueueIndex: StateFlow<Int> = state.currentQueueIndex
    val playbackError: StateFlow<String?> = state.playbackError

    fun init(context: Context) {
        val intent = Intent(context, PlaybackService::class.java).setAction(PlaybackService.LOCAL_ACTION)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    internal fun createEngine(context: Context, player: androidx.media3.exoplayer.ExoPlayer) =
        PlaybackEngine(context.applicationContext, player, state)

    internal fun detach(playback: PlaybackEngine) {
        if (engine === playback) engine = null
    }

    private fun dispatch(action: (PlaybackEngine) -> Unit) {
        val playback = engine
        if (playback != null) action(playback)
        else if (pending.size < 32) pending.addLast(action)
    }

    fun playSong(song: Song, list: List<Song> = emptyList(), isFm: Boolean = false) =
        dispatch { it.playSong(song, list, isFm) }
    fun playSongAtIndex(index: Int) = dispatch { it.playSongAtIndex(index) }
    fun playQueueEntry(id: String) = dispatch { it.playQueueEntry(id) }
    fun startPersonalFm(onLoaded: ((Boolean) -> Unit)? = null) = dispatch { it.startPersonalFm(onLoaded) }
    fun startHeartMode(onLoaded: ((Boolean) -> Unit)? = null) = dispatch { it.startHeartMode(onLoaded) }
    fun toggleFavorite(song: Song? = currentSong.value) = dispatch { it.toggleFavorite(song) }
    fun switchSource(sourceId: String) = dispatch { it.switchSource(sourceId) }
    fun insertAsNext(song: Song) = dispatch { it.insertAsNext(song) }
    fun moveQueueEntryNext(id: String) = dispatch { it.moveQueueEntryNext(id) }
    fun play() = dispatch { it.play() }
    fun pause() = dispatch { it.pause() }
    fun togglePlayPause() = dispatch { it.togglePlayPause() }
    fun playNext() = dispatch { it.playNext() }
    fun playPrevious() = dispatch { it.playPrevious() }
    fun seekTo(positionMs: Long) = dispatch { it.seekTo(positionMs) }
    fun seekRelative(deltaMs: Long) = dispatch { it.seekRelative(deltaMs) }
    fun togglePlayMode() = dispatch { it.togglePlayMode() }
    fun setQuality(quality: AudioQuality) = dispatch { it.setQuality(quality) }
    fun getHistory(): List<Song> = playHistory.value.orEmpty()
    fun removeFromHistory(song: Song) = dispatch { it.removeFromHistory(song) }
    fun clearHistory() = dispatch { it.clearHistory() }
    fun removeFromPlaylist(song: Song) = dispatch { it.removeFromPlaylist(song) }
    fun removeFromPlaylistAt(index: Int) = dispatch { it.removeFromPlaylistAt(index) }
    fun removeQueueEntry(id: String) = dispatch { it.removeQueueEntry(id) }
    fun beginKaraoke() = dispatch { it.beginKaraoke() }
    fun enqueueKaraoke(song: Song): Boolean = engine?.enqueueKaraoke(song) == true
    fun endKaraoke() = dispatch { it.endKaraoke() }
}
