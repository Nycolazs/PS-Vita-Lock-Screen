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

    private val cameraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val cameraFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
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
        val now = Date()
        val is24h = prefs.is24HourFormat
        val timePattern = if (is24h) "HH:mm" else "h:mm"
        val timeStr = SimpleDateFormat(timePattern, Locale.US).format(now)
        val amPmStr = if (!is24h) SimpleDateFormat("a", Locale.US).format(now) else ""

        // English Date Format as in reference image: "December 12 (Thursday)"
        val datePattern = "MMMM d (EEEE)"
        val dateStr = SimpleDateFormat(datePattern, Locale.US).format(now)

        val clockColor = prefs.clockColor
        timePaint.color = clockColor
        datePaint.color = clockColor
        amPmPaint.color = clockColor
        cameraPaint.color = clockColor
        cameraFillPaint.color = clockColor

        val cardH = cardRect.height()
        val timeSize = cardH * 0.28f
        val amPmSize = timeSize * 0.40f
        val dateSize = timeSize * 0.28f

        timePaint.textSize = timeSize
        datePaint.textSize = dateSize
        amPmPaint.textSize = amPmSize

        val padX = cardRect.left + 54f
        val bottomY = cardRect.bottom - 48f

        when (prefs.clockPosition) {
            ClockPosition.BOTTOM_LEFT -> {
                timePaint.textAlign = Paint.Align.LEFT
                datePaint.textAlign = Paint.Align.LEFT
                amPmPaint.textAlign = Paint.Align.LEFT

                val timeY = bottomY - 14f
                val dateY = timeY - timeSize * 0.95f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, padX, dateY, datePaint)
                }

                if (prefs.showClock) {
                    canvas.drawText(timeStr, padX, timeY, timePaint)
                    if (amPmStr.isNotEmpty()) {
                        val timeWidth = timePaint.measureText(timeStr)
                        canvas.drawText(amPmStr, padX + timeWidth + 16f, timeY, amPmPaint)
                    }
                }

                // Camera Icon in Bottom-Left corner of the card
                if (prefs.showCameraIcon) {
                    val camX = cardRect.left + 54f
                    val camY = cardRect.bottom - 24f
                    drawCameraIcon(canvas, camX, camY)
                }
            }
            ClockPosition.TOP_LEFT -> {
                timePaint.textAlign = Paint.Align.LEFT
                datePaint.textAlign = Paint.Align.LEFT
                amPmPaint.textAlign = Paint.Align.LEFT

                val dateY = cardRect.top + 70f
                val timeY = dateY + timeSize * 1.05f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, padX, dateY, datePaint)
                }
                if (prefs.showClock) {
                    canvas.drawText(timeStr, padX, timeY, timePaint)
                    if (amPmStr.isNotEmpty()) {
                        val timeWidth = timePaint.measureText(timeStr)
                        canvas.drawText(amPmStr, padX + timeWidth + 16f, timeY, amPmPaint)
                    }
                }
            }
            ClockPosition.BOTTOM_RIGHT -> {
                timePaint.textAlign = Paint.Align.RIGHT
                datePaint.textAlign = Paint.Align.RIGHT
                amPmPaint.textAlign = Paint.Align.RIGHT

                val rightX = cardRect.right - 54f
                val timeY = bottomY - 14f
                val dateY = timeY - timeSize * 0.95f

                if (prefs.showDate) {
                    canvas.drawText(dateStr, rightX, dateY, datePaint)
                }
                if (prefs.showClock) {
                    canvas.drawText(timeStr, rightX, timeY, timePaint)
                }
            }
        }
    }

    private fun drawCameraIcon(canvas: Canvas, x: Float, y: Float) {
        val w = 34f
        val h = 24f
        val rect = RectF(x, y - h, x + w, y)
        // Camera body
        canvas.drawRoundRect(rect, 4f, 4f, cameraPaint)
        // Camera lens
        canvas.drawCircle(x + w / 2f, y - h / 2f, 6f, cameraPaint)
        canvas.drawCircle(x + w / 2f, y - h / 2f, 2.5f, cameraFillPaint)
        // Flash/shutter bump
        canvas.drawRect(x + 6f, y - h - 3f, x + 14f, y - h, cameraFillPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawOnCard(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}
