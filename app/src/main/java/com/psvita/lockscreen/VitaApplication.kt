package com.psvita.lockscreen

import android.app.Application
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.service.LockScreenService

class VitaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = LockPreferences(this)
        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }
    }
}
