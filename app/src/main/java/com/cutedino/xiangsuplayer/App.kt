package com.cutedino.xiangsuplayer

import android.app.Application
import com.cutedino.xiangsuplayer.core.audio.PlayerController
import com.cutedino.xiangsuplayer.data.session.SessionManager
import net.moriafly.ncm.NcmApi

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.init(this)
        NcmApi.install(this)
        PlayerController.init(this)
    }
}
