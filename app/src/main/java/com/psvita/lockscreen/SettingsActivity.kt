package com.psvita.lockscreen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
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
                refreshPreview()
                Toast.makeText(this, "Imagem personalizada da frente definida!", Toast.LENGTH_SHORT).show()
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
                refreshPreview()
                Toast.makeText(this, "Imagem personalizada revelada definida!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configureDisplayRefreshRate()

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = LockPreferences(this)

        if (prefs.isLockscreenEnabled) {
            LockScreenService.start(this)
        }

        binding.livePreviewView.isPreviewMode = true

        initViews()
        bindEvents()
        checkOverlayPermission()
        refreshPreview()
    }

    private fun configureDisplayRefreshRate() {
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

    override fun onResume() {
        super.onResume()
        checkOverlayPermission()
        refreshPreview()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
            binding.cardOverlayPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
            binding.btnGrantOverlay.setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        } else {
            binding.cardOverlayPermission.visibility = View.GONE
        }
    }

    private fun refreshPreview() {
        binding.livePreviewView.reloadSettings()
        updatePreviewLabels()
    }

    private fun updatePreviewLabels() {
        val frontName = if (prefs.frontCustomWallpaperUri != null) {
            "Personalizado (Foto)"
        } else {
            when (prefs.frontWallpaperPreset) {
                "blue" -> "Azul Cristal"
                "red" -> "Vermelho Cósmico"
                "green" -> "Verde Esmeralda"
                "purple" -> "Roxo Neon"
                "amber" -> "Âmbar Sunset"
                else -> "Preto Onyx"
            }
        }

        val backName = if (prefs.backCustomWallpaperUri != null) {
            "Personalizado (Foto)"
        } else {
            when (prefs.backWallpaperPreset) {
                "red" -> "Vermelho Cósmico"
                "green" -> "Verde Esmeralda"
                "purple" -> "Roxo Neon"
                "amber" -> "Âmbar Sunset"
                "black" -> "Preto Onyx"
                else -> "Azul Cristal"
            }
        }

        binding.tvPreviewFrontInfo.text = "Frente: $frontName"
        binding.tvPreviewBackInfo.text = "Fundo: $backName"
    }

    private fun initViews() {
        // Master switch
        binding.switchEnableLock.isChecked = prefs.isLockscreenEnabled

        // Card Sheet Size
        val cardSize = prefs.cardSizePercent
        binding.seekBarCardSize.progress = (cardSize - 70).coerceIn(0, 30)
        binding.tvCardSizeValue.text = "$cardSize%"

        // Front Wallpaper radio
        if (prefs.frontCustomWallpaperUri == null) {
            when (prefs.frontWallpaperPreset) {
                "blue" -> binding.radioFrontBlue.isChecked = true
                "red" -> binding.radioFrontRed.isChecked = true
                "green" -> binding.radioFrontGreen.isChecked = true
                "purple" -> binding.radioFrontPurple.isChecked = true
                "amber" -> binding.radioFrontAmber.isChecked = true
                else -> binding.radioFrontBlack.isChecked = true
            }
        } else {
            binding.radioGroupFront.clearCheck()
        }

        // Back Wallpaper radio
        if (prefs.backCustomWallpaperUri == null) {
            when (prefs.backWallpaperPreset) {
                "red" -> binding.radioBackRed.isChecked = true
                "green" -> binding.radioBackGreen.isChecked = true
                "purple" -> binding.radioBackPurple.isChecked = true
                "amber" -> binding.radioBackAmber.isChecked = true
                "black" -> binding.radioBackBlack.isChecked = true
                else -> binding.radioBackBlue.isChecked = true
            }
        } else {
            binding.radioGroupBack.clearCheck()
        }

        // Clock, Date & Camera toggles
        binding.switchShowDate.isChecked = prefs.showDate
        binding.switchShowClock.isChecked = prefs.showClock
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

        // Fullscreen Preview button
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
                    refreshPreview()
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
            refreshPreview()
        }
        binding.btnSizeBalanced.setOnClickListener {
            prefs.cardSizePercent = 85
            binding.seekBarCardSize.progress = 15
            binding.tvCardSizeValue.text = "85%"
            refreshPreview()
        }
        binding.btnSizeLarge.setOnClickListener {
            prefs.cardSizePercent = 92
            binding.seekBarCardSize.progress = 22
            binding.tvCardSizeValue.text = "92%"
            refreshPreview()
        }
        binding.btnSizeFullscreen.setOnClickListener {
            prefs.cardSizePercent = 100
            binding.seekBarCardSize.progress = 30
            binding.tvCardSizeValue.text = "100%"
            refreshPreview()
        }

        // Front Wallpaper selection
        binding.radioGroupFront.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1) {
                prefs.frontCustomWallpaperUri = null
                prefs.frontWallpaperPreset = when (checkedId) {
                    binding.radioFrontBlue.id -> "blue"
                    binding.radioFrontRed.id -> "red"
                    binding.radioFrontGreen.id -> "green"
                    binding.radioFrontPurple.id -> "purple"
                    binding.radioFrontAmber.id -> "amber"
                    else -> "black"
                }
                refreshPreview()
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
            if (checkedId != -1) {
                prefs.backCustomWallpaperUri = null
                prefs.backWallpaperPreset = when (checkedId) {
                    binding.radioBackRed.id -> "red"
                    binding.radioBackGreen.id -> "green"
                    binding.radioBackPurple.id -> "purple"
                    binding.radioBackAmber.id -> "amber"
                    binding.radioBackBlack.id -> "black"
                    else -> "blue"
                }
                refreshPreview()
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
            refreshPreview()
        }
        binding.switchShowClock.setOnCheckedChangeListener { _, isChecked ->
            prefs.showClock = isChecked
            refreshPreview()
        }
        binding.switchTopBarTime.setOnCheckedChangeListener { _, isChecked ->
            prefs.showTopBarTime = isChecked
            refreshPreview()
        }
        binding.switch24h.setOnCheckedChangeListener { _, isChecked ->
            prefs.is24HourFormat = isChecked
            refreshPreview()
        }

        // Clock position
        binding.radioGroupClockPos.setOnCheckedChangeListener { _, checkedId ->
            prefs.clockPosition = when (checkedId) {
                binding.radioPosTopLeft.id -> ClockPosition.TOP_LEFT
                binding.radioPosBottomRight.id -> ClockPosition.BOTTOM_RIGHT
                else -> ClockPosition.BOTTOM_LEFT
            }
            refreshPreview()
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
            refreshPreview()
        }
        binding.switchShowBluetooth.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBluetoothIcon = isChecked
            refreshPreview()
        }
        binding.switchShowBattery.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBatteryIcon = isChecked
            refreshPreview()
        }
        binding.switchShowBatteryPct.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBatteryPercentage = isChecked
            refreshPreview()
        }
        binding.switchShowCarrier.setOnCheckedChangeListener { _, isChecked ->
            prefs.showCarrierText = isChecked
            refreshPreview()
        }
        binding.switchShowSignal.setOnCheckedChangeListener { _, isChecked ->
            prefs.showSignalIcon = isChecked
            refreshPreview()
        }
    }
}
