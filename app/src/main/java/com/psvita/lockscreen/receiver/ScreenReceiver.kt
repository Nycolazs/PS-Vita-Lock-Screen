package com.psvita.lockscreen.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.psvita.lockscreen.VitaLockActivity
import com.psvita.lockscreen.data.LockPreferences

class ScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = LockPreferences(context)
        if (!prefs.isLockscreenEnabled) return

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Launch immediately when screen turns off so VitaLockActivity is already
                // in the foreground and ready when the screen turns back on!
                launchLockActivity(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                // Ensure activity is launched if it wasn't already running
                launchLockActivity(context)
            }
        }
    }

    private fun launchLockActivity(context: Context) {
        val lockIntent = Intent(context, VitaLockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        try {
            context.startActivity(lockIntent)
        } catch (_: Exception) {}
    }
}
