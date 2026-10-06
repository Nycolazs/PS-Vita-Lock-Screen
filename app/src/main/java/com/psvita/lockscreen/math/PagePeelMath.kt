package com.psvita.lockscreen.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PeelPoint(val x: Float, val y: Float)

data class PeelRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

/**
 * Geometric calculations for the PS Vita page-peel / film curl animation.
 * Simulates peeling the top-right corner of a card or sheet (cardRect.right, cardRect.top)
 * towards the drag point (touchX, touchY).
 */
object PagePeelMath {

    data class FoldLine(
        val p1: PeelPoint,
        val p2: PeelPoint,
        val normal: PeelPoint, // Unit vector pointing towards the peeled corner
        val midpoint: PeelPoint,
        val angleRad: Float
    )

    data class PeelState(
        val corner: PeelPoint = PeelPoint(0f, 0f),
        val touch: PeelPoint = PeelPoint(0f, 0f),
        val dragDistance: Float = 0f,
        val foldLine: FoldLine? = null,
        val isThresholdMet: Boolean = false,
        val curlRadius: Float = 60f
    )

    /**
     * High-performance, zero-allocation peel calculation engine for 120fps/60fps rendering.
     * Reuses internal primitive fields to eliminate garbage collection pressure during animations.
     */
    class FastPeelEngine {
        var hasFoldLine: Boolean = false
        var p1X: Float = 0f
        var p1Y: Float = 0f
        var p2X: Float = 0f
        var p2Y: Float = 0f
        var normalX: Float = 0f
        var normalY: Float = 0f
        var midX: Float = 0f
        var midY: Float = 0f
        var dragDistance: Float = 0f
        var isThresholdMet: Boolean = false

        val reflectionMatrixValues = FloatArray(9)

        fun calculate(
            left: Float, top: Float, right: Float, bottom: Float,
            touchX: Float, touchY: Float
        ) {
            val cornerX = right
            val cornerY = top
            val width = right - left
            val height = bottom - top

            val clampedX = touchX.coerceIn(left - width * 0.5f, right)
            val clampedY = touchY.coerceIn(top, bottom + height * 0.5f)

            val vx = clampedX - cornerX
            val vy = clampedY - cornerY
            val dist = sqrt(vx * vx + vy * vy)
            dragDistance = dist

            if (dist < 1f) {
                hasFoldLine = false
                isThresholdMet = false
                return
            }

            val nx = vx / dist
            val ny = vy / dist
            normalX = nx
            normalY = ny

            val mx = (cornerX + clampedX) * 0.5f
            val my = (cornerY + clampedY) * 0.5f
            midX = mx
            midY = my

            val k = nx * mx + ny * my

            var count = 0

            fun addPoint(px: Float, py: Float) {
                if (count == 0) {
                    p1X = px
                    p1Y = py
                    count = 1
                } else if (count == 1) {
                    val dpx = px - p1X
                    val dpy = py - p1Y
                    if (dpx * dpx + dpy * dpy >= 4f) {
                        p2X = px
                        p2Y = py
                        count = 2
                    }
                }
            }

            // 1. Top border: y = top => nx * x = k - ny * top
            if (nx != 0f) {
                val xTop = (k - ny * top) / nx
                if (xTop in left..right) {
                    addPoint(xTop, top)
                }
            }

            // 2. Right border: x = right => ny * y = k - nx * right
            if (ny != 0f) {
                val yRight = (k - nx * right) / ny
                if (yRight in top..bottom) {
                    addPoint(right, yRight)
                }
            }

            // 3. Bottom border: y = bottom => nx * x = k - ny * bottom
            if (nx != 0f && count < 2) {
                val xBottom = (k - ny * bottom) / nx
                if (xBottom in left..right) {
                    addPoint(xBottom, bottom)
                }
            }

            // 4. Left border: x = left => ny * y = k - nx * left
            if (ny != 0f && count < 2) {
                val yLeft = (k - nx * left) / ny
                if (yLeft in top..bottom) {
                    addPoint(left, yLeft)
                }
            }

            hasFoldLine = (count >= 2)

            val cardDiagonal = sqrt(width * width + height * height)
            isThresholdMet = dist > (cardDiagonal * 0.42f) || clampedX < (left + width * 0.40f)
        }

        fun updateReflectionMatrix() {
            val k = normalX * midX + normalY * midY
            val nx2 = normalX * normalX
            val ny2 = normalY * normalY
            val nxny2 = 2f * normalX * normalY

            reflectionMatrixValues[0] = 1f - 2f * nx2
            reflectionMatrixValues[1] = -nxny2
            reflectionMatrixValues[2] = 2f * normalX * k

            reflectionMatrixValues[3] = -nxny2
            reflectionMatrixValues[4] = 1f - 2f * ny2
            reflectionMatrixValues[5] = 2f * normalY * k

            reflectionMatrixValues[6] = 0f
            reflectionMatrixValues[7] = 0f
            reflectionMatrixValues[8] = 1f
        }
    }

    /**
     * Calculates the peel for a given bounding rectangle (cardRect)
     * from its top-right corner (cardRect.right, cardRect.top).
     */
    fun calculateCardPeel(
        rect: PeelRect,
        touchX: Float,
        touchY: Float,
        defaultRadius: Float = 60f
    ): PeelState {
        val corner = PeelPoint(rect.right, rect.top)
        val clampedX = touchX.coerceIn(rect.left - rect.width() * 0.5f, rect.right)
        val clampedY = touchY.coerceIn(rect.top, rect.bottom + rect.height() * 0.5f)
        val touch = PeelPoint(clampedX, clampedY)

        val vx = touch.x - corner.x
        val vy = touch.y - corner.y
        val dist = sqrt(vx * vx + vy * vy)

        if (dist < 1f) {
            return PeelState(
                corner = corner,
                touch = touch,
                dragDistance = 0f,
                foldLine = null,
                isThresholdMet = false,
                curlRadius = defaultRadius
            )
        }

        val nx = vx / dist
        val ny = vy / dist

        val mx = (corner.x + touch.x) / 2f
        val my = (corner.y + touch.y) / 2f
        val midpoint = PeelPoint(mx, my)

        // Line equation: nx * x + ny * y = K
        val k = nx * mx + ny * my
        val intersections = mutableListOf<PeelPoint>()

        fun addIntersection(p: PeelPoint) {
            if (intersections.none { (it.x - p.x) * (it.x - p.x) + (it.y - p.y) * (it.y - p.y) < 4f }) {
                intersections.add(p)
            }
        }

        // 1. Top border: y = rect.top => nx * x = k - ny * rect.top
        if (nx != 0f) {
            val xTop = (k - ny * rect.top) / nx
            if (xTop in rect.left..rect.right) {
                addIntersection(PeelPoint(xTop, rect.top))
            }
        }

        // 2. Right border: x = rect.right => ny * y = k - nx * rect.right
        if (ny != 0f) {
            val yRight = (k - nx * rect.right) / ny
            if (yRight in rect.top..rect.bottom) {
                addIntersection(PeelPoint(rect.right, yRight))
            }
        }

        // 3. Bottom border: y = rect.bottom => nx * x = k - ny * rect.bottom
        if (nx != 0f) {
            val xBottom = (k - ny * rect.bottom) / nx
            if (xBottom in rect.left..rect.right) {
                addIntersection(PeelPoint(xBottom, rect.bottom))
            }
        }

        // 4. Left border: x = rect.left => ny * y = k - nx * rect.left
        if (ny != 0f) {
            val yLeft = (k - nx * rect.left) / ny
            if (yLeft in rect.top..rect.bottom) {
                addIntersection(PeelPoint(rect.left, yLeft))
            }
        }

        val foldLine = if (intersections.size >= 2) {
            val p1 = intersections[0]
            val p2 = intersections[1]
            val angle = atan2(p2.y - p1.y, p2.x - p1.x)
            FoldLine(
                p1 = p1,
                p2 = p2,
                normal = PeelPoint(nx, ny),
                midpoint = midpoint,
                angleRad = angle
            )
        } else {
            null
        }

        val cardDiagonal = sqrt(rect.width() * rect.width() + rect.height() * rect.height())
        val thresholdMet = dist > (cardDiagonal * 0.38f) || touch.x < (rect.left + rect.width() * 0.45f)

        return PeelState(
            corner = corner,
            touch = touch,
            dragDistance = dist,
            foldLine = foldLine,
            isThresholdMet = thresholdMet,
            curlRadius = defaultRadius
        )
    }

    /**
     * Backward-compatible calculatePeel for fullscreen rectangle (0,0,width,height)
     */
    fun calculatePeel(
        width: Float,
        height: Float,
        touchX: Float,
        touchY: Float,
        defaultRadius: Float = 60f
    ): PeelState {
        return calculateCardPeel(PeelRect(0f, 0f, width, height), touchX, touchY, defaultRadius)
    }

    /**
     * Resting idle dog-ear position for a card corner, proportional to card size.
     */
    fun calculateCardIdleCurl(
        rect: PeelRect,
        animationOffset: Float = 0f
    ): PeelPoint {
        val baseCurl = rect.width() * 0.082f + animationOffset
        return PeelPoint(rect.right - baseCurl * 1.05f, rect.top + baseCurl * 0.95f)
    }

    fun calculateIdleCurl(
        width: Float,
        height: Float,
        animationOffset: Float = 0f
    ): PeelPoint {
        return calculateCardIdleCurl(PeelRect(0f, 0f, width, height), animationOffset)
    }

    fun distanceToFold(point: PeelPoint, foldLine: FoldLine): Float {
        val dx = point.x - foldLine.midpoint.x
        val dy = point.y - foldLine.midpoint.y
        return dx * foldLine.normal.x + dy * foldLine.normal.y
    }

    fun reflectPoint(point: PeelPoint, foldLine: FoldLine): PeelPoint {
        val dist = distanceToFold(point, foldLine)
        return PeelPoint(
            point.x - 2f * dist * foldLine.normal.x,
            point.y - 2f * dist * foldLine.normal.y
        )
    }

    /**
     * Calculates the 3x3 affine transformation matrix for 2D reflection across the fold line.
     * Compatible with android.graphics.Matrix.setValues(floatArray).
     */
    fun calculateReflectionMatrix(foldLine: FoldLine): FloatArray {
        val mx = foldLine.midpoint.x
        val my = foldLine.midpoint.y
        val nx = foldLine.normal.x
        val ny = foldLine.normal.y
        val k = nx * mx + ny * my

        return floatArrayOf(
            1f - 2f * nx * nx, -2f * nx * ny,       2f * nx * k,
            -2f * nx * ny,     1f - 2f * ny * ny,   2f * ny * k,
            0f,                0f,                  1f
        )
    }
}
