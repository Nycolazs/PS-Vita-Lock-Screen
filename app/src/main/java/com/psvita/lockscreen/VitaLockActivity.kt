package com.psvita.lockscreen

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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

        // Lock to Landscape safely
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Setup lock screen flags, refresh rate and full edge-to-edge layout past cutouts
        configureLockScreenFlags()
        configureDisplayRefreshRate()
        enableImmersiveMode()

        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        peelView = VitaPeelView(this).apply {
            onUnlockListener = object : VitaPeelView.OnUnlockListener {
                override fun onUnlock() {
                    unlockDevice()
                }
            }
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(peelView) { _, insets ->
            val cutout = insets.displayCutout
            if (cutout != null) {
                peelView.cutoutLeft = cutout.safeInsetLeft.toFloat()
                peelView.cutoutRight = cutout.safeInsetRight.toFloat()
            }
            insets
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
        peelView.resetPeel(force = true)
    }

    override fun onResume() {
        super.onResume()
        configureLockScreenFlags()
        configureDisplayRefreshRate()
        enableImmersiveMode()
        peelView.resetPeel(force = true)
        val prefs = LockPreferences(this)
        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        configureLockScreenFlags()
        configureDisplayRefreshRate()
        enableImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    private fun configureDisplayRefreshRate() {
        // Explicitly select the highest available refresh rate (e.g. 120Hz on high-refresh panels)
        // On 60Hz panels, maxMode will cleanly be the 60Hz mode, perfectly safe and without breaking.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val d = display
            val modes = d?.supportedModes
            if (!modes.isNullOrEmpty()) {
                val maxMode = modes.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val lp = window.attributes
            lp.preferredRefreshRate = 120f
            window.attributes = lp
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
        window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
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
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Gamepad A / Start (or Enter on a keyboard) peels the card off, like a console start screen
        if (event.repeatCount == 0 && keyCode in UNLOCK_KEYS) {
            peelView.peelAndUnlock()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Prevent accidental dismissal of lock screen
    }

    companion object {
        private val UNLOCK_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER
        )
    }
}
