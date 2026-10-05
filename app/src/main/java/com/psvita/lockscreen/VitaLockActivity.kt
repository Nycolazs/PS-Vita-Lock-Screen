package com.psvita.lockscreen

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.service.LockScreenService
import com.psvita.lockscreen.view.VitaPeelView

class VitaLockActivity : AppCompatActivity() {

    private lateinit var peelView: VitaPeelView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock to Landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Setup lock screen flags and full edge-to-edge layout past cutouts
        configureLockScreenFlags()
        enableImmersiveMode()

        peelView = VitaPeelView(this).apply {
            onUnlockListener = object : VitaPeelView.OnUnlockListener {
                override fun onUnlock() {
                    unlockDevice()
                }
            }
        }

        setContentView(peelView)

        val prefs = LockPreferences(this)
        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockScreenFlags()
        enableImmersiveMode()
        peelView.resetPeel()
    }

    override fun onResume() {
        super.onResume()
        configureLockScreenFlags()
        enableImmersiveMode()
        peelView.resetPeel()
        val prefs = LockPreferences(this)
        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        configureLockScreenFlags()
        enableImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }

    @Suppress("DEPRECATION")
    private fun unlockDevice() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
        moveTaskToBack(true)
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Prevent accidental dismissal of lock screen
    }
}
