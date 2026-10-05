package com.psvita.lockscreen

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.psvita.lockscreen.data.ClockPosition
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.databinding.ActivitySettingsBinding
import com.psvita.lockscreen.service.LockScreenService

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: LockPreferences

    private val pickFrontLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                prefs.frontCustomWallpaperUri = uri.toString()
                prefs.frontWallpaperPreset = "custom"
                binding.radioGroupFront.clearCheck()
                Toast.makeText(this, "Custom front card image set!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickBackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                prefs.backCustomWallpaperUri = uri.toString()
                prefs.backWallpaperPreset = "custom"
                binding.radioGroupBack.clearCheck()
                Toast.makeText(this, "Custom revealed background image set!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LockPreferences(this)

        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }

        initViews()
        bindEvents()
    }

    private fun initViews() {
        // Master switch
        binding.switchEnableLock.isChecked = prefs.isLockscreenEnabled

        // Card Sheet Size
        val cardSize = prefs.cardSizePercent
        binding.seekBarCardSize.progress = (cardSize - 70).coerceIn(0, 30)
        binding.tvCardSizeValue.text = "$cardSize%"

        // Front Wallpaper radio
        when (prefs.frontWallpaperPreset) {
            "blue" -> binding.radioFrontBlue.isChecked = true
            "red" -> binding.radioFrontRed.isChecked = true
            "black" -> binding.radioFrontBlack.isChecked = true
            else -> binding.radioGroupFront.clearCheck()
        }

        // Back Wallpaper radio
        when (prefs.backWallpaperPreset) {
            "red" -> binding.radioBackRed.isChecked = true
            "green" -> binding.radioBackGreen.isChecked = true
            "blue" -> binding.radioBackBlue.isChecked = true
            else -> binding.radioGroupBack.clearCheck()
        }

        // Clock, Date & Camera toggles
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowClock.isChecked = prefs.showClock
        binding.switchCameraIcon.isChecked = prefs.showCameraIcon
        binding.switchTopBarTime.isChecked = prefs.showTopBarTime
        binding.switch24h.isChecked = prefs.is24HourFormat

        when (prefs.clockPosition) {
            ClockPosition.BOTTOM_LEFT -> binding.radioPosBottomLeft.isChecked = true
            ClockPosition.TOP_LEFT -> binding.radioPosTopLeft.isChecked = true
            ClockPosition.BOTTOM_RIGHT -> binding.radioPosBottomRight.isChecked = true
        }

        // Sound & Haptic
        binding.switchSound.isChecked = prefs.isSoundEnabled
        binding.switchHaptic.isChecked = prefs.isHapticEnabled

        // Status Bar (Info Bar) toggles
        binding.switchShowWifi.isChecked = prefs.showWifiIcon
        binding.switchShowBluetooth.isChecked = prefs.showBluetoothIcon
        binding.switchShowBattery.isChecked = prefs.showBatteryIcon
        binding.switchShowBatteryPct.isChecked = prefs.showBatteryPercentage
        binding.switchShowCarrier.isChecked = prefs.showCarrierText
        binding.switchShowSignal.isChecked = prefs.showSignalIcon
    }

    private fun bindEvents() {
        // Master switch
        binding.switchEnableLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.isLockscreenEnabled = isChecked
            if (isChecked) {
                LockScreenService.start(this)
            } else {
                LockScreenService.stop(this)
            }
        }

        // Preview button
        binding.btnPreview.setOnClickListener {
            val intent = Intent(this, VitaLockActivity::class.java)
            startActivity(intent)
        }

        // Card Sheet Size slider
        binding.seekBarCardSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 70
                binding.tvCardSizeValue.text = "$size%"
                if (fromUser) {
                    prefs.cardSizePercent = size
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Card Sheet Size quick preset buttons
        binding.btnSizeCompact.setOnClickListener {
            prefs.cardSizePercent = 75
            binding.seekBarCardSize.progress = 5
            binding.tvCardSizeValue.text = "75%"
        }
        binding.btnSizeBalanced.setOnClickListener {
            prefs.cardSizePercent = 85
            binding.seekBarCardSize.progress = 15
            binding.tvCardSizeValue.text = "85%"
        }
        binding.btnSizeLarge.setOnClickListener {
            prefs.cardSizePercent = 92
            binding.seekBarCardSize.progress = 22
            binding.tvCardSizeValue.text = "92%"
        }
        binding.btnSizeFullscreen.setOnClickListener {
            prefs.cardSizePercent = 100
            binding.seekBarCardSize.progress = 30
            binding.tvCardSizeValue.text = "100%"
        }

        // Front Wallpaper selection
        binding.radioGroupFront.setOnCheckedChangeListener { _, checkedId ->
            prefs.frontCustomWallpaperUri = null
            prefs.frontWallpaperPreset = when (checkedId) {
                R.id.radioFrontBlue -> "blue"
                R.id.radioFrontRed -> "red"
                else -> "black"
            }
        }

        binding.btnPickFront.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickFrontLauncher.launch(intent)
        }

        // Back Wallpaper selection
        binding.radioGroupBack.setOnCheckedChangeListener { _, checkedId ->
            prefs.backCustomWallpaperUri = null
            prefs.backWallpaperPreset = when (checkedId) {
                R.id.radioBackRed -> "red"
                R.id.radioBackGreen -> "green"
                else -> "blue"
            }
        }

        binding.btnPickBack.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickBackLauncher.launch(intent)
        }

        // Clock, Date & Camera toggles
        binding.switchShowDate.setOnCheckedChangeListener { _, isChecked ->
            prefs.showDate = isChecked
        }
        binding.switchShowClock.setOnCheckedChangeListener { _, isChecked ->
            prefs.showClock = isChecked
        }
        binding.switchCameraIcon.setOnCheckedChangeListener { _, isChecked ->
            prefs.showCameraIcon = isChecked
        }
        binding.switchTopBarTime.setOnCheckedChangeListener { _, isChecked ->
            prefs.showTopBarTime = isChecked
        }
        binding.switch24h.setOnCheckedChangeListener { _, isChecked ->
            prefs.is24HourFormat = isChecked
        }

        // Clock position
        binding.radioGroupClockPos.setOnCheckedChangeListener { _, checkedId ->
            prefs.clockPosition = when (checkedId) {
                R.id.radioPosTopLeft -> ClockPosition.TOP_LEFT
                R.id.radioPosBottomRight -> ClockPosition.BOTTOM_RIGHT
                else -> ClockPosition.BOTTOM_LEFT
            }
        }

        // Sound switch
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoundEnabled = isChecked
        }

        // Haptic switch
        binding.switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.isHapticEnabled = isChecked
        }

        // Status Bar (Info Bar) switches
        binding.switchShowWifi.setOnCheckedChangeListener { _, isChecked ->
            prefs.showWifiIcon = isChecked
        }
        binding.switchShowBluetooth.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBluetoothIcon = isChecked
        }
        binding.switchShowBattery.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBatteryIcon = isChecked
        }
        binding.switchShowBatteryPct.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBatteryPercentage = isChecked
        }
        binding.switchShowCarrier.setOnCheckedChangeListener { _, isChecked ->
            prefs.showCarrierText = isChecked
        }
        binding.switchShowSignal.setOnCheckedChangeListener { _, isChecked ->
            prefs.showSignalIcon = isChecked
        }
    }
}
