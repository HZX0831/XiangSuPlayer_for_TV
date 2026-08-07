package com.cutedino.xiangsuplayer.core.audio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList

enum class PlayMode(val displayName: String, val icon: String) {
    SEQUENCE("顺序播放", "🔁"),
    SHUFFLE("随机播放", "🔀"),
    REPEAT_ONE("单曲循环", "🔂");

    fun next(): PlayMode = when (this) {
        SEQUENCE -> SHUFFLE
        SHUFFLE -> REPEAT_ONE
        REPEAT_ONE -> SEQUENCE
    }
}

object PlayerController {

    private lateinit var exoPlayer: ExoPlayer
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _currentSong = MutableLiveData<Song?>()
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _playMode = MutableLiveData(PlayMode.SEQUENCE)
    val playMode: LiveData<PlayMode> = _playMode

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _lyrics = MutableLiveData<List<LyricLine>>(emptyList())
    val lyrics: LiveData<List<LyricLine>> = _lyrics

    private val _activeLyricIndex = MutableLiveData(-1)
    val activeLyricIndex: LiveData<Int> = _activeLyricIndex

    private val _quality = MutableLiveData(AudioQuality.LOSSLESS)
    val quality: LiveData<AudioQuality> = _quality

    private val playlist = mutableListOf<Song>()
    private val originalPlaylist = mutableListOf<Song>()
    private val historyList = mutableListOf<Song>()

    private val _playHistory = MutableLiveData<List<Song>>(emptyList())
    val playHistory: LiveData<List<Song>> = _playHistory

    private val _playlistLiveData = MutableLiveData<List<Song>>(emptyList())
    val playlistLiveData: LiveData<List<Song>> = _playlistLiveData

    private var currentIndex = -1
    private var progressJob: Job? = null

    fun init(context: Context) {
        exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.postValue(isPlaying)
                if (isPlaying) startProgressTracker() else stopProgressTracker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }
        })
    }

    private fun onSongEnded() {
        when (_playMode.value) {
            PlayMode.REPEAT_ONE -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            else -> playNext()
        }
    }

    fun playSong(song: Song, list: List<Song> = emptyList()) {
        if (list.isNotEmpty()) {
            originalPlaylist.clear()
            originalPlaylist.addAll(list)
            playlist.clear()
            if (_playMode.value == PlayMode.SHUFFLE) {
                val others = list.filterNot { it.id == song.id }.shuffled()
                playlist.add(song)
                playlist.addAll(others)
                currentIndex = 0
            } else {
                playlist.addAll(list)
                currentIndex = playlist.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            }
            _playlistLiveData.postValue(playlist.toList())
        } else if (!playlist.any { it.id == song.id }) {
            originalPlaylist.add(song)
            playlist.add(song)
            currentIndex = playlist.size - 1
            _playlistLiveData.postValue(playlist.toList())
        } else {
            currentIndex = playlist.indexOfFirst { it.id == song.id }
        }

        if (!historyList.any { it.id == song.id }) {
            historyList.add(0, song)
            _playHistory.postValue(historyList.toList())
        }

        _currentSong.postValue(song)
        fetchAndPlayUrl(song)
        fetchLyrics(song)
        fetchCoverIfNeeded(song)
    }

    fun playSongAtIndex(index: Int) {
        if (index in playlist.indices) {
            currentIndex = index
            val song = playlist[index]
            _currentSong.postValue(song)
            fetchAndPlayUrl(song)
            fetchLyrics(song)
            fetchCoverIfNeeded(song)
        }
    }

    private fun fetchCoverIfNeeded(song: Song) {
        if (song.coverUrl.isBlank() || song.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) {
            scope.launch {
                try {
                    val detailRes = NcmApi.songDetail(listOf(song.id))
                    val detailData = detailRes.getOrNull()
                    if (detailData != null) {
                        val detailSongs = detailData.ncmList("songs")
                        if (detailSongs.isNotEmpty()) {
                            val realSong = (detailSongs[0] as? Map<String, Any?>)?.toSong()
                            if (realSong != null && realSong.coverUrl.isNotBlank() && !realSong.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) {
                                _currentSong.postValue(realSong)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun insertAsNext(song: Song) {
        if (playlist.isEmpty()) {
            playSong(song)
            return
        }

        val current = _currentSong.value
        if (current?.id == song.id) return

        val existingIndex = playlist.indexOfFirst { it.id == song.id }
        if (existingIndex != -1) {
            playlist.removeAt(existingIndex)
            if (existingIndex < currentIndex) {
                currentIndex--
            }
        }

        if (!originalPlaylist.any { it.id == song.id }) {
            originalPlaylist.add(song)
        }

        val insertIndex = (currentIndex + 1).coerceIn(0, playlist.size)
        playlist.add(insertIndex, song)
        _playlistLiveData.postValue(playlist.toList())
    }

    private fun fetchAndPlayUrl(song: Song) {
        scope.launch {
            val result = SoundSourceRepository.getStreamUrl(song, _quality.value ?: AudioQuality.LOSSLESS)
            val streamResult = result.getOrNull()
            val playUrl = streamResult?.streamUrl
            if (!playUrl.isNullOrEmpty()) {
                val mediaItem = MediaItem.fromUri(Uri.parse(playUrl))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    private fun fetchLyrics(song: Song) {
        scope.launch {
            val result = SoundSourceRepository.getLyrics(song)
            val lrcList = result.getOrNull() ?: emptyList()
            _lyrics.postValue(lrcList)
            _activeLyricIndex.postValue(-1)
        }
    }

    fun togglePlayPause() {
        if (!::exoPlayer.isInitialized) return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        playSongAtIndex(currentIndex)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playSongAtIndex(currentIndex)
    }

    fun seekTo(positionMs: Long) {
        if (::exoPlayer.isInitialized) {
            exoPlayer.seekTo(positionMs)
        }
    }

    fun seekRelative(deltaMs: Long) {
        if (::exoPlayer.isInitialized) {
            val current = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            val target = (current + deltaMs).coerceIn(0L, dur)
            exoPlayer.seekTo(target)
        }
    }

    fun togglePlayMode() {
        val next = (_playMode.value ?: PlayMode.SEQUENCE).next()
        _playMode.postValue(next)

        val current = _currentSong.value
        if (next == PlayMode.SHUFFLE) {
            if (playlist.isNotEmpty()) {
                val others = if (current != null) playlist.filterNot { it.id == current.id } else playlist
                val shuffled = others.shuffled()
                playlist.clear()
                if (current != null) {
                    playlist.add(current)
                }
                playlist.addAll(shuffled)
                currentIndex = 0
                _playlistLiveData.postValue(playlist.toList())
            }
        } else {
            if (originalPlaylist.isNotEmpty()) {
                playlist.clear()
                playlist.addAll(originalPlaylist)
                if (current != null) {
                    currentIndex = playlist.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                }
                _playlistLiveData.postValue(playlist.toList())
            }
        }
    }

    fun setQuality(quality: AudioQuality) {
        _quality.postValue(quality)
        _currentSong.value?.let { fetchAndPlayUrl(it) }
    }

    fun getHistory(): List<Song> = historyList.toList()

    fun removeFromHistory(song: Song) {
        historyList.removeAll { it.id == song.id }
        _playHistory.postValue(historyList.toList())
    }

    fun clearHistory() {
        historyList.clear()
        _playHistory.postValue(emptyList())
    }

    fun removeFromPlaylist(song: Song) {
        val removeIndex = playlist.indexOfFirst { it.id == song.id }
        if (removeIndex != -1) {
            playlist.removeAt(removeIndex)
            originalPlaylist.removeAll { it.id == song.id }
            if (removeIndex < currentIndex) {
                currentIndex--
            } else if (removeIndex == currentIndex && playlist.isNotEmpty()) {
                currentIndex = currentIndex.coerceIn(0, playlist.size - 1)
                playSongAtIndex(currentIndex)
            }
            _playlistLiveData.postValue(playlist.toList())
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    _currentPosition.value = pos
                    updateActiveLyric(pos)
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
    }

    private fun updateActiveLyric(pos: Long) {
        val list = _lyrics.value ?: return
        if (list.isEmpty()) return
        var active = -1
        for (i in list.indices) {
            if (pos >= list[i].timeMs) {
                active = i
            } else {
                break
            }
        }
        if (active != _activeLyricIndex.value) {
            _activeLyricIndex.postValue(active)
        }
    }
}
