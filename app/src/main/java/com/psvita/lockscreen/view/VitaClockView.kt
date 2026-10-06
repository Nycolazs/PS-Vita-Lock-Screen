package com.psvita.lockscreen.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.psvita.lockscreen.data.ClockPosition
import com.psvita.lockscreen.data.LockPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VitaClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val prefs = LockPreferences(context)

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val amPmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private var cachedTimeStr: String = ""
    private var cachedAmPmStr: String = ""
    private var cachedDateStr: String = ""
    private var lastFormattedMinute: Long = -1L

    fun updateTimeStrings(force: Boolean = false) {
        val currentMinute = System.currentTimeMillis() / 60000L
        if (!force && currentMinute == lastFormattedMinute && cachedTimeStr.isNotEmpty()) return
        lastFormattedMinute = currentMinute
        val now = Date()
        val is24h = prefs.is24HourFormat
        val timePattern = if (is24h) "HH:mm" else "h:mm"
        cachedTimeStr = SimpleDateFormat(timePattern, Locale.US).format(now)
        cachedAmPmStr = if (!is24h) SimpleDateFormat("a", Locale.US).format(now) else ""
        val datePattern = "MMMM d (EEEE)"
        cachedDateStr = SimpleDateFormat(datePattern, Locale.US).format(now)
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            updateTimeStrings(force = true)
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTimeStrings(force = true)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(timeReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(timeReceiver)
        } catch (_: Exception) {}
    }

    /**
     * Draws the clock, date, and camera icon relative to the card's bounding box.
     */
    fun drawOnCard(canvas: Canvas, cardRect: RectF) {
        updateTimeStrings()
        val timeStr = cachedTimeStr
        val amPmStr = cachedAmPmStr
        val dateStr = cachedDateStr

        val clockColor = prefs.clockColor
        timePaint.color = clockColor
        datePaint.color = clockColor
        amPmPaint.color = clockColor

        val density = context.resources.displayMetrics.density
        val cardH = cardRect.height()
        val cardW = cardRect.width()
        val scaleBasis = minOf(cardH, cardW * 0.56f)

        val timeSize = (scaleBasis * 0.27f).coerceAtLeast(16f * density)
        val amPmSize = timeSize * 0.40f
        val dateSize = timeSize * 0.28f

        timePaint.textSize = timeSize
        datePaint.textSize = dateSize
        amPmPaint.textSize = amPmSize

        val padX = cardRect.left + maxOf(18f * density, cardW * 0.045f)
        val padRightX = cardRect.right - maxOf(18f * density, cardW * 0.045f)
        val bottomY = cardRect.bottom - maxOf(16f * density, cardH * 0.05f)

        when (prefs.clockPosition) {
            ClockPosition.BOTTOM_LEFT -> {
                timePaint.textAlign = Paint.Align.LEFT
                datePaint.textAlign = Paint.Align.LEFT
                amPmPaint.textAlign = Paint.Align.LEFT

                val timeY = bottomY - 6f * density
                val dateY = timeY - timeSize * 0.95f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, padX, dateY, datePaint)
                }

                if (prefs.showClock) {
                    canvas.drawText(timeStr, padX, timeY, timePaint)
                    if (amPmStr.isNotEmpty()) {
                        val timeWidth = timePaint.measureText(timeStr)
                        canvas.drawText(amPmStr, padX + timeWidth + 8f * density, timeY, amPmPaint)
                    }
                }
            }
            ClockPosition.TOP_LEFT -> {
                timePaint.textAlign = Paint.Align.LEFT
                datePaint.textAlign = Paint.Align.LEFT
                amPmPaint.textAlign = Paint.Align.LEFT

                val dateY = cardRect.top + maxOf(24f * density, cardH * 0.08f)
                val timeY = dateY + timeSize * 1.02f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, padX, dateY, datePaint)
                }
                if (prefs.showClock) {
                    canvas.drawText(timeStr, padX, timeY, timePaint)
                    if (amPmStr.isNotEmpty()) {
                        val timeWidth = timePaint.measureText(timeStr)
                        canvas.drawText(amPmStr, padX + timeWidth + 8f * density, timeY, amPmPaint)
                    }
                }
            }
            ClockPosition.BOTTOM_RIGHT -> {
                timePaint.textAlign = Paint.Align.RIGHT
                datePaint.textAlign = Paint.Align.RIGHT
                amPmPaint.textAlign = Paint.Align.RIGHT

                val timeY = bottomY - 6f * density
                val dateY = timeY - timeSize * 0.95f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, padRightX, dateY, datePaint)
                }
                if (prefs.showClock) {
                    canvas.drawText(timeStr, padRightX, timeY, timePaint)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOnCard(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}
