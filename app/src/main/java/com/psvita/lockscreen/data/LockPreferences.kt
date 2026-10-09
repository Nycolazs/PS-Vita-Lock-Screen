package com.psvita.lockscreen.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

enum class ClockPosition(val id: Int) {
    BOTTOM_LEFT(0),
    TOP_LEFT(1),
    BOTTOM_RIGHT(2);

    companion object {
        fun fromId(id: Int): ClockPosition = values().firstOrNull { it.id == id } ?: BOTTOM_LEFT
    }
}

class LockPreferences(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    private val prefs: SharedPreferences = run {
        val targetContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                val dps = context.createDeviceProtectedStorageContext()
                val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                if (userManager?.isUserUnlocked == true) {
                    try {
                        dps.moveSharedPreferencesFrom(context, "psvita_lock_prefs")
                    } catch (_: Exception) {}
                }
                dps
            } catch (_: Exception) {
                context
            }
        } else {
            context
        }
        targetContext.getSharedPreferences("psvita_lock_prefs", Context.MODE_PRIVATE)
    }

    var isLockscreenEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCKSCREEN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LOCKSCREEN_ENABLED, value).apply()

    var is24HourFormat: Boolean
        // Follows the device's 12/24h setting until the user picks one in the app
        get() = prefs.getBoolean(KEY_24H_FORMAT, android.text.format.DateFormat.is24HourFormat(appContext))
        set(value) = prefs.edit().putBoolean(KEY_24H_FORMAT, value).apply()

    var clockPosition: ClockPosition
        get() = ClockPosition.fromId(prefs.getInt(KEY_CLOCK_POS, ClockPosition.BOTTOM_LEFT.id))
        set(value) = prefs.edit().putInt(KEY_CLOCK_POS, value.id).apply()

    var clockColor: Int
        get() = prefs.getInt(KEY_CLOCK_COLOR, Color.WHITE)
        set(value) = prefs.edit().putInt(KEY_CLOCK_COLOR, value).apply()

    var carrierText: String
        get() = prefs.getString(KEY_CARRIER_TEXT, "PlayStation") ?: "PlayStation"
        set(value) = prefs.edit().putString(KEY_CARRIER_TEXT, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    // Card Sheet Size percentage (70% - 100%, default 88%)
    var cardSizePercent: Int
        get() = prefs.getInt(KEY_CARD_SIZE_PERCENT, 88)
        set(value) = prefs.edit().putInt(KEY_CARD_SIZE_PERCENT, value.coerceIn(70, 100)).apply()

    // Front Card Wallpaper (The peelable sheet)
    var frontWallpaperPreset: String
        get() = prefs.getString(KEY_FRONT_WALLPAPER, "black") ?: "black"
        set(value) = prefs.edit().putString(KEY_FRONT_WALLPAPER, value).apply()

    var frontCustomWallpaperUri: String?
        get() = prefs.getString(KEY_FRONT_CUSTOM_URI, null)
        set(value) = prefs.edit().putString(KEY_FRONT_CUSTOM_URI, value).apply()

    // Back Underlying Wallpaper (Revealed behind the peel)
    var backWallpaperPreset: String
        get() = prefs.getString(KEY_BACK_WALLPAPER, "blue") ?: "blue"
        set(value) = prefs.edit().putString(KEY_BACK_WALLPAPER, value).apply()

    var backCustomWallpaperUri: String?
        get() = prefs.getString(KEY_BACK_CUSTOM_URI, null)
        set(value) = prefs.edit().putString(KEY_BACK_CUSTOM_URI, value).apply()

    // Legacy property mapping
    var wallpaperPreset: String
        get() = frontWallpaperPreset
        set(value) { frontWallpaperPreset = value }

    var customWallpaperUri: String?
        get() = frontCustomWallpaperUri
        set(value) { frontCustomWallpaperUri = value }

    var isAnimatedWavesEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATED_WAVES, true)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATED_WAVES, value).apply()

    var isFloatingParticlesEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_PARTICLES, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_PARTICLES, value).apply()

    // Status Bar / Info Bar customization toggles
    var showCarrierText: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CARRIER, false) // in the user's reference, top bar has time & battery
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CARRIER, value).apply()

    var showSignalIcon: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SIGNAL, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SIGNAL, value).apply()

    var showWifiIcon: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WIFI, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_WIFI, value).apply()

    var showBluetoothIcon: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BT, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BT, value).apply()

    var showNotificationDot: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIF, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIF, value).apply()

    var showBatteryIcon: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BATTERY_ICON, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BATTERY_ICON, value).apply()

    var showBatteryPercentage: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BATTERY_PCT, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BATTERY_PCT, value).apply()

    var showTopBarTime: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOP_TIME, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOP_TIME, value).apply()

    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CLOCK, value).apply()

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DATE, value).apply()

    var showCameraIcon: Boolean
        get() = false
        set(_) {}

    companion object {
        private const val KEY_LOCKSCREEN_ENABLED = "lockscreen_enabled"
        private const val KEY_24H_FORMAT = "24h_format"
        private const val KEY_CLOCK_POS = "clock_pos"
        private const val KEY_CLOCK_COLOR = "clock_color"
        private const val KEY_CARRIER_TEXT = "carrier_text"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_CARD_SIZE_PERCENT = "card_size_percent"
        private const val KEY_FRONT_WALLPAPER = "front_wallpaper_preset"
        private const val KEY_FRONT_CUSTOM_URI = "front_custom_wallpaper_uri"
        private const val KEY_BACK_WALLPAPER = "back_wallpaper_preset"
        private const val KEY_BACK_CUSTOM_URI = "back_custom_wallpaper_uri"
        private const val KEY_ANIMATED_WAVES = "animated_waves"
        private const val KEY_FLOATING_PARTICLES = "floating_particles"
        private const val KEY_SHOW_CARRIER = "show_carrier"
        private const val KEY_SHOW_SIGNAL = "show_signal"
        private const val KEY_SHOW_WIFI = "show_wifi"
        private const val KEY_SHOW_BT = "show_bt"
        private const val KEY_SHOW_NOTIF = "show_notif"
        private const val KEY_SHOW_BATTERY_ICON = "show_battery_icon"
        private const val KEY_SHOW_BATTERY_PCT = "show_battery_pct"
        private const val KEY_SHOW_TOP_TIME = "show_top_time"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_SHOW_CAMERA_ICON = "show_camera_icon"
    }
}
