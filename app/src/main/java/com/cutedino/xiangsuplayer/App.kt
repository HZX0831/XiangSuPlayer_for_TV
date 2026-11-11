package com.cutedino.xiangsuplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import com.cutedino.xiangsuplayer.core.source.SourceNetwork
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import com.cutedino.xiangsuplayer.data.db.AppDatabase
import com.cutedino.xiangsuplayer.data.session.SessionManager
import net.moriafly.ncm.NcmApi

class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { SourceNetwork.configure(OkHttpClient.Builder()).build() }
        .build()

    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        AppDatabase.init(this)
        NcmApi.install(this)
        SourceMarketRepository.init(this)
        SoundSourceRepository.enabledSourceIds = SessionManager.enabledSoundSources
        SoundSourceRepository.disabledLxSourceIds = SessionManager.disabledLxSources
        PlayerController.init(this)
    }
}
