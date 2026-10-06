package com.psvita.lockscreen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.service.LockScreenService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != "com.htc.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_REBOOT &&
            action != "com.psvita.lockscreen.ACTION_TRIGGER_BOOT"
        ) {
            return
        }

        val prefs = LockPreferences(context)
        if (!prefs.isLockscreenEnabled) return

        LockScreenService.start(context)
        LockScreenService.launchLockScreen(context)
    }
}
