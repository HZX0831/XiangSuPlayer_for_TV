package com.cutedino.xiangsuplayer.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cutedino.xiangsuplayer.core.source.SourceNetwork
import com.cutedino.xiangsuplayer.ui.player.PlayerActivity
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var engine: PlaybackEngine
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioClient = SourceNetwork.configure(OkHttpClient.Builder(), allowLegacy = false).build()
        val dataSource = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(audioClient))
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        engine = PlayerController.createEngine(this, player)
        val controls = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
                .buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)
            override fun play() = engine.play()
            override fun pause() = engine.pause()
            override fun stop() = engine.stop()
            override fun seekToNext() = engine.playNext()
            override fun seekToNextMediaItem() = engine.playNext()
            override fun seekToPrevious() = engine.playPrevious()
            override fun seekToPreviousMediaItem() = engine.playPrevious()
            override fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
        }
        val openPlayer = PendingIntent.getActivity(
            this, 0, Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(this, controls).setSessionActivity(openPlayer).build()
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == LOCAL_ACTION) LocalBinder(engine) else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        PlayerController.detach(engine)
        engine.close()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    internal class LocalBinder(val engine: PlaybackEngine) : Binder()

    internal companion object {
        const val LOCAL_ACTION = "com.cutedino.xiangsuplayer.BIND_PLAYBACK"
    }
}
