package com.psvita.lockscreen.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.psvita.lockscreen.R
import com.psvita.lockscreen.audio.SoundManager
import com.psvita.lockscreen.data.LockPreferences
import com.psvita.lockscreen.math.PagePeelMath
import com.psvita.lockscreen.math.PeelPoint
import com.psvita.lockscreen.math.PeelRect
import kotlin.math.*
import kotlin.random.Random

class VitaPeelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnUnlockListener {
        fun onUnlock()
    }

    var onUnlockListener: OnUnlockListener? = null

    private val prefs = LockPreferences(context)
    private val soundManager = SoundManager(context)

    // Vibrator
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Touch & Animation State
    private var isDragging = false
    private var isAnimating = false
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var lastTopRightTapTime = 0L

    // Idle flutter animation
    private var idleAnimator: ValueAnimator? = null
    private var idleFlutterOffset = 0f

    // Live wave background animation
    private var waveAnimator: ValueAnimator? = null
    private var waveTime = 0f

    // Card geometry (Rounded rectangle sheet)
    private val cardRect = RectF()
    private val cardCornerRadius = 28f

    // Bitmaps for Front and Back photos
    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Paints
    private val consoleFramePaint = Paint().apply { color = Color.BLACK }
    private val cardCutoutShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000") // Subtle groove shadow under cutout
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * resources.displayMetrics.density
    }
    private val cardCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFFFFF") // Authentic clean gray/white cutout line
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * resources.displayMetrics.density
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * resources.displayMetrics.density
    }
    private val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0C0D10")
        style = Paint.Style.FILL
    }
    private val peelBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rollHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Floating particles
    private data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speedY: Float,
        val swaySpeed: Float,
        val swayDist: Float,
        val alpha: Int
    )
    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Child views
    private val infoBar = VitaInfoBarView(context)
    private val clockView = VitaClockView(context)

    init {
        infoBar.onStateChangedListener = { postInvalidate() }
        infoBar.startMonitoring()
        loadWallpapers()
        initParticles()
        startIdleAnimation()
        startWaveAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        infoBar.startMonitoring()
    }

    fun reloadSettings() {
        loadWallpapers()
        if (width > 0 && height > 0) {
            updateCardGeometry(width, height)
        }
        infoBar.startMonitoring()
        infoBar.invalidate()
        clockView.invalidate()
        invalidate()
    }

    fun resetPeel() {
        idleAnimator?.cancel()
        isDragging = false
        isAnimating = false
        velocityTracker?.recycle()
        velocityTracker = null

        if (width > 0 && height > 0) {
            updateCardGeometry(width, height)
        } else {
            val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, 0f)
            currentTouchX = idle.x
            currentTouchY = idle.y
        }
        idleFlutterOffset = 0f

        startIdleAnimation()
        reloadSettings()
    }

    private fun initParticles() {
        particles.clear()
        val rnd = Random(42)
        for (i in 0..26) {
            particles.add(
                Particle(
                    x = rnd.nextFloat() * 2400f,
                    y = rnd.nextFloat() * 1080f,
                    radius = rnd.nextFloat() * 2.5f + 1f,
                    speedY = rnd.nextFloat() * 0.6f + 0.3f,
                    swaySpeed = rnd.nextFloat() * 1.5f + 0.8f,
                    swayDist = rnd.nextFloat() * 20f + 10f,
                    alpha = rnd.nextInt(40, 160)
                )
            )
        }
    }

    private fun loadWallpapers() {
        // 1. Front card wallpaper
        try {
            val frontUri = prefs.frontCustomWallpaperUri
            if (frontUri != null) {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(frontUri))
                frontBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } else {
                frontBitmap = when (prefs.frontWallpaperPreset) {
                    "blue" -> BitmapFactory.decodeResource(resources, R.drawable.bg_vita_blue)
                    "red" -> BitmapFactory.decodeResource(resources, R.drawable.bg_vita_red)
                    "green" -> BitmapFactory.decodeResource(resources, R.drawable.bg_vita_green)
                    "purple" -> BitmapFactory.decodeResource(resources, R.drawable.bg_vita_purple)
                    "amber" -> BitmapFactory.decodeResource(resources, R.drawable.bg_vita_amber)
                    else -> null // Solid black card as in reference image!
                }
            }
        } catch (_: Exception) {
            frontBitmap = null
        }

        // 2. Underlying revealed wallpaper (Back Photo)
        try {
            val backUri = prefs.backCustomWallpaperUri
            if (backUri != null) {
                val inputStream = context.contentResolver.openInputStream(Uri.parse(backUri))
                backBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } else {
                // If no custom photo is chosen, presets render as rich live animated waves!
                backBitmap = null
            }
        } catch (_: Exception) {
            backBitmap = null
        }
    }

    private fun startIdleAnimation() {
        idleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                if (!isDragging && !isAnimating) {
                    val progress = it.animatedValue as Float
                    idleFlutterOffset = sin(progress * Math.PI.toFloat()) * 12f
                    invalidate()
                }
            }
            start()
        }
    }

    private fun startWaveAnimation() {
        waveAnimator = ValueAnimator.ofFloat(0f, 1000f).apply {
            duration = 60000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                waveTime = (it.animatedValue as Float) * 0.08f
                invalidate()
            }
            start()
        }
    }

    fun updateCardGeometry(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val topBarH = (38 * resources.displayMetrics.density).toInt()
        infoBar.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(topBarH, MeasureSpec.EXACTLY)
        )
        infoBar.layout(0, 0, w, topBarH)

        val sizePct = prefs.cardSizePercent.coerceIn(70, 100)
        val availHeight = (h - topBarH).toFloat()

        if (sizePct >= 100) {
            cardRect.set(0f, topBarH.toFloat(), w.toFloat(), h.toFloat())
        } else {
            val factor = (sizePct - 70f) / 30f // 0.0 to 1.0
            val minW = w * 0.70f
            val maxW = w * 0.97f
            val minH = availHeight * 0.74f
            val maxH = availHeight * 0.97f

            val cardW = minW + (maxW - minW) * factor
            val cardH = minH + (maxH - minH) * factor

            val centerX = w * 0.5f
            val centerY = topBarH + availHeight * 0.5f
            cardRect.set(
                centerX - cardW * 0.5f,
                centerY - cardH * 0.5f,
                centerX + cardW * 0.5f,
                centerY + cardH * 0.5f
            )
        }
        infoBar.setCardBounds(cardRect.left, cardRect.right)

        if (!isDragging && !isAnimating) {
            val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, idleFlutterOffset)
            currentTouchX = idle.x
            currentTouchY = idle.y
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            updateCardGeometry(w, h)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return true

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Quick-tap unlock shortcut: Tap Top-Right then Bottom-Left of the card
                val isBottomLeft = x < cardRect.left + cardRect.width() * 0.35f && y > cardRect.bottom - cardRect.height() * 0.4f
                val isTopRight = x > cardRect.right - cardRect.width() * 0.35f && y < cardRect.top + cardRect.height() * 0.4f

                if (isBottomLeft && (System.currentTimeMillis() - lastTopRightTapTime) < 800L) {
                    triggerUnlockAnimation()
                    return true
                }
                if (isTopRight) {
                    lastTopRightTapTime = System.currentTimeMillis()
                }

                // Check if touch is near top-right corner of the card
                val distFromCorner = sqrt((x - cardRect.right).pow(2) + (y - cardRect.top).pow(2))
                if (distFromCorner < cardRect.width() * 0.65f || isTopRight) {
                    isDragging = true
                    currentTouchX = x
                    currentTouchY = y
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    soundManager.playPeelSound()
                    vibrateTouch()
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    currentTouchX = x.coerceIn(cardRect.left - cardRect.width() * 0.4f, cardRect.right)
                    currentTouchY = y.coerceIn(cardRect.top, cardRect.bottom + cardRect.height() * 0.4f)
                    velocityTracker?.addMovement(event)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
                    val peelState = PagePeelMath.calculateCardPeel(peelRect, currentTouchX, currentTouchY)
                    val isFlingUnlock = vx < -1200f || (vx < -600f && vy > 600f)

                    if (peelState.isThresholdMet || isFlingUnlock) {
                        triggerUnlockAnimation()
                    } else {
                        triggerSpringBackAnimation()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerUnlockAnimation() {
        isAnimating = true
        soundManager.playUnlockSound()
        vibrateUnlock()

        val startX = currentTouchX
        val startY = currentTouchY
        val targetX = cardRect.left - cardRect.width() * 0.6f
        val targetY = cardRect.bottom + cardRect.height() * 0.8f

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = AccelerateInterpolator(1.6f)
            addUpdateListener {
                val f = it.animatedValue as Float
                currentTouchX = startX + (targetX - startX) * f
                currentTouchY = startY + (targetY - startY) * f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    onUnlockListener?.onUnlock()
                }
            })
        }
        anim.start()
    }

    private fun triggerSpringBackAnimation() {
        isAnimating = true
        val startX = currentTouchX
        val startY = currentTouchY
        val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
        val idle = PagePeelMath.calculateCardIdleCurl(peelRect, 0f)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                val f = it.animatedValue as Float
                currentTouchX = startX + (idle.x - startX) * f
                currentTouchY = startY + (idle.y - startY) * f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
        }
        anim.start()
    }

    private fun vibrateTouch() {
        if (!prefs.isHapticEnabled || vibrator == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    }

    private fun vibrateUnlock() {
        if (!prefs.isHapticEnabled || vibrator == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cornerRadius = if (prefs.cardSizePercent >= 100) 0f else cardCornerRadius

        // 1. Draw Base Front Wallpaper across the full screen!
        // Outside the card is NOT black: it is the same background as the card!
        drawBaseWallpaper(canvas, w, h)

        val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)

        // Determine current touch for card peel
        val touchX = if (!isDragging && !isAnimating) {
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, idleFlutterOffset)
            idle.x
        } else {
            currentTouchX
        }

        val touchY = if (!isDragging && !isAnimating) {
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, idleFlutterOffset)
            idle.y
        } else {
            currentTouchY
        }

        val peelState = PagePeelMath.calculateCardPeel(peelRect, touchX, touchY)
        val foldLine = peelState.foldLine

        val fullCardPath = Path().apply {
            addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        if (foldLine == null) {
            // Check if card has been fully peeled past the threshold during unlock animation
            if (peelState.isThresholdMet && (touchX < cardRect.left || touchY > cardRect.bottom)) {
                infoBar.draw(canvas)
                return
            }
            // No peel active: draw clock, date, camera icon on the card
            clockView.drawOnCard(canvas, cardRect)

            // Draw the PS Vita Cutout Line ("recorte cinza / branca do quadrado do meio")
            if (cornerRadius > 0f) {
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutShadowPaint)
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutPaint)
            }
            infoBar.draw(canvas)
            return
        }

        // --- Active Peel (Resting curl or dragging) ---
        // 2. Draw REVEALED UNDERNEATH LAYER inside cardRect
        canvas.save()
        canvas.clipPath(fullCardPath)
        drawUnderlyingLayer(canvas, cardRect)
        canvas.restore()

        // --- 3. Card Peeling Geometry ---
        val p1 = foldLine.p1
        val p2 = foldLine.p2
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = hypot(dx, dy)
        val ux = if (len > 0f) dx / len else 1f
        val uy = if (len > 0f) dy / len else 0f

        val span = 5000f
        val p1ExtX = p1.x - ux * span
        val p1ExtY = p1.y - uy * span
        val p2ExtX = p2.x + ux * span
        val p2ExtY = p2.y + uy * span

        val nx = foldLine.normal.x
        val ny = foldLine.normal.y

        // Half-plane containing the peeled corner (in direction of -normal)
        val cornerSidePoly = Path().apply {
            moveTo(p1ExtX, p1ExtY)
            lineTo(p2ExtX, p2ExtY)
            lineTo(p2ExtX - nx * span, p2ExtY - ny * span)
            lineTo(p1ExtX - nx * span, p1ExtY - ny * span)
            close()
        }

        // Unpeeled region = Full card minus cornerSidePoly
        val unpeeledRegion = Path()
        unpeeledRegion.op(fullCardPath, cornerSidePoly, Path.Op.DIFFERENCE)

        // Peeled region = Full card intersected with cornerSidePoly
        val peeledRegion = Path()
        peeledRegion.op(fullCardPath, cornerSidePoly, Path.Op.INTERSECT)

        // Draw Front Card on the Unpeeled Region
        canvas.save()
        canvas.clipPath(unpeeledRegion)
        drawFrontCardContent(canvas, cardRect)
        canvas.restore()

        // Draw the PS Vita Cutout Line around card frame ("recorte cinza / branca")
        if (cornerRadius > 0f) {
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutShadowPaint)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutPaint)
        }

        // --- 5. Draw Drop Shadow beneath the folded sheet onto the underlying photo ---
        val shadowWidth = 50f
        dropShadowPaint.shader = LinearGradient(
            foldLine.midpoint.x - nx * shadowWidth,
            foldLine.midpoint.y - ny * shadowWidth,
            foldLine.midpoint.x,
            foldLine.midpoint.y,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#B0000000")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        val shadowPath = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p2.x - nx * shadowWidth, p2.y - ny * shadowWidth)
            lineTo(p1.x - nx * shadowWidth, p1.y - ny * shadowWidth)
            close()
        }

        canvas.save()
        canvas.clipPath(fullCardPath)
        canvas.drawPath(shadowPath, dropShadowPaint)
        canvas.restore()

        // --- 6. Draw Backside of the Curled Sheet (Reflected Flap with rounded corner) ---
        val matrixValues = PagePeelMath.calculateReflectionMatrix(foldLine)
        val reflectionMatrix = Matrix().apply { setValues(matrixValues) }

        val foldedFlapPath = Path()
        peeledRegion.transform(reflectionMatrix, foldedFlapPath)

        // Dark grey to metallic shaded gradient matching PS Vita reference image
        peelBackPaint.shader = LinearGradient(
            foldLine.midpoint.x, foldLine.midpoint.y,
            touchX, touchY,
            intArrayOf(
                Color.parseColor("#646A74"),
                Color.parseColor("#444951"),
                Color.parseColor("#2A2D33"),
                Color.parseColor("#1B1D21")
            ),
            floatArrayOf(0f, 0.25f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(foldedFlapPath, peelBackPaint)
        canvas.drawPath(foldedFlapPath, cardBorderPaint)

        // --- 7. Draw Curled Cylindrical Roll Highlight along the fold crease ---
        val rollRadius = 26f
        rollHighlightPaint.shader = LinearGradient(
            foldLine.midpoint.x, foldLine.midpoint.y,
            foldLine.midpoint.x + nx * rollRadius,
            foldLine.midpoint.y + ny * rollRadius,
            intArrayOf(
                Color.parseColor("#45000000"),
                Color.parseColor("#A0FFFFFF"),
                Color.parseColor("#25FFFFFF"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        val rollPath = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p2.x + nx * rollRadius, p2.y + ny * rollRadius)
            lineTo(p1.x + nx * rollRadius, p1.y + ny * rollRadius)
            close()
        }
        canvas.drawPath(rollPath, rollHighlightPaint)

        // 8. Draw Top Info Bar (Black status strip with time, battery, icons)
        infoBar.draw(canvas)
    }

    /**
     * Draws the Underlying Revealed Layer (Behind the card).
     * By default: classic vibrant PS Vita Blue Waves & crystal particles!
     */
    private fun drawUnderlyingLayer(canvas: Canvas, rect: RectF) {
        if (backBitmap != null) {
            val bmp = backBitmap!!
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
        } else {
            // Live animated PS Vita Crystal Waves for chosen color preset
            val w = rect.width()
            val h = rect.height()

            val (topColor, bottomColor, waveR, waveG, waveB) = when (prefs.backWallpaperPreset) {
                "red" -> arrayOf(Color.parseColor("#340810"), Color.parseColor("#800B1D"), 255, 45, 80)
                "green" -> arrayOf(Color.parseColor("#062414"), Color.parseColor("#0B6030"), 30, 220, 100)
                "purple" -> arrayOf(Color.parseColor("#220834"), Color.parseColor("#581285"), 180, 50, 255)
                "amber" -> arrayOf(Color.parseColor("#341806"), Color.parseColor("#80400B"), 255, 160, 30)
                "black" -> arrayOf(Color.parseColor("#18191C"), Color.parseColor("#0A0B0D"), 100, 110, 125)
                else -> arrayOf(Color.parseColor("#081636"), Color.parseColor("#0046A0"), 0, 160, 255) // authentic blue
            }

            val bgGrad = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                topColor as Int, bottomColor as Int,
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(rect, Paint().apply { shader = bgGrad })

            val t = waveTime
            val r = waveR as Int
            val g = waveG as Int
            val b = waveB as Int

            wavePaint.color = Color.argb(90, r, g, b)
            val path1 = Path().apply {
                moveTo(rect.left, rect.bottom)
                for (x in rect.left.toInt()..rect.right.toInt() step 20) {
                    val fx = x.toFloat()
                    val y = rect.top + h * 0.55f + sin(fx * 0.0028f + t * 0.9f) * 60f + cos(fx * 0.0016f - t * 0.5f) * 35f
                    lineTo(fx, y)
                }
                lineTo(rect.right, rect.bottom)
                close()
            }
            canvas.drawPath(path1, wavePaint)

            wavePaint.color = Color.argb(125, r, g, b)
            val path2 = Path().apply {
                moveTo(rect.left, rect.bottom)
                for (x in rect.left.toInt()..rect.right.toInt() step 20) {
                    val fx = x.toFloat()
                    val y = rect.top + h * 0.65f + sin(fx * 0.0036f - t * 0.7f + 1.2f) * 80f + cos(fx * 0.0020f + t * 0.6f) * 30f
                    lineTo(fx, y)
                }
                lineTo(rect.right, rect.bottom)
                close()
            }
            canvas.drawPath(path2, wavePaint)
        }

        // Draw ambient floating crystal dust
        if (prefs.isFloatingParticlesEnabled) {
            for (p in particles) {
                p.y -= p.speedY
                if (p.y < rect.top) {
                    p.y = rect.bottom
                    p.x = rect.left + Random.nextFloat() * rect.width()
                }
                val swayX = p.x + sin(waveTime * p.swaySpeed) * p.swayDist
                particlePaint.color = Color.argb(p.alpha, 255, 255, 255)
                canvas.drawCircle(swayX, p.y, p.radius, particlePaint)
            }
        }
    }

    /**
     * Draws the Base Front Wallpaper across the full screen.
     * Outside the card is NOT black: it is the same background as the card!
     */
    private fun drawBaseWallpaper(canvas: Canvas, w: Float, h: Float) {
        if (frontBitmap != null) {
            val bmp = frontBitmap!!
            val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
            val viewRatio = w / h
            val src = if (bmpRatio > viewRatio) {
                val newW = (bmp.height * viewRatio).toInt()
                val startX = (bmp.width - newW) / 2
                Rect(startX, 0, startX + newW, bmp.height)
            } else {
                val newH = (bmp.width / viewRatio).toInt()
                val startY = (bmp.height - newH) / 2
                Rect(0, startY, bmp.width, startY + newH)
            }
            val dst = Rect(0, 0, w.toInt(), h.toInt())
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
        } else {
            // Default deep onyx black matching PS Vita reference
            canvas.drawRect(0f, 0f, w, h, cardFillPaint)
        }
    }

    /**
     * Draws the Front Card Content (The peelable sheet).
     * Renders seamlessly continuous with the base wallpaper, plus Clock, Date, Camera Icon!
     */
    private fun drawFrontCardContent(canvas: Canvas, rect: RectF) {
        if (frontBitmap != null) {
            val bmp = frontBitmap!!
            val w = width.toFloat()
            val h = height.toFloat()
            val bmpRatio = bmp.width.toFloat() / bmp.height.toFloat()
            val viewRatio = w / h
            val src = if (bmpRatio > viewRatio) {
                val newW = (bmp.height * viewRatio).toInt()
                val startX = (bmp.width - newW) / 2
                Rect(startX, 0, startX + newW, bmp.height)
            } else {
                val newH = (bmp.width / viewRatio).toInt()
                val startY = (bmp.height - newH) / 2
                Rect(0, startY, bmp.width, startY + newH)
            }
            val dst = Rect(0, 0, w.toInt(), h.toInt())
            canvas.drawBitmap(bmp, src, dst, bitmapPaint)
        } else {
            // Default: Solid Deep Onyx Black Card matching the user's reference image
            canvas.drawRect(rect, cardFillPaint)
        }

        // Clock, Date, Camera Icon drawn positioned on the card!
        clockView.drawOnCard(canvas, rect)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        infoBar.stopMonitoring()
        idleAnimator?.cancel()
        waveAnimator?.cancel()
        soundManager.release()
    }
}
