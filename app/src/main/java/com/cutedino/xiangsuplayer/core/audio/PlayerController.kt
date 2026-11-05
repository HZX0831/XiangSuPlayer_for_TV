package com.cutedino.xiangsuplayer.core.audio

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.StreamResult
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.db.AppDatabase
import com.cutedino.xiangsuplayer.data.db.toFavoriteEntity
import com.cutedino.xiangsuplayer.data.db.toHistoryEntity
import com.cutedino.xiangsuplayer.data.session.SessionManager
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
import kotlinx.coroutines.withContext
import net.moriafly.ncm.NcmApi
import net.moriafly.ncm.ncmList

enum class PlayMode(val displayName: String, val icon: String) {
    SEQUENCE("顺序播放", "🔁"),
    SHUFFLE("随机播放", "🔀"),
    REPEAT_ONE("单曲循环", "🔂"),
    HEART_MODE("心动模式", "💓");

    fun next(): PlayMode = when (this) {
        SEQUENCE -> SHUFFLE
        SHUFFLE -> REPEAT_ONE
        REPEAT_ONE -> HEART_MODE
        HEART_MODE -> SEQUENCE
    }
}

object PlayerController {

    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSessionCompat? = null
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

    private val _activeStreamResult = MutableStateFlow<StreamResult?>(null)
    val activeStreamResult: StateFlow<StreamResult?> = _activeStreamResult

    private val _isCurrentFavorite = MutableStateFlow(false)
    val isCurrentFavorite: StateFlow<Boolean> = _isCurrentFavorite

    private val _isPersonalFmMode = MutableStateFlow(false)
    val isPersonalFmMode: StateFlow<Boolean> = _isPersonalFmMode

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs

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
        _quality.value = SessionManager.getAudioQuality(context)
        exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.postValue(isPlaying)
                updateMediaSessionState()
                if (isPlaying) startProgressTracker() else stopProgressTracker()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    updateMediaSessionState()
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }
        })

        setupMediaSession(context)
        loadPersistedRoomData()
    }

    private fun setupMediaSession(context: Context) {
        runCatching {
            val session = MediaSessionCompat(context, "XiangSuPlayerTVSession")
            session.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = togglePlayPause()
                override fun onPause() = togglePlayPause()
                override fun onSkipToNext() = playNext()
                override fun onSkipToPrevious() = playPrevious()
                override fun onSeekTo(pos: Long) = seekTo(pos)
            })
            session.isActive = true
            mediaSession = session
        }
    }

    private fun updateMediaSessionState() {
        val session = mediaSession ?: return
        val song = _currentSong.value
        val playing = _isPlaying.value == true
        val pos = if (::exoPlayer.isInitialized) exoPlayer.currentPosition else 0L
        val dur = _duration.value

        runCatching {
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(
                    if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    pos,
                    1.0f
                )
            session.setPlaybackState(stateBuilder.build())

            if (song != null) {
                val meta = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.coverUrl)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur)
                    .build()
                session.setMetadata(meta)
            }
        }
    }

    private fun loadPersistedRoomData() {
        scope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstanceOrNull() ?: return@launch
            val dao = db.songDao()

            // 加载持久化播放历史
            val persistedHistory = runCatching { dao.getPlayHistoryList().map { it.toSong() } }.getOrDefault(emptyList())
            if (persistedHistory.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    historyList.clear()
                    historyList.addAll(persistedHistory)
                    _playHistory.value = historyList.toList()
                }
            }

            // 监听收藏歌曲变化
            dao.getAllFavorites().collect { entities ->
                val favs = entities.map { it.toSong() }
                _favoriteSongs.value = favs
                val curId = _currentSong.value?.id
                if (curId != null) {
                    _isCurrentFavorite.value = favs.any { it.id == curId }
                }
            }
        }
    }

    private fun onSongEnded() {
        when (_playMode.value) {
            PlayMode.REPEAT_ONE -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            else -> {
                if (_isPersonalFmMode.value && currentIndex >= playlist.size - 2) {
                    appendMorePersonalFm()
                }
                playNext()
            }
        }
    }

    fun playSong(song: Song, list: List<Song> = emptyList(), isFm: Boolean = false) {
        _isPersonalFmMode.value = isFm
        if (list.isNotEmpty()) {
            originalPlaylist.clear()
            originalPlaylist.addAll(list)
            playlist.clear()
            if (_playMode.value == PlayMode.SHUFFLE && !isFm) {
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

        recordHistory(song)
        activateSong(song)
    }

    fun playSongAtIndex(index: Int) {
        if (index in playlist.indices) {
            currentIndex = index
            val song = playlist[index]
            recordHistory(song)
            activateSong(song)
        }
    }

    private fun activateSong(song: Song) {
        _currentSong.postValue(song)
        _isCurrentFavorite.value = _favoriteSongs.value.any { it.id == song.id }
        fetchAndPlayUrl(song)
        fetchLyrics(song)
        fetchCoverIfNeeded(song)
        updateMediaSessionState()
    }

    private fun recordHistory(song: Song) {
        historyList.removeAll { it.id == song.id }
        historyList.add(0, song)
        if (historyList.size > 200) {
            historyList.removeAt(historyList.lastIndex)
        }
        _playHistory.postValue(historyList.toList())

        scope.launch(Dispatchers.IO) {
            runCatching {
                AppDatabase.getInstanceOrNull()?.songDao()?.insertHistory(song.toHistoryEntity())
            }
        }
    }

    /**
     * 启动私人 FM 漫游模式
     */
    fun startPersonalFm(onLoaded: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val res = MultiPlatformAccountManager.fetchPersonalFmSongs()
            val fmSongs = res.getOrDefault(emptyList())
            if (fmSongs.isNotEmpty()) {
                playSong(fmSongs.first(), fmSongs, isFm = true)
                onLoaded?.invoke(true)
            } else {
                onLoaded?.invoke(false)
            }
        }
    }

    private fun appendMorePersonalFm() {
        scope.launch {
            val more = MultiPlatformAccountManager.fetchPersonalFmSongs().getOrDefault(emptyList())
            if (more.isNotEmpty()) {
                val filtered = more.filterNot { m -> playlist.any { it.id == m.id } }
                playlist.addAll(filtered)
                originalPlaylist.addAll(filtered)
                _playlistLiveData.postValue(playlist.toList())
            }
        }
    }

    /**
     * 启动心动智能推荐模式 (Heart Mode)
     */
    fun startHeartMode(onLoaded: ((Boolean) -> Unit)? = null) {
        scope.launch {
            val seedId = _currentSong.value?.id
                ?: _favoriteSongs.value.firstOrNull()?.id
                ?: historyList.firstOrNull()?.id
                ?: "1901371647"
            val heartSongs = MultiPlatformAccountManager.fetchHeartModeSongs(seedId).getOrDefault(emptyList())
            if (heartSongs.isNotEmpty()) {
                _playMode.postValue(PlayMode.HEART_MODE)
                playSong(heartSongs.first(), heartSongs, isFm = false)
                onLoaded?.invoke(true)
            } else {
                onLoaded?.invoke(false)
            }
        }
    }

    /**
     * 收藏 / 取消收藏当前或指定歌曲（持久化到 Room 并同步至网易云红心）
     */
    fun toggleFavorite(song: Song? = _currentSong.value) {
        val target = song ?: return
        scope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstanceOrNull()?.songDao() ?: return@launch
            val currentlyFav = dao.isFavorite(target.id)
            if (currentlyFav) {
                dao.deleteFavoriteById(target.id)
                MultiPlatformAccountManager.syncNeteaseLike(target.id, false)
            } else {
                dao.insertFavorite(target.toFavoriteEntity())
                MultiPlatformAccountManager.syncNeteaseLike(target.id, true)
            }
            if (_currentSong.value?.id == target.id) {
                _isCurrentFavorite.value = !currentlyFav
            }
        }
    }

    /**
     * 手动切换指定音源重新解析当前歌曲
     */
    fun switchSource(sourceId: String) {
        val song = _currentSong.value ?: return
        val currentPos = if (::exoPlayer.isInitialized) exoPlayer.currentPosition else 0L
        scope.launch {
            val res = SoundSourceRepository.getStreamUrlFromSource(
                sourceId = sourceId,
                song = song,
                quality = _quality.value ?: AudioQuality.LOSSLESS
            )
            val streamResult = res.getOrNull()
            if (streamResult != null && streamResult.streamUrl.isNotBlank()) {
                _activeStreamResult.value = streamResult
                val mediaItem = MediaItem.fromUri(Uri.parse(streamResult.streamUrl))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                if (currentPos > 1000L) {
                    exoPlayer.seekTo(currentPos)
                }
                exoPlayer.play()
            }
        }
    }

    private fun fetchCoverIfNeeded(song: Song) {
        if ((song.coverUrl.isBlank() || song.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) && song.id.all { it.isDigit() }) {
            scope.launch {
                try {
                    val detailRes = NcmApi.songDetail(listOf(song.id))
                    val detailData = detailRes.getOrNull()
                    if (detailData != null) {
                        val detailSongs = detailData.ncmList("songs")
                        if (detailSongs.isNotEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            val realSong = (detailSongs[0] as? Map<String, Any?>)?.toSong()
                            if (realSong != null && realSong.coverUrl.isNotBlank() && !realSong.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) {
                                _currentSong.postValue(realSong)
                            }
                        }
                    }
                } catch (_: Exception) {
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
            _activeStreamResult.value = null
            val result = SoundSourceRepository.getStreamUrl(song, _quality.value ?: AudioQuality.LOSSLESS)
            val streamResult = result.getOrNull()
            _activeStreamResult.value = streamResult
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
        if (_isPersonalFmMode.value && currentIndex >= playlist.size - 2) {
            appendMorePersonalFm()
        }
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
            _currentPosition.value = positionMs
        }
    }

    fun seekRelative(deltaMs: Long) {
        if (::exoPlayer.isInitialized) {
            val current = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            val target = (current + deltaMs).coerceIn(0L, dur)
            exoPlayer.seekTo(target)
            _currentPosition.value = target
        }
    }

    fun togglePlayMode() {
        val next = (_playMode.value ?: PlayMode.SEQUENCE).next()
        _playMode.postValue(next)

        if (next == PlayMode.HEART_MODE) {
            startHeartMode()
            return
        }

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
        scope.launch(Dispatchers.IO) {
            runCatching { AppDatabase.getInstanceOrNull()?.songDao()?.deleteHistoryById(song.id) }
        }
    }

    fun clearHistory() {
        historyList.clear()
        _playHistory.postValue(emptyList())
        scope.launch(Dispatchers.IO) {
            runCatching { AppDatabase.getInstanceOrNull()?.songDao()?.clearAllHistory() }
        }
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
