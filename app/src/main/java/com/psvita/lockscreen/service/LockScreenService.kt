package com.psvita.lockscreen.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.psvita.lockscreen.R
import com.psvita.lockscreen.SettingsActivity
import com.psvita.lockscreen.VitaLockActivity
import com.psvita.lockscreen.receiver.ScreenReceiver

class LockScreenService : Service() {

    private var screenReceiver: ScreenReceiver? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        registerScreenReceiver()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "PS Vita Lock Screen Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps PS Vita Lock Screen active"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startAsForeground() {
        val settingsPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, VitaLockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            1,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PS Vita Lock Screen")
            .setContentText("Toque para abrir as configurações")
            .setContentIntent(settingsPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID_SERVICE, notification)
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = ScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_LAUNCH_LOCK, false) == true) {
            launchLockActivity(this)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_SERVICE_ID = "vita_lock_service_channel"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val EXTRA_LAUNCH_LOCK = "extra_launch_lock"

        fun start(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            context.stopService(intent)
        }

        fun launchLockScreen(context: Context) {
            val intent = Intent(context, LockScreenService::class.java).apply {
                putExtra(EXTRA_LAUNCH_LOCK, true)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
            launchLockActivity(context)
        }

        fun launchLockActivity(context: Context) {
            val lockIntent = Intent(context, VitaLockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            } else {
                null
            }
            try {
                context.startActivity(lockIntent, options)
            } catch (_: Exception) {}
        }
    }
}
