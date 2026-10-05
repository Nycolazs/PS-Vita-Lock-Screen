package com.psvita.lockscreen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.service.LockScreenService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = LockPreferences(context)
        if (prefs.isLockscreenEnabled &&
            (intent.action == Intent.ACTION_BOOT_COMPLETED ||
             intent.action == "android.intent.action.QUICKBOOT_POWERON")
        ) {
            val serviceIntent = Intent(context, LockScreenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
