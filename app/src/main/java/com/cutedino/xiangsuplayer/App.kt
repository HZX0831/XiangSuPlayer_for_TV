package com.cutedino.xiangsuplayer

import android.app.Application
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.core.source.SoundSourceRepository
import com.cutedino.xiangsuplayer.core.source.market.SourceMarketRepository
import com.cutedino.xiangsuplayer.data.db.AppDatabase
import com.cutedino.xiangsuplayer.data.session.SessionManager
import net.moriafly.ncm.NcmApi

class App : Application() {
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
