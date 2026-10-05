package com.psvita.lockscreen.view

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.psvita.lockscreen.data.LockPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VitaInfoBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val prefs = LockPreferences(context)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3CD070") // Authentic PS Vita green battery
        style = Paint.Style.FILL
    }

    private val notifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0099FF")
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#00CCFF"))
    }

    private val barBgPaint = Paint()
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#28FFFFFF")
        strokeWidth = 1.5f
    }

    private var batteryLevel: Int = 100
    private var isCharging: Boolean = false
    private var isWifiConnected: Boolean = false
    private var isBluetoothEnabled: Boolean = false

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            post { updateWifiStatus() }
        }
        override fun onLost(network: Network) {
            post { updateWifiStatus() }
        }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            post { updateWifiStatus() }
        }
    }

    var onStateChangedListener: (() -> Unit)? = null
    private var isMonitoring: Boolean = false

    init {
        startMonitoring()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        updateBatteryFromIntent(it)
                    }
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        updateBluetoothStatus()
                    }
                    WifiManager.NETWORK_STATE_CHANGED_ACTION,
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        updateWifiStatus()
                    }
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED -> {
                        notifyStateChanged()
                    }
                }
            }
        }
    }

    private fun updateBatteryFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            batteryLevel = (level * 100) / scale
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        isCharging = plugged > 0 ||
                     status == BatteryManager.BATTERY_STATUS_CHARGING ||
                     status == BatteryManager.BATTERY_STATUS_FULL
        notifyStateChanged()
    }

    private fun updateWifiStatus() {
        val connected = checkWifiConnected()
        if (isWifiConnected != connected) {
            isWifiConnected = connected
            notifyStateChanged()
        }
    }

    private fun checkWifiConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNet = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNet) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun updateBluetoothStatus() {
        val enabled = checkBluetoothEnabled()
        if (isBluetoothEnabled != enabled) {
            isBluetoothEnabled = enabled
            notifyStateChanged()
        }
    }

    private fun checkBluetoothEnabled(): Boolean {
        val isGlobalOn = try {
            Settings.Global.getInt(context.contentResolver, "bluetooth_on", 0) == 1
        } catch (_: Exception) { false }

        val isAdapterOn = try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.isEnabled == true
        } catch (_: Exception) { false }

        return isGlobalOn || isAdapterOn
    }

    fun startMonitoring() {
        if (isMonitoring) {
            // Re-check current states
            isWifiConnected = checkWifiConnected()
            isBluetoothEnabled = checkBluetoothEnabled()
            val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            sticky?.let { updateBatteryFromIntent(it) }
            notifyStateChanged()
            return
        }
        isMonitoring = true

        // 1. Query sticky battery intent immediately
        val stickyBattery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        stickyBattery?.let { updateBatteryFromIntent(it) }

        // 2. Query initial Wi-Fi and Bluetooth state
        isWifiConnected = checkWifiConnected()
        isBluetoothEnabled = checkBluetoothEnabled()

        // 3. Register broadcast receiver for real-time changes
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}

        // 4. Register network callback for instant Wi-Fi connect/disconnect events
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) {}

        notifyStateChanged()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    private fun notifyStateChanged() {
        invalidate()
        onStateChangedListener?.invoke()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startMonitoring()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopMonitoring()
    }

    private var cardLeft: Float = -1f
    private var cardRight: Float = -1f

    fun setCardBounds(left: Float, right: Float) {
        cardLeft = left
        cardRight = right
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (42 * resources.displayMetrics.density).toInt()
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        val paddingLeft = if (cardLeft > 0f) maxOf(cardLeft + 10f * density, 24f * density) else 24f * density
        val paddingRight = if (cardRight > 0f && cardRight < w) maxOf((w - cardRight) + 10f * density, 24f * density) else 24f * density
        val centerY = h / 2f

        // Draw sleek glossy bar background & bottom divider line
        barBgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            Color.parseColor("#1E2128"),
            Color.parseColor("#0C0E12"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, barBgPaint)
        canvas.drawLine(0f, h - 1f, w, h - 1f, dividerPaint)

        // --- Left Side: Carrier, Signal, Wi-Fi, Bluetooth (Authentic PS Vita order & placement) ---
        var currentX = paddingLeft

        if (prefs.showCarrierText) {
            val carrier = prefs.carrierText
            textPaint.textSize = h * 0.42f
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            canvas.drawText(carrier, currentX, centerY + textPaint.textSize * 0.35f, textPaint)
            currentX += textPaint.measureText(carrier) + 14f * density
        }

        if (prefs.showSignalIcon) {
            val barWidth = 3f * density
            val barGap = 2f * density
            val maxBarHeight = 14f * density
            fillPaint.color = Color.WHITE
            for (i in 0..3) {
                val barH = maxBarHeight * ((i + 1) / 4f)
                val bx = currentX + i * (barWidth + barGap)
                val by = centerY + maxBarHeight / 2f - barH
                canvas.drawRoundRect(
                    RectF(bx, by, bx + barWidth, centerY + maxBarHeight / 2f),
                    1f * density, 1f * density, fillPaint
                )
            }
            currentX += 4 * (barWidth + barGap) + 14f * density
        }

        // Authentic PS Vita Wi-Fi Icon (Shown when enabled in settings AND connected to Wi-Fi)
        if (prefs.showWifiIcon && isWifiConnected) {
            val wifiW = 18f * density
            val centerX = currentX + wifiW / 2f
            val dotY = centerY + 5f * density

            // Central bottom dot
            fillPaint.color = Color.WHITE
            canvas.drawCircle(centerX, dotY, 1.8f * density, fillPaint)

            // 3 Concentric curved waves
            iconPaint.color = Color.WHITE
            iconPaint.strokeWidth = 1.9f * density
            iconPaint.strokeCap = Paint.Cap.ROUND

            val r1 = 4.6f * density
            val rect1 = RectF(centerX - r1, dotY - r1, centerX + r1, dotY + r1)
            canvas.drawArc(rect1, 225f, 90f, false, iconPaint)

            val r2 = 8.2f * density
            val rect2 = RectF(centerX - r2, dotY - r2, centerX + r2, dotY + r2)
            canvas.drawArc(rect2, 225f, 90f, false, iconPaint)

            val r3 = 11.8f * density
            val rect3 = RectF(centerX - r3, dotY - r3, centerX + r3, dotY + r3)
            canvas.drawArc(rect3, 225f, 90f, false, iconPaint)

            currentX += wifiW + 14f * density
        }

        // Authentic PS Vita Bluetooth Icon (Shown when enabled in settings AND Bluetooth is ON)
        if (prefs.showBluetoothIcon && isBluetoothEnabled) {
            val halfH = 7.5f * density
            val topY = centerY - halfH
            val bottomY = centerY + halfH
            val spineX = currentX + 4.2f * density
            val leftX = currentX + 0.4f * density
            val rightX = currentX + 8.4f * density
            val upperPeakY = centerY - halfH * 0.5f
            val lowerPeakY = centerY + halfH * 0.5f
            val tailTopY = centerY - halfH * 0.5f
            val tailBottomY = centerY + halfH * 0.5f

            val btPath = Path().apply {
                moveTo(leftX, tailBottomY)
                lineTo(rightX, upperPeakY)
                lineTo(spineX, topY)
                lineTo(spineX, bottomY)
                lineTo(rightX, lowerPeakY)
                lineTo(leftX, tailTopY)
            }

            iconPaint.color = Color.WHITE
            iconPaint.strokeWidth = 1.9f * density
            iconPaint.strokeCap = Paint.Cap.ROUND
            iconPaint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(btPath, iconPaint)

            currentX += 9f * density + 14f * density
        }

        // --- Center: PS Vita Notification Dot ---
        if (prefs.showNotificationDot) {
            canvas.drawCircle(w / 2f, centerY, 7f * density, notifPaint)
            val notifCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(w / 2f, centerY, 2.5f * density, notifCenterPaint)
        }

        // --- Right Side: Top Bar Time and Authentic Green Battery Capsule ---
        var rightX = w - paddingRight

        if (prefs.showBatteryIcon) {
            val battW = 32f * density
            val battH = 16f * density
            val battX = rightX - battW
            val battY = centerY - battH / 2f

            // Battery rounded shell
            iconPaint.strokeWidth = 1.9f * density
            iconPaint.strokeCap = Paint.Cap.ROUND
            iconPaint.strokeJoin = Paint.Join.ROUND
            val battRect = RectF(battX, battY, battX + battW, battY + battH)
            canvas.drawRoundRect(battRect, 3.5f * density, 3.5f * density, iconPaint)

            // Positive terminal nipple
            fillPaint.color = Color.WHITE
            val nippleW = 2.4f * density
            val nippleH = 6f * density
            val nippleRect = RectF(
                battX + battW,
                centerY - nippleH / 2f,
                battX + battW + nippleW,
                centerY + nippleH / 2f
            )
            canvas.drawRoundRect(nippleRect, 1.2f * density, 1.2f * density, fillPaint)

            // Green battery fill (Authentic PS Vita green #3CD070)
            val fillPad = 2.2f * density
            val maxFillW = battW - (fillPad * 2)
            val currentFillW = (maxFillW * (batteryLevel / 100f)).coerceIn(0f, maxFillW)
            fillPaint.color = if (batteryLevel <= 15) Color.parseColor("#E03030") else Color.parseColor("#3CD070")
            val fillRect = RectF(
                battX + fillPad,
                battY + fillPad,
                battX + fillPad + currentFillW,
                battY + battH - fillPad
            )
            canvas.drawRoundRect(fillRect, 2f * density, 2f * density, fillPaint)

            // Charging Lightning Bolt (Authentic PS Vita charging indicator)
            if (isCharging) {
                val bcX = battX + battW / 2f
                val bcY = centerY
                val boltPath = Path().apply {
                    moveTo(bcX + 1.2f * density, bcY - 5.2f * density)
                    lineTo(bcX - 2.8f * density, bcY + 0.6f * density)
                    lineTo(bcX - 0.2f * density, bcY + 0.6f * density)
                    lineTo(bcX - 1.2f * density, bcY + 5.2f * density)
                    lineTo(bcX + 2.8f * density, bcY - 0.6f * density)
                    lineTo(bcX + 0.2f * density, bcY - 0.6f * density)
                    close()
                }
                val boltShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#90000000")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.4f * density
                    strokeJoin = Paint.Join.ROUND
                }
                val boltFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawPath(boltPath, boltShadowPaint)
                canvas.drawPath(boltPath, boltFillPaint)
            }

            rightX = battX - 10f * density
        }

        if (prefs.showBatteryPercentage) {
            val pctText = "$batteryLevel%"
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = h * 0.38f
            textPaint.color = Color.WHITE
            canvas.drawText(pctText, rightX, centerY + textPaint.textSize * 0.35f, textPaint)
            rightX -= textPaint.measureText(pctText) + 10f * density
        }

        // Top bar time: e.g. "7:53 PM" (Authentic Vita right top corner)
        if (prefs.showTopBarTime) {
            val now = Date()
            val pattern = if (prefs.is24HourFormat) "HH:mm" else "h:mm a"
            val timeText = SimpleDateFormat(pattern, Locale.US).format(now)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = h * 0.42f
            textPaint.color = Color.WHITE
            canvas.drawText(timeText, rightX, centerY + textPaint.textSize * 0.35f, textPaint)
        }
    }
}
