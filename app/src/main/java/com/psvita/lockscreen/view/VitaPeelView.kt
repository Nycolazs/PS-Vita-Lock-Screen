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
import android.view.animation.DecelerateInterpolator
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

    var isPreviewMode: Boolean = false
    var onUnlockListener: OnUnlockListener? = null

    private val prefs = LockPreferences(context)
    private val soundManager = SoundManager(context)

    private val density get() = resources.displayMetrics.density

    // Cutout insets
    var cutoutLeft: Float = 0f
        set(value) {
            field = value
            infoBar.setCutoutInsets(cutoutLeft, cutoutRight)
            if (width > 0 && height > 0) updateCardGeometry(width, height)
        }

    var cutoutRight: Float = 0f
        set(value) {
            field = value
            infoBar.setCutoutInsets(cutoutLeft, cutoutRight)
            if (width > 0 && height > 0) updateCardGeometry(width, height)
        }

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
    private var isUnlocking = false
    private var isUnlocked = false
    private var unlockProgress = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var peelAnimator: ValueAnimator? = null

    // Idle flutter animation
    private var idleAnimator: ValueAnimator? = null
    private var idleFlutterOffset = 0f

    // Live wave background animation
    private var waveAnimator: ValueAnimator? = null
    private var waveTime = 0f

    // Card geometry (Rounded rectangle sheet)
    private val cardRect = RectF()
    private val cardCornerRadius get() = 18f * density
    private val shadowWidth get() = 24f * density
    private val rollRadius get() = 14f * density

    // Bitmaps for Front and Back photos
    private var frontBitmap: Bitmap? = null
    private var backBitmap: Bitmap? = null
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Paints
    private val consoleFramePaint = Paint().apply { color = Color.BLACK }
    private val cardCutoutShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000") // Subtle groove shadow under cutout
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
    }
    private val cardCutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFFFFF") // Authentic clean gray/white cutout line
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
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

    // Fast zero-allocation peel calculation engine
    private val peelEngine = PagePeelMath.FastPeelEngine()

    // Pre-allocated paths for 120fps/60fps rendering (reused across all frames)
    private val cardPath = Path()
    private val cornerSidePolyPath = Path()
    private val peeledRegionPath = Path()
    private val shadowPath = Path()
    private val foldedFlapPath = Path()
    private val rollPath = Path()
    private val wavePath1 = Path()
    private val wavePath2 = Path()

    // Pre-allocated matrices and arrays
    private val reflectionMatrix = Matrix()
    private val shaderMatrix = Matrix()
    private val shaderMatrixValues = FloatArray(9)

    // Pre-allocated unit shaders for instant GPU matrix transformation
    private val unitShadowShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(Color.TRANSPARENT, Color.parseColor("#B0000000")),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP
    )

    private val unitPeelBackShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(
            Color.parseColor("#646A74"),
            Color.parseColor("#444951"),
            Color.parseColor("#2A2D33"),
            Color.parseColor("#1B1D21")
        ),
        floatArrayOf(0f, 0.25f, 0.65f, 1f),
        Shader.TileMode.CLAMP
    )

    private val unitRollHighlightShader = LinearGradient(
        0f, 0f, 1f, 0f,
        intArrayOf(
            Color.parseColor("#45000000"),
            Color.parseColor("#A0FFFFFF"),
            Color.parseColor("#25FFFFFF"),
            Color.TRANSPARENT
        ),
        floatArrayOf(0f, 0.35f, 0.7f, 1f),
        Shader.TileMode.CLAMP
    )

    private var bgGradShader: LinearGradient? = null
    private var lastBgGradHeight = 0f
    private var lastBgGradPreset = ""
    private var cachedTopColor = Color.parseColor("#081636")
    private var cachedBottomColor = Color.parseColor("#0046A0")
    private var cachedWaveR = 0
    private var cachedWaveG = 160
    private var cachedWaveB = 255
    private val bgGradPaint = Paint()

    private fun setShaderGradient(shader: Shader, x0: Float, y0: Float, x1: Float, y1: Float) {
        val dx = x1 - x0
        val dy = y1 - y0
        val safeDx = if (dx == 0f && dy == 0f) 0.001f else dx
        shaderMatrixValues[0] = safeDx
        shaderMatrixValues[1] = -dy
        shaderMatrixValues[2] = x0
        shaderMatrixValues[3] = dy
        shaderMatrixValues[4] = safeDx
        shaderMatrixValues[5] = y0
        shaderMatrixValues[6] = 0f
        shaderMatrixValues[7] = 0f
        shaderMatrixValues[8] = 1f
        shaderMatrix.setValues(shaderMatrixValues)
        shader.setLocalMatrix(shaderMatrix)
    }

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
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                setRequestedFrameRate(120f)
            } catch (_: Throwable) {}
        }
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

    fun resetPeel(force: Boolean = false) {
        if (!force && (isDragging || isUnlocking || isUnlocked)) return
        peelAnimator?.cancel()
        peelAnimator = null
        idleAnimator?.cancel()
        isDragging = false
        isAnimating = false
        isUnlocking = false
        isUnlocked = false
        unlockProgress = 0f
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

    private fun initParticles(w: Float = 2400f, h: Float = 1080f) {
        particles.clear()
        val rnd = Random(42)
        val maxW = if (w > 0f) w else 2400f
        val maxH = if (h > 0f) h else 1080f
        for (i in 0..26) {
            particles.add(
                Particle(
                    x = rnd.nextFloat() * maxW,
                    y = rnd.nextFloat() * maxH,
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
                if (!isDragging && !isAnimating && !isUnlocked && !isUnlocking) {
                    val progress = it.animatedValue as Float
                    idleFlutterOffset = sin(progress * Math.PI.toFloat()) * (10f * density)
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
        val topBarH = (38 * density).toInt()
        infoBar.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(topBarH, MeasureSpec.EXACTLY)
        )
        infoBar.layout(0, 0, w, topBarH)

        val sizePct = prefs.cardSizePercent.coerceIn(70, 100)
        val availW = (w - cutoutLeft - cutoutRight).coerceAtLeast(100f)
        val availH = (h - topBarH).toFloat().coerceAtLeast(100f)

        if (sizePct >= 100) {
            cardRect.set(cutoutLeft, topBarH.toFloat(), w - cutoutRight, h.toFloat())
        } else {
            val factor = (sizePct - 70f) / 30f // 0.0 to 1.0

            if (w >= h) {
                // Landscape: Stretched comfortably to the sides like the authentic PS Vita framing
                val minW = availW * 0.78f
                val maxW = availW * 0.96f
                val cardW = minW + (maxW - minW) * factor

                val minH = availH * 0.74f
                val maxH = availH * 0.95f
                val cardH = minH + (maxH - minH) * factor

                val centerX = cutoutLeft + availW * 0.5f
                val centerY = topBarH + availH * 0.5f
                cardRect.set(
                    centerX - cardW * 0.5f,
                    centerY - cardH * 0.5f,
                    centerX + cardW * 0.5f,
                    centerY + cardH * 0.5f
                )
            } else {
                // Portrait mode: Responsive card fitting width with comfortable margins
                val cardW = availW * (0.80f + 0.17f * factor)
                val cardH = (cardW / 1.35f).coerceAtMost(availH * 0.85f)
                val centerX = cutoutLeft + availW * 0.5f
                val centerY = topBarH + availH * 0.45f
                cardRect.set(
                    centerX - cardW * 0.5f,
                    centerY - cardH * 0.5f,
                    centerX + cardW * 0.5f,
                    centerY + cardH * 0.5f
                )
            }
        }
        val cornerRadius = if (prefs.cardSizePercent >= 100) 0f else cardCornerRadius
        cardPath.reset()
        cardPath.addRoundRect(cardRect, cornerRadius, cornerRadius, Path.Direction.CW)
        infoBar.setCardBounds(cardRect.left, cardRect.right)

        if (!isDragging && !isAnimating && !isUnlocked && !isUnlocking) {
            val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, idleFlutterOffset)
            currentTouchX = idle.x
            currentTouchY = idle.y
        }
    }

    override fun onApplyWindowInsets(insets: android.view.WindowInsets): android.view.WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = insets.displayCutout
            if (cutout != null) {
                cutoutLeft = cutout.safeInsetLeft.toFloat()
                cutoutRight = cutout.safeInsetRight.toFloat()
            }
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initParticles(w.toFloat(), h.toFloat())
            updateCardGeometry(w, h)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return true

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Must initiate touch precisely in the top-right dog-ear region of the card
                val cornerHitW = maxOf(72f * density, cardRect.width() * 0.22f)
                val cornerHitH = maxOf(72f * density, cardRect.height() * 0.25f)
                val isCornerHit = x >= (cardRect.right - cornerHitW) &&
                                  x <= (cardRect.right + 32f * density) &&
                                  y >= (cardRect.top - 32f * density) &&
                                  y <= (cardRect.top + cornerHitH)

                if (isCornerHit) {
                    isDragging = true
                    touchDownX = x
                    touchDownY = y
                    currentTouchX = x
                    currentTouchY = y
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)

                    soundManager.playPeelSound()
                    vibrateTouch()
                    invalidate()
                    return true
                }
                return false
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

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    val dragDistance = hypot(x - touchDownX, y - touchDownY)
                    val minUnlockDrag = maxOf(64f * density, cardRect.width() * 0.25f)
                    val draggedLeft = touchDownX - x
                    val draggedDown = y - touchDownY
                    val isDraggedTowardUnlock = draggedLeft > (24f * density) && draggedDown > (24f * density)

                    peelEngine.calculate(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom, currentTouchX, currentTouchY)

                    val isFlingUnlock = isDraggedTowardUnlock && (vx < -800f || (vx < -450f && vy > 450f))
                    val isThresholdUnlock = dragDistance >= minUnlockDrag && isDraggedTowardUnlock &&
                            (peelEngine.isThresholdMet || currentTouchX < (cardRect.left + cardRect.width() * 0.42f))

                    if (isThresholdUnlock || isFlingUnlock) {
                        triggerUnlockAnimation(vx, vy)
                    } else {
                        triggerSpringBackAnimation(vx, vy)
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    triggerSpringBackAnimation()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Peels the card off automatically, as if the corner had been dragged.
     * Used for unlocking with a gamepad button.
     */
    fun peelAndUnlock() {
        if (isDragging || isUnlocking || isUnlocked) return
        soundManager.playPeelSound()
        triggerUnlockAnimation()
    }

    private fun triggerUnlockAnimation(flingVx: Float = 0f, flingVy: Float = 0f) {
        peelAnimator?.cancel()
        idleAnimator?.cancel()
        isAnimating = true
        isUnlocking = true
        unlockProgress = 0f
        soundManager.playUnlockSound()
        vibrateUnlock()

        val startX = currentTouchX
        val startY = currentTouchY
        // Generously clear the bottom-left corner and borders so the entire sheet,
        // the drop shadow, and the reflection flap completely glide off-screen.
        val targetX = cardRect.left - cardRect.width() * 1.8f
        val targetY = cardRect.bottom + cardRect.height() * 1.8f

        val dist = hypot(targetX - startX, targetY - startY)
        val cardDiag = hypot(cardRect.width(), cardRect.height())
        val progressRemaining = (dist / (cardDiag * 2.0f)).coerceIn(0.25f, 1f)

        val flingSpeed = hypot(flingVx, flingVy)
        val animDuration: Long
        val animInterpolator: android.view.animation.Interpolator

        if (flingSpeed > 800f) {
            val speedFactor = (800f / flingSpeed.coerceAtMost(3200f)).coerceIn(0.55f, 1f)
            animDuration = ((360L * speedFactor) * progressRemaining).toLong().coerceIn(280L, 380L)
            animInterpolator = DecelerateInterpolator(1.3f)
        } else {
            animDuration = (420L * progressRemaining).toLong().coerceIn(340L, 460L)
            animInterpolator = DecelerateInterpolator(1.5f)
        }

        peelAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            interpolator = animInterpolator
            addUpdateListener {
                val f = it.animatedValue as Float
                unlockProgress = f
                currentTouchX = startX + (targetX - startX) * f
                currentTouchY = startY + (targetY - startY) * f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    peelAnimator = null
                    isAnimating = false
                    isUnlocking = false
                    isUnlocked = true
                    unlockProgress = 1f
                    currentTouchX = targetX
                    currentTouchY = targetY
                    invalidate()
                    if (isPreviewMode) {
                        postDelayed({
                            if (isPreviewMode) {
                                triggerSpringBackAnimation()
                            }
                        }, 600)
                    } else {
                        onUnlockListener?.onUnlock()
                    }
                }
            })
        }
        peelAnimator?.start()
    }

    private fun triggerSpringBackAnimation(flingVx: Float = 0f, flingVy: Float = 0f) {
        peelAnimator?.cancel()
        isAnimating = true
        isUnlocking = false
        isUnlocked = false
        unlockProgress = 0f
        val startX = currentTouchX
        val startY = currentTouchY
        val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
        val idle = PagePeelMath.calculateCardIdleCurl(peelRect, 0f)

        val dist = hypot(idle.x - startX, idle.y - startY)
        val cardDiag = hypot(cardRect.width(), cardRect.height())
        val animDuration = ((dist / cardDiag) * 320f).toLong().coerceIn(180L, 280L)

        peelAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animDuration
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener {
                val f = it.animatedValue as Float
                currentTouchX = startX + (idle.x - startX) * f
                currentTouchY = startY + (idle.y - startY) * f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    peelAnimator = null
                    isAnimating = false
                    isUnlocked = false
                    isUnlocking = false
                    currentTouchX = idle.x
                    currentTouchY = idle.y
                    startIdleAnimation()
                    invalidate()
                }
            })
        }
        peelAnimator?.start()
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

        if (isUnlocked) {
            // When unlocked, card sheet is completely gone
            if (isPreviewMode) {
                drawUnderlyingLayer(canvas, 0f, 0f, w, h)
            }
            return
        }

        val cornerRadius = if (prefs.cardSizePercent >= 100) 0f else cardCornerRadius

        // Determine current touch for card peel
        val touchX: Float
        val touchY: Float
        if (!isDragging && !isAnimating && !isUnlocking) {
            val peelRect = PeelRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            val idle = PagePeelMath.calculateCardIdleCurl(peelRect, idleFlutterOffset)
            touchX = idle.x
            touchY = idle.y
        } else {
            touchX = currentTouchX
            touchY = currentTouchY
        }

        peelEngine.calculate(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom, touchX, touchY)

        if (!peelEngine.hasFoldLine) {
            if (isUnlocking || (peelEngine.isThresholdMet && (touchX < cardRect.left || touchY > cardRect.bottom))) {
                if (isPreviewMode) {
                    drawUnderlyingLayer(canvas, 0f, 0f, w, h)
                }
                return
            }

            // Normal closed card with resting idle curl
            drawBaseWallpaper(canvas, w, h)
            clockView.drawOnCard(canvas, cardRect)

            if (cornerRadius > 0f) {
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutShadowPaint)
                canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutPaint)
            }
            infoBar.draw(canvas)
            return
        }

        // --- Active Peel ---
        if (isPreviewMode) {
            // 1. Draw base wallpaper behind the unpeeled card
            drawBaseWallpaper(canvas, w, h)

            // 2. Draw REVEALED UNDERNEATH LAYER inside cardRect
            canvas.save()
            canvas.clipPath(cardPath)
            drawUnderlyingLayer(canvas, cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            canvas.restore()
        } else {
            // Live lockscreen: Base wallpaper outside the card dissolves smoothly on unlock
            val outerAlpha = if (isUnlocking) (1f - unlockProgress).coerceIn(0f, 1f) else 1f
            if (outerAlpha > 0f) {
                val sc = if (outerAlpha < 1f) {
                    canvas.saveLayerAlpha(0f, 0f, w, h, (outerAlpha * 255).toInt())
                } else {
                    canvas.save()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    canvas.clipOutPath(cardPath)
                }
                drawBaseWallpaper(canvas, w, h)
                canvas.restoreToCount(sc)
            }
        }

        // 3. Card Peeling Geometry
        val p1X = peelEngine.p1X
        val p1Y = peelEngine.p1Y
        val p2X = peelEngine.p2X
        val p2Y = peelEngine.p2Y
        val dx = p2X - p1X
        val dy = p2Y - p1Y
        val len = hypot(dx, dy)
        val ux = if (len > 0f) dx / len else 1f
        val uy = if (len > 0f) dy / len else 0f

        val span = 5000f
        val p1ExtX = p1X - ux * span
        val p1ExtY = p1Y - uy * span
        val p2ExtX = p2X + ux * span
        val p2ExtY = p2Y + uy * span

        val nx = peelEngine.normalX
        val ny = peelEngine.normalY

        // Half-plane containing the peeled corner
        cornerSidePolyPath.reset()
        cornerSidePolyPath.moveTo(p1ExtX, p1ExtY)
        cornerSidePolyPath.lineTo(p2ExtX, p2ExtY)
        cornerSidePolyPath.lineTo(p2ExtX - nx * span, p2ExtY - ny * span)
        cornerSidePolyPath.lineTo(p1ExtX - nx * span, p1ExtY - ny * span)
        cornerSidePolyPath.close()

        // Draw Front Card on the Unpeeled Region using hardware-accelerated clipOutPath
        canvas.save()
        canvas.clipPath(cardPath)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(cornerSidePolyPath)
        } else {
            @Suppress("DEPRECATION")
            canvas.clipPath(cornerSidePolyPath, Region.Op.DIFFERENCE)
        }
        drawFrontCardContent(canvas, cardRect)
        canvas.restore()

        // Cutout line around card
        val cutoutAlpha = if (isUnlocking) (1f - unlockProgress).coerceIn(0f, 1f) else 1f
        if (cornerRadius > 0f && cutoutAlpha > 0f) {
            canvas.save()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(cornerSidePolyPath)
            }
            cardCutoutShadowPaint.alpha = ((0x40 * cutoutAlpha).toInt())
            cardCutoutPaint.alpha = ((0x70 * cutoutAlpha).toInt())
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutShadowPaint)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardCutoutPaint)
            canvas.restore()
        }

        // 4. Drop Shadow beneath the folded sheet onto the underlying photo
        val shadowW = shadowWidth
        shadowPath.reset()
        shadowPath.moveTo(p1X, p1Y)
        shadowPath.lineTo(p2X, p2Y)
        shadowPath.lineTo(p2X - nx * shadowW, p2Y - ny * shadowW)
        shadowPath.lineTo(p1X - nx * shadowW, p1Y - ny * shadowW)
        shadowPath.close()

        setShaderGradient(
            unitShadowShader,
            peelEngine.midX - nx * shadowW,
            peelEngine.midY - ny * shadowW,
            peelEngine.midX,
            peelEngine.midY
        )
        val shadowAlpha = if (isUnlocking) (1f - (unlockProgress - 0.5f) / 0.5f).coerceIn(0f, 1f) else 1f
        dropShadowPaint.shader = unitShadowShader
        dropShadowPaint.alpha = (shadowAlpha * 255).toInt()

        canvas.save()
        canvas.clipPath(cardPath)
        canvas.drawPath(shadowPath, dropShadowPaint)
        canvas.restore()

        // 5. Backside of Curled Sheet (Reflected Flap with rounded corner)
        peeledRegionPath.reset()
        peeledRegionPath.op(cardPath, cornerSidePolyPath, Path.Op.INTERSECT)

        peelEngine.updateReflectionMatrix()
        reflectionMatrix.setValues(peelEngine.reflectionMatrixValues)

        foldedFlapPath.reset()
        peeledRegionPath.transform(reflectionMatrix, foldedFlapPath)

        setShaderGradient(
            unitPeelBackShader,
            peelEngine.midX,
            peelEngine.midY,
            touchX,
            touchY
        )
        val flapAlpha = if (isUnlocking) (1f - (unlockProgress - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
        peelBackPaint.shader = unitPeelBackShader
        peelBackPaint.alpha = (flapAlpha * 255).toInt()
        cardBorderPaint.alpha = (0x70 * flapAlpha).toInt()

        canvas.drawPath(foldedFlapPath, peelBackPaint)
        canvas.drawPath(foldedFlapPath, cardBorderPaint)

        // 6. Curled Cylindrical Roll Highlight along the fold crease
        val rollR = rollRadius
        rollPath.reset()
        rollPath.moveTo(p1X, p1Y)
        rollPath.lineTo(p2X, p2Y)
        rollPath.lineTo(p2X + nx * rollR, p2Y + ny * rollR)
        rollPath.lineTo(p1X + nx * rollR, p1Y + ny * rollR)
        rollPath.close()

        setShaderGradient(
            unitRollHighlightShader,
            peelEngine.midX,
            peelEngine.midY,
            peelEngine.midX + nx * rollR,
            peelEngine.midY + ny * rollR
        )
        rollHighlightPaint.shader = unitRollHighlightShader
        rollHighlightPaint.alpha = (shadowAlpha * 255).toInt()

        canvas.drawPath(rollPath, rollHighlightPaint)

        // 7. Top Info Bar
        val infoBarAlpha = if (isUnlocking) {
            (1f - (unlockProgress - 0.2f) / 0.8f).coerceIn(0f, 1f)
        } else {
            1f
        }
        if (infoBarAlpha > 0f) {
            if (infoBarAlpha < 1f) {
                val sc = canvas.saveLayerAlpha(0f, 0f, w, (42f * density), (infoBarAlpha * 255).toInt())
                infoBar.draw(canvas)
                canvas.restoreToCount(sc)
            } else {
                infoBar.draw(canvas)
            }
        }
    }

    private fun updateBgPresetColors() {
        val preset = prefs.backWallpaperPreset
        if (preset == lastBgGradPreset && bgGradShader != null) return
        lastBgGradPreset = preset
        when (preset) {
            "red" -> {
                cachedTopColor = Color.parseColor("#340810")
                cachedBottomColor = Color.parseColor("#800B1D")
                cachedWaveR = 255; cachedWaveG = 45; cachedWaveB = 80
            }
            "green" -> {
                cachedTopColor = Color.parseColor("#062414")
                cachedBottomColor = Color.parseColor("#0B6030")
                cachedWaveR = 30; cachedWaveG = 220; cachedWaveB = 100
            }
            "purple" -> {
                cachedTopColor = Color.parseColor("#220834")
                cachedBottomColor = Color.parseColor("#581285")
                cachedWaveR = 180; cachedWaveG = 50; cachedWaveB = 255
            }
            "amber" -> {
                cachedTopColor = Color.parseColor("#341806")
                cachedBottomColor = Color.parseColor("#80400B")
                cachedWaveR = 255; cachedWaveG = 160; cachedWaveB = 30
            }
            "black" -> {
                cachedTopColor = Color.parseColor("#18191C")
                cachedBottomColor = Color.parseColor("#0A0B0D")
                cachedWaveR = 100; cachedWaveG = 110; cachedWaveB = 125
            }
            else -> {
                cachedTopColor = Color.parseColor("#081636")
                cachedBottomColor = Color.parseColor("#0046A0")
                cachedWaveR = 0; cachedWaveG = 160; cachedWaveB = 255
            }
        }
        bgGradShader = null
    }

    private val underlyingSrcRect = Rect()
    private val underlyingDstRect = Rect()

    /**
     * Draws the Underlying Revealed Layer (Behind the card).
     * By default: classic vibrant PS Vita Blue Waves & crystal particles!
     */
    private fun drawUnderlyingLayer(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        if (backBitmap != null) {
            val bmp = backBitmap!!
            underlyingSrcRect.set(0, 0, bmp.width, bmp.height)
            underlyingDstRect.set(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
            canvas.drawBitmap(bmp, underlyingSrcRect, underlyingDstRect, bitmapPaint)
        } else {
            val h = bottom - top
            updateBgPresetColors()

            if (bgGradShader == null || lastBgGradHeight != h) {
                lastBgGradHeight = h
                bgGradShader = LinearGradient(
                    0f, top, 0f, bottom,
                    cachedTopColor, cachedBottomColor,
                    Shader.TileMode.CLAMP
                )
                bgGradPaint.shader = bgGradShader
            }
            canvas.drawRect(left, top, right, bottom, bgGradPaint)

            val t = waveTime
            val r = cachedWaveR
            val g = cachedWaveG
            val b = cachedWaveB

            wavePaint.color = Color.argb(90, r, g, b)
            wavePath1.reset()
            wavePath1.moveTo(left, bottom)
            val leftI = left.toInt()
            val rightI = right.toInt()
            var curX = leftI
            while (curX <= rightI) {
                val fx = curX.toFloat()
                val y = top + h * 0.55f + sin(fx * 0.0028f + t * 0.9f) * 60f + cos(fx * 0.0016f - t * 0.5f) * 35f
                wavePath1.lineTo(fx, y)
                curX += 28
            }
            wavePath1.lineTo(right, bottom)
            wavePath1.close()
            canvas.drawPath(wavePath1, wavePaint)

            wavePaint.color = Color.argb(125, r, g, b)
            wavePath2.reset()
            wavePath2.moveTo(left, bottom)
            curX = leftI
            while (curX <= rightI) {
                val fx = curX.toFloat()
                val y = top + h * 0.65f + sin(fx * 0.0036f - t * 0.7f + 1.2f) * 80f + cos(fx * 0.0020f + t * 0.6f) * 30f
                wavePath2.lineTo(fx, y)
                curX += 28
            }
            wavePath2.lineTo(right, bottom)
            wavePath2.close()
            canvas.drawPath(wavePath2, wavePaint)
        }

        // Draw ambient floating crystal dust
        if (prefs.isFloatingParticlesEnabled) {
            val count = particles.size
            for (i in 0 until count) {
                val p = particles[i]
                p.y -= p.speedY
                if (p.y < top) {
                    p.y = bottom
                    p.x = left + Random.nextFloat() * (right - left)
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
