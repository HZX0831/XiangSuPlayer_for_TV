package com.cutedino.xiangsuplayer.core.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.cutedino.xiangsuplayer.core.library.FolderLibrary
import com.cutedino.xiangsuplayer.core.library.WebDavClient
import com.cutedino.xiangsuplayer.data.db.SongDao
import com.cutedino.xiangsuplayer.core.model.AudioQuality
import com.cutedino.xiangsuplayer.core.model.LyricLine
import com.cutedino.xiangsuplayer.core.model.Song
import com.cutedino.xiangsuplayer.core.model.toSong
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.StreamResult
import com.cutedino.xiangsuplayer.core.source.ResolvedStream
import com.cutedino.xiangsuplayer.core.source.SourceNetwork
import com.cutedino.xiangsuplayer.data.auth.MultiPlatformAccountManager
import com.cutedino.xiangsuplayer.data.auth.FavoriteSyncWorker
import com.cutedino.xiangsuplayer.data.db.AppDatabase
import com.cutedino.xiangsuplayer.data.db.toFavoriteEntity
import com.cutedino.xiangsuplayer.data.db.toHistoryEntity
import com.cutedino.xiangsuplayer.data.session.SessionManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

@OptIn(UnstableApi::class)
internal class PlaybackEngine(
    private val appContext: Context,
    private val exoPlayer: ExoPlayer,
    private val state: PlaybackState
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val snapshots = PlaybackSnapshotStore(appContext)
    private val legacyAudioFactory by lazy {
        val http = SourceNetwork.configure(okhttp3.OkHttpClient.Builder()).build()
        DefaultMediaSourceFactory(OkHttpDataSource.Factory(http))
    }
    private var snapshotJob: Job? = null
    private var historyJob: Job? = null
    private val favoritesLock = Mutex()
    private val favoriteSync = AppDatabase.getInstanceOrNull()?.songDao()?.let { FavoriteSyncWorker(it, scope) }
    private var resumePosition: Long? = null
    private var resumeOnReady: Long? = null
    private var fmJob: Job? = null
    private var recommendationJob: Job? = null
    private var restoreJob: Job? = null
    private var enterKaraokeJob: Job? = null
    private var requestedQueueRevision = 0L
    private var waitingForFm = false
    private var karaoke = false
    private var ordinaryQueue: OrdinaryQueue? = null
    private var nextEntryId: String? = null

    private data class OrdinaryQueue(
        val playback: PlaybackSnapshot,
        val originalOrder: List<PlaybackQueueEntry>,
        val nextEntryId: String?,
        val isPersonalFm: Boolean
    )


    private val _currentSong = state.currentSong

    private val _isPlaying = state.isPlaying

    private val _playMode = state.playMode

    private val _currentPosition = state.currentPosition

    private val _duration = state.duration

    private val _lyrics = state.lyrics

    private val _activeLyricIndex = state.activeLyricIndex

    private val _quality = state.quality

    private val _activeStreamResult = state.activeStreamResult

    private val _isCurrentFavorite = state.isCurrentFavorite

    private val _isPersonalFmMode = state.isPersonalFmMode

    private val _favoriteSongs = state.favoriteSongs

    private val playlist = mutableListOf<PlaybackQueueEntry>()
    private val originalPlaylist = mutableListOf<PlaybackQueueEntry>()
    private val historyList = mutableListOf<Song>()

    private val _playHistory = state.playHistory

    private val _playlistLiveData = state.playlistLiveData

    private val _currentQueueIndex = state.currentQueueIndex
    private var currentIndex: Int
        get() = _currentQueueIndex.value
        set(value) { _currentQueueIndex.value = value }
    private var progressJob: Job? = null
    private var resolveJob: Job? = null
    private var lyricsJob: Job? = null
    private var coverJob: Job? = null
    private var songRevision = 0L
    private var streamRevision = 0L
    private var historyRevision = -1L
    private var pendingManualResolution: Long? = null
    private var shouldPlay = false
    private var activeResolution: ResolvedStream? = null
    private var resolutionReady = false
    private var rejectedMiguUrl: String? = null
    private val sourceAttempts = PlaybackSourceAttempts()
    private var previousStream: PreviousStream? = null

    private data class PreviousStream(
        val item: MediaItem,
        val position: Long,
        val quality: AudioQuality,
        val resolution: ResolvedStream?
    )

    private val _playbackError = state.playbackError

    private fun Song.sameTrack(other: Song) = key == other.key

    init {
        _quality.value = SessionManager.getAudioQuality(appContext)
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                publish()
                if (isPlaying) {
                    val song = _currentSong.value
                    if (song != null && historyRevision != songRevision) {
                        historyRevision = songRevision
                        recordHistory(song)
                    }
                    startProgressTracker()
                } else stopProgressTracker()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    shouldPlay = false
                    saveSnapshot()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    previousStream = null
                    resolutionReady = true
                    _quality.value?.let { SessionManager.saveAudioQuality(appContext, it) }
                    _currentPosition.value = exoPlayer.currentPosition
                    updateActiveLyric(exoPlayer.currentPosition)
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    resumeOnReady?.let { saved ->
                        resumeOnReady = null
                        if (exoPlayer.isCurrentMediaItemSeekable && activeResolution?.seekable != false && _duration.value > 0L) {
                            seekTo(saved.coerceAtMost(_duration.value - 1))
                        }
                        exoPlayer.playWhenReady = shouldPlay
                    }
                    publish()
                } else if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (pendingManualResolution == null) recoverPlaybackError()
            }
        })

        loadPersistedRoomData()
        restoreJob = scope.launch {
            val saved = withContext(Dispatchers.IO) { snapshots.read() }
            if (saved != null && songRevision == 0L) restore(saved)
        }
    }

    private fun recoverPlaybackError() {
        val refreshMigu = invalidateMiguUrl()
        val previous = previousStream
        previousStream = null
        resumeOnReady?.let { resumePosition = it }
        resumeOnReady = null
        _playbackError.value = if (previous == null) "播放失败，请重试" else "切换失败，已恢复原音源"
        if (previous != null) {
            _quality.value = previous.quality
            activeResolution = previous.resolution
            _activeStreamResult.value = previous.resolution?.result
            prepareMedia(previous.item, previous.position, previous.resolution)
            exoPlayer.playWhenReady = shouldPlay
            exoPlayer.prepare()
        } else if (retryAfterPlaybackError(refreshMigu)) return
        publish()
    }

    private fun publish() {
        state.ui.value = PlaybackUiState(
            queue = playlist.toList(),
            currentEntryId = playlist.getOrNull(currentIndex)?.id,
            song = _currentSong.value,
            playing = _isPlaying.value == true,
            positionMs = _currentPosition.value,
            durationMs = _duration.value,
            error = _playbackError.value,
            seekable = _duration.value > 0 && activeResolution?.seekable != false && exoPlayer.isCurrentMediaItemSeekable
        )
    }

    private fun publishQueue() {
        _playlistLiveData.value = playlist.map { it.song }
        publish()
    }

    fun close() {
        scope.cancel()
    }

    private fun loadPersistedRoomData() {
        val dao = AppDatabase.getInstanceOrNull()?.songDao() ?: return
        scope.launch {
            dao.getPlayHistoryFlow().collect { entities ->
                historyList.clear()
                historyList.addAll(entities.map { it.toSong() })
                _playHistory.value = historyList.toList()
            }
        }
        scope.launch {
            dao.getAllFavorites().collect { entities ->
                val favs = entities.map { it.toSong() }
                _favoriteSongs.value = favs
                val currentKey = _currentSong.value?.key
                _isCurrentFavorite.value = favs.any { it.key == currentKey }
            }
        }
    }

    private fun onSongEnded() {
        if (_playMode.value == PlayMode.REPEAT_ONE && !karaoke && !_isPersonalFmMode.value) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
        } else playNext()
    }

    fun playSong(song: Song, list: List<Song> = emptyList(), isFm: Boolean = false) {
        endKaraoke()
        cancelRecommendation()
        nextEntryId = null
        fmJob?.cancel()
        waitingForFm = false
        _isPersonalFmMode.value = isFm
        if (list.isNotEmpty()) {
            originalPlaylist.clear()
            originalPlaylist.addAll(list.map { PlaybackQueueEntry(it) })
            val selected = originalPlaylist.firstOrNull { it.song.sameTrack(song) }
                ?: PlaybackQueueEntry(song).also { originalPlaylist.add(it) }
            playlist.clear()
            if (_playMode.value == PlayMode.SHUFFLE && !isFm) {
                playlist.add(selected)
                playlist.addAll(originalPlaylist.filter { it.id != selected.id }.shuffled())
            } else playlist.addAll(originalPlaylist)
            currentIndex = playlist.indexOf(selected)
        } else {
            currentIndex = playlist.indexOfFirst { it.song.sameTrack(song) }
            if (currentIndex < 0) {
                val entry = PlaybackQueueEntry(song)
                playlist.add(entry)
                originalPlaylist.add(entry)
                currentIndex = playlist.lastIndex
            }
        }
        playSongAtIndex(currentIndex)
    }

    fun playSongAtIndex(index: Int) {
        val entry = playlist.getOrNull(index) ?: return
        cancelRecommendation()
        if (playlist.getOrNull(currentIndex)?.id != entry.id) nextEntryId = null
        currentIndex = index
        activateSong(entry.song)
        publishQueue()
        saveSnapshot()
    }

    fun playQueueEntry(id: String) = playSongAtIndex(playlist.indexOfFirst { it.id == id })
    fun removeQueueEntry(id: String) = removeFromPlaylistAt(playlist.indexOfFirst { it.id == id })

    fun moveQueueEntryNext(id: String) {
        val index = playlist.indexOfFirst { it.id == id }
        if (index < 0 || index == currentIndex) return
        val entry = playlist.removeAt(index)
        if (index < currentIndex) currentIndex--
        playlist.add((currentIndex + 1).coerceIn(0, playlist.size), entry)
        rememberNext(entry)
        publishQueue()
        saveSnapshot()
    }

    private fun activateSong(song: Song) {
        val revision = ++songRevision
        previousStream = null
        pendingManualResolution = null
        resumePosition = null
        resumeOnReady = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        shouldPlay = true
        _currentSong.value = song
        _currentPosition.value = 0L
        _duration.value = 0L
        activeResolution = null
        resolutionReady = false
        sourceAttempts.reset()
        rejectedMiguUrl = null
        _activeStreamResult.value = null
        _lyrics.value = emptyList()
        _activeLyricIndex.value = -1
        _isCurrentFavorite.value = _favoriteSongs.value.any { it.sameTrack(song) }
        resolveStream(song)
        fetchLyrics(song, revision)
        fetchCoverIfNeeded(song, revision)
        publish()
    }

    private fun recordHistory(song: Song) {
        val record = song.toHistoryEntity()
        writeHistory { it.insertHistory(record) }
    }

    private fun writeHistory(write: suspend (SongDao) -> Unit) {
        val dao = AppDatabase.getInstanceOrNull()?.songDao() ?: return
        val previous = historyJob
        historyJob = scope.launch {
            previous?.join()
            try {
                write(dao)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("PlaybackHistory", "Could not write history", failure)
            }
        }
    }

    /**
     * 启动私人 FM 漫游模式
     */
    private fun cancelRecommendation() {
        requestedQueueRevision++
        recommendationJob?.cancel()
        recommendationJob = null
    }

    fun startPersonalFm(onLoaded: ((Boolean) -> Unit)? = null) {
        cancelRecommendation()
        val revision = requestedQueueRevision
        recommendationJob = scope.launch {
            val res = MultiPlatformAccountManager.fetchPersonalFmSongs()
            ensureActive()
            if (revision != requestedQueueRevision) return@launch
            recommendationJob = null
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
        if (fmJob?.isActive == true) return
        fmJob = scope.launch {
            val more = MultiPlatformAccountManager.fetchPersonalFmSongs().getOrDefault(emptyList())
            ensureActive()
            if (!_isPersonalFmMode.value) return@launch
            val known = playlist.mapTo(mutableSetOf()) { it.song.key }
            val filtered = more.filter { known.add(it.key) }.map { PlaybackQueueEntry(it) }
            playlist.addAll(filtered)
            originalPlaylist.addAll(filtered)
            if (waitingForFm) {
                waitingForFm = false
                if (currentIndex < playlist.lastIndex) playSongAtIndex(currentIndex + 1)
                else {
                    _playbackError.value = "私人 FM 暂无后续曲目，按下一曲重试"
                    pause()
                }
            }
            publishQueue()
        }
    }

    /**
     * 启动心动智能推荐模式 (Heart Mode)
     */
    fun startHeartMode(onLoaded: ((Boolean) -> Unit)? = null) {
        cancelRecommendation()
        val revision = requestedQueueRevision
        recommendationJob = scope.launch {
            val seedId = _currentSong.value?.takeIf { it.key.sourceId == "wy" }?.id
                ?: _favoriteSongs.value.firstOrNull { it.key.sourceId == "wy" }?.id
                ?: historyList.firstOrNull { it.key.sourceId == "wy" }?.id
                ?: "1901371647"
            val heartSongs = MultiPlatformAccountManager.fetchHeartModeSongs(seedId).getOrDefault(emptyList())
            ensureActive()
            if (revision != requestedQueueRevision) return@launch
            recommendationJob = null
            if (heartSongs.isNotEmpty()) {
                _playMode.value = PlayMode.HEART_MODE
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
        val accountId = NcmApi.currentAccount()?.userId
        scope.launch {
            favoritesLock.withLock {
                val dao = AppDatabase.getInstanceOrNull()?.songDao() ?: return@withLock
                try {
                    dao.toggleFavorite(target.toFavoriteEntity(), accountId)
                    favoriteSync?.notifyChanged()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("PlaybackFavorites", "Could not update favorite", failure)
                }
            }
        }
    }

    /**
     * 手动切换指定音源重新解析当前歌曲
     */
    fun switchSource(sourceId: String) {
        _currentSong.value?.let { resolveStream(it, sourceId = sourceId, preservePosition = true) }
    }

    private fun fetchCoverIfNeeded(song: Song, revision: Long) {
        coverJob?.cancel()
        if (song.source == "wy" && ((song.coverUrl.isBlank() || song.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) && song.id.all { it.isDigit() })) {
            coverJob = scope.launch {
                try {
                    val detailRes = NcmApi.songDetail(listOf(song.id))
                    val detailData = detailRes.getOrNull()
                    if (detailData != null) {
                        val detailSongs = detailData.ncmList("songs")
                        if (detailSongs.isNotEmpty()) {
                            @Suppress("UNCHECKED_CAST")
                            val realSong = (detailSongs[0] as? Map<String, Any?>)?.toSong()
                            if (realSong != null && realSong.coverUrl.isNotBlank() && !realSong.coverUrl.contains("6ffA2_Xx4J-yXZDKm4p8fw==")) {
                                if (revision == songRevision) {
                                    _currentSong.value = song.copy(coverUrl = realSong.coverUrl)
                                }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep the song's existing artwork.
                }
            }
        }
    }

    fun insertAsNext(song: Song) {
        if (playlist.isEmpty()) {
            playSong(song)
            return
        }
        val existing = playlist.firstOrNull { it.song.sameTrack(song) }
        if (existing != null) {
            moveQueueEntryNext(existing.id)
            return
        }
        val entry = PlaybackQueueEntry(song)
        playlist.add((currentIndex + 1).coerceIn(0, playlist.size), entry)
        rememberNext(entry)
        publishQueue()
        saveSnapshot()
    }

    private fun rememberNext(entry: PlaybackQueueEntry) {
        val current = playlist.getOrNull(currentIndex)
        originalPlaylist.remove(entry)
        val anchor = originalPlaylist.indexOfFirst { it.id == current?.id }
        originalPlaylist.add(anchor + 1, entry)
        nextEntryId = entry.id
    }

    private fun resolveStream(
        song: Song,
        quality: AudioQuality = _quality.value ?: AudioQuality.LOSSLESS,
        sourceId: String? = null,
        preservePosition: Boolean = false,
        preferredSourceId: String? = null,
        continueAttempt: Boolean = false
    ) {
        if (!continueAttempt) sourceAttempts.startBudget()
        val revision = ++streamRevision
        resolveJob?.cancel()
        pendingManualResolution = revision.takeIf { preservePosition }
        _playbackError.value = null
        resolveJob = scope.launch {
            val result = try {
                when {
                    FolderLibrary.isFolderSong(song) ->
                        Result.success(FolderLibrary.resolve(appContext, song, quality))
                    sourceId == null -> SoundSourceRepository.getStreamUrl(
                        song, quality, preferredSourceId, sourceAttempts.exclusions(quality), sourceAttempts.remaining(), rejectedMiguUrl
                    )
                    else -> SoundSourceRepository.getStreamUrlFromSource(sourceId, song, quality, rejectedMiguUrl)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Result.failure(failure)
            }
            ensureActive()
            if (revision != streamRevision) return@launch
            pendingManualResolution = null
            val stream = result.getOrNull()?.takeIf { it.streamUrl.isNotBlank() }
            if (stream == null) {
                if (preservePosition && exoPlayer.playerError != null) {
                    recoverPlaybackError()
                    return@launch
                }
                _playbackError.value = when {
                    preservePosition -> "切换失败，请重试"
                    FolderLibrary.isFolderSong(song) -> "目录曲目不可访问，请检查授权、U 盘或网络"
                    else -> "没有可播放的音源，请重试"
                }
                publish()
                return@launch
            }

            resumeOnReady = (resumePosition ?: resumeOnReady)?.takeIf { stream.seekable != false }
            resumePosition = null
            val position = if (preservePosition && resumeOnReady == null) exoPlayer.currentPosition else 0L
            previousStream = if (preservePosition) {
                previousStream ?: exoPlayer.currentMediaItem?.takeIf { exoPlayer.playerError == null }?.let {
                    PreviousStream(it, position, _quality.value ?: AudioQuality.LOSSLESS, activeResolution)
                }
            } else null
            val mediaItem = MediaItem.Builder()
                .setMediaId(playlist.getOrNull(currentIndex)?.id.orEmpty())
                .setUri(Uri.parse(stream.streamUrl))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                        .build()
                )
                .build()
            activeResolution = stream
            resolutionReady = false
            _activeStreamResult.value = stream.result
            _quality.value = quality
            prepareMedia(mediaItem, position, stream)
            exoPlayer.playWhenReady = shouldPlay && resumeOnReady == null
            exoPlayer.prepare()
        }
    }

    private fun invalidateMiguUrl(): Boolean {
        val stream = activeResolution ?: return false
        if (_currentSong.value?.key?.sourceId != "mg" || rejectedMiguUrl != null ||
            stream.sourceId != SoundSourceRepository.crossPlatformAdapter.sourceId) return false
        rejectedMiguUrl = stream.streamUrl
        return true
    }

    private fun retryAfterPlaybackError(refreshMigu: Boolean): Boolean {
        val song = _currentSong.value ?: return false
        val failed = activeResolution ?: return false
        if (FolderLibrary.isFolderSong(song)) return false
        val quality = failed.requestedQuality
        if (!refreshMigu) sourceAttempts.reject(quality, failed.sourceId)
        sourceAttempts.onMediaFailure(wasReady = resolutionReady)
        activeResolution = null
        resolutionReady = false
        if (!shouldPlay || sourceAttempts.remaining() <= 0L) return false
        if (!SoundSourceRepository.hasUntriedSource(song, sourceAttempts.exclusions(quality))) return false
        resumePosition = resumePosition ?: exoPlayer.currentPosition.takeIf { it > 0L }
        resolveStream(song, quality, continueAttempt = true)
        return true
    }

    private fun prepareMedia(item: MediaItem, position: Long, resolution: ResolvedStream?) {
        if (resolution?.sourceId?.startsWith("webdav:") == true) {
            val factory = OkHttpDataSource.Factory(WebDavClient.playbackClient(resolution.seekable)).setDefaultRequestProperties(resolution.requestHeaders)
            val source = ProgressiveMediaSource.Factory(factory).createMediaSource(item)
            exoPlayer.setMediaSource(source, if (resolution.seekable == false) 0L else position)
        } else if (item.localConfiguration?.uri?.scheme == "http") {
            exoPlayer.setMediaSource(legacyAudioFactory.createMediaSource(item), position)
        } else exoPlayer.setMediaItem(item, position)
    }

    private fun fetchLyrics(song: Song, revision: Long) {
        lyricsJob?.cancel()
        if (FolderLibrary.isFolderSong(song)) return
        lyricsJob = scope.launch {
            val result = SoundSourceRepository.getLyrics(song)
            if (revision == songRevision) {
                _lyrics.value = result.getOrDefault(emptyList())
                _activeLyricIndex.value = -1
                updateActiveLyric(_currentPosition.value)
            }
        }
    }

    fun play() {
        val song = _currentSong.value ?: return
        shouldPlay = true
        if (exoPlayer.currentMediaItem == null || shouldRetryPlayback()) {
            if (resolveJob?.isActive != true) {
                val quality = _quality.value ?: AudioQuality.LOSSLESS
                if (!SoundSourceRepository.hasUntriedSource(song, sourceAttempts.exclusions(quality))) sourceAttempts.clear(quality)
                val failed = exoPlayer.playerError != null
                if (failed) resumePosition = resumePosition ?: exoPlayer.currentPosition.takeIf { it > 0L }
                resolveStream(song, quality, preservePosition = !failed && exoPlayer.currentMediaItem != null)
            }
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun stop() {
        cancelRecommendation()
        shouldPlay = false
        waitingForFm = false
        streamRevision++
        resolveJob?.cancel()
        pendingManualResolution = null
        previousStream = null
        exoPlayer.pause()
        exoPlayer.stop()
        saveSnapshot()
    }

    fun pause() {
        cancelRecommendation()
        shouldPlay = false
        waitingForFm = false
        exoPlayer.pause()
        saveSnapshot()
    }

    private fun shouldRetryPlayback() = _playbackError.value != null &&
        (exoPlayer.currentMediaItem == null || exoPlayer.playerError != null)

    fun togglePlayPause() {
        if (shouldPlay && !shouldRetryPlayback()) pause() else play()
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        cancelRecommendation()
        if (karaoke) {
            removeFromPlaylistAt(currentIndex)
            return
        }
        if (_isPersonalFmMode.value) {
            if (currentIndex >= playlist.lastIndex) {
                waitingForFm = true
                appendMorePersonalFm()
                return
            }
            if (currentIndex >= playlist.lastIndex - 1) appendMorePersonalFm()
        }
        playSongAtIndex((currentIndex + 1) % playlist.size)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        cancelRecommendation()
        if (karaoke) {
            seekTo(0L)
            return
        }
        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playSongAtIndex(currentIndex)
    }

    fun seekTo(positionMs: Long) {
        if (activeResolution?.seekable == false) return
        run {
            val target = positionMs.coerceIn(0L, _duration.value)
            exoPlayer.seekTo(target)
            _currentPosition.value = target
            updateActiveLyric(target)
            publish()
            saveSnapshot()
        }
    }

    fun seekRelative(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    fun togglePlayMode() {
        if (karaoke) return
        cancelRecommendation()
        val next = (_playMode.value ?: PlayMode.SEQUENCE).next()
        _playMode.value = next
        if (next == PlayMode.HEART_MODE) {
            startHeartMode()
            return
        }
        val current = playlist.getOrNull(currentIndex)
        if (next == PlayMode.SHUFFLE && current != null) {
            val shuffled = playlist.filter { it.id != current.id }.shuffled()
            playlist.clear()
            playlist.add(current)
            playlist.addAll(shuffled)
        } else if (originalPlaylist.isNotEmpty()) {
            playlist.clear()
            playlist.addAll(originalPlaylist)
        }
        val queuedNext = playlist.firstOrNull { it.id == nextEntryId }
        if (current != null && queuedNext != null) {
            playlist.remove(queuedNext)
            playlist.add(playlist.indexOf(current) + 1, queuedNext)
        }
        currentIndex = playlist.indexOfFirst { it.id == current?.id }
        publishQueue()
        saveSnapshot()
    }

    fun setQuality(quality: AudioQuality) {
        val song = _currentSong.value
        if (song == null) {
            _quality.value = quality
            SessionManager.saveAudioQuality(appContext, quality)
        } else {
            resolveStream(song, quality = quality, preservePosition = true, preferredSourceId = activeResolution?.sourceId)
        }
    }

    fun getHistory(): List<Song> = historyList.toList()

    fun removeFromHistory(song: Song) {
        writeHistory { it.deleteHistoryById(song.key.sourceId, song.id) }
    }

    fun clearHistory() {
        writeHistory { it.clearAllHistory() }
    }

    fun removeFromPlaylist(song: Song) {
        removeFromPlaylistAt(playlist.indexOfFirst { it.song.sameTrack(song) })
    }

    fun removeFromPlaylistAt(removeIndex: Int) {
        if (removeIndex in playlist.indices) {
            val removed = playlist.removeAt(removeIndex)
            if (nextEntryId == removed.id) nextEntryId = null
            val originalIndex = originalPlaylist.indexOfFirst { it.id == removed.id }
            if (originalIndex >= 0) originalPlaylist.removeAt(originalIndex)
            if (removeIndex < currentIndex) {
                currentIndex--
            } else if (removeIndex == currentIndex) {
                if (playlist.isEmpty()) {
                    clearCurrentSong()
                } else {
                    playSongAtIndex(removeIndex.coerceAtMost(playlist.lastIndex))
                }
            }
            publishQueue()
            saveSnapshot()
        }
    }

    private fun clearCurrentSong() {
        cancelRecommendation()
        fmJob?.cancel()
        nextEntryId = null
        pendingManualResolution = null
        currentIndex = -1
        songRevision++
        streamRevision++
        resolveJob?.cancel()
        coverJob?.cancel()
        lyricsJob?.cancel()
        previousStream = null
        resumePosition = null
        resumeOnReady = null
        shouldPlay = false
        waitingForFm = false
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentSong.value = null
        _lyrics.value = emptyList()
        _activeLyricIndex.value = -1
        _isCurrentFavorite.value = false
        _isPersonalFmMode.value = false
        activeResolution = null
        _activeStreamResult.value = null
        _playbackError.value = null
        _duration.value = 0L
        _currentPosition.value = 0L
        publish()
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var savedAt = android.os.SystemClock.elapsedRealtime()
            while (true) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    _currentPosition.value = pos
                    updateActiveLyric(pos)
                }
                publish()
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - savedAt >= 10_000L) {
                    savedAt = now
                    saveSnapshot()
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
            _activeLyricIndex.value = active
        }
    }

    private fun snapshot(): PlaybackSnapshot? {
        val current = playlist.getOrNull(currentIndex) ?: return null
        val position = resumePosition ?: resumeOnReady ?:
            if (exoPlayer.currentMediaItem == null) _currentPosition.value else exoPlayer.currentPosition
        return PlaybackSnapshot(playlist.toList(), current.id, position, _playMode.value ?: PlayMode.SEQUENCE)
    }

    private fun saveSnapshot() {
        if (karaoke || (restoreJob?.isActive == true && songRevision == 0L)) return
        val value = snapshot()
        val previous = snapshotJob
        snapshotJob = scope.launch {
            previous?.join()
            withContext(Dispatchers.IO) { snapshots.write(value) }
        }
    }

    private fun restore(value: PlaybackSnapshot) {
        val restoredIndex = value.queue.indexOfFirst { it.id == value.currentId }
        if (restoredIndex < 0) return
        sourceAttempts.reset()
        rejectedMiguUrl = null
        playlist.clear()
        originalPlaylist.clear()
        playlist.addAll(value.queue)
        originalPlaylist.addAll(value.queue)
        currentIndex = restoredIndex
        _playMode.value = value.mode
        _currentSong.value = playlist[restoredIndex].song
        _isCurrentFavorite.value = _favoriteSongs.value.any { it.key == playlist[restoredIndex].song.key }
        _currentPosition.value = value.positionMs.coerceAtLeast(0L)
        _duration.value = playlist[restoredIndex].song.durationMs.coerceAtLeast(0L)
        resumePosition = _currentPosition.value
        publishQueue()
        fetchLyrics(playlist[restoredIndex].song, songRevision)
        fetchCoverIfNeeded(playlist[restoredIndex].song, songRevision)
    }

    fun beginKaraoke() {
        if (karaoke || enterKaraokeJob?.isActive == true) return
        enterKaraokeJob = scope.launch {
            restoreJob?.join()
            enterKaraoke()
        }
    }

    private fun enterKaraoke() {
        ordinaryQueue = snapshot()?.let {
            OrdinaryQueue(it, originalPlaylist.toList(), nextEntryId, _isPersonalFmMode.value)
        }
        saveSnapshot()
        karaoke = true
        playlist.clear()
        originalPlaylist.clear()
        clearCurrentSong()
        _playMode.value = PlayMode.SEQUENCE
        publishQueue()
    }

    fun enqueueKaraoke(song: Song): Boolean {
        if (!karaoke || playlist.size >= 100) return false
        val entry = PlaybackQueueEntry(song)
        playlist.add(entry)
        originalPlaylist.add(entry)
        if (currentIndex < 0) playSongAtIndex(0) else publishQueue()
        return true
    }

    fun endKaraoke() {
        enterKaraokeJob?.cancel()
        enterKaraokeJob = null
        if (!karaoke) return
        playlist.clear()
        originalPlaylist.clear()
        clearCurrentSong()
        karaoke = false
        ordinaryQueue?.let {
            restore(it.playback)
            originalPlaylist.clear()
            originalPlaylist.addAll(it.originalOrder)
            nextEntryId = it.nextEntryId
            _isPersonalFmMode.value = it.isPersonalFm
        }
        ordinaryQueue = null
        publishQueue()
    }

}
