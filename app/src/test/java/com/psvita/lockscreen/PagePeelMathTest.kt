package com.psvita.lockscreen

import com.psvita.lockscreen.math.PagePeelMath
import com.psvita.lockscreen.math.PeelPoint
import com.psvita.lockscreen.math.PeelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PagePeelMathTest {

    private val width = 2400f
    private val height = 1080f

    @Test
    fun testRestingCorner_isNearTopRight() {
        val idlePoint = PagePeelMath.calculateIdleCurl(width, height, 0f)
        assertTrue("Idle curl X should be near right edge", idlePoint.x < width && idlePoint.x > width - 300f)
        assertTrue("Idle curl Y should be near top edge", idlePoint.y > 0f && idlePoint.y < 300f)
    }

    @Test
    fun testReflectionMatrix_matchesReflectedCorner() {
        val rect = PeelRect(100f, 100f, 2300f, 1000f)
        val state = PagePeelMath.calculateCardPeel(rect, 1900f, 500f)
        assertNotNull(state.foldLine)
        val matrix = PagePeelMath.calculateReflectionMatrix(state.foldLine!!)

        // Apply matrix to corner (2300, 100)
        val cx = rect.right
        val cy = rect.top
        val rx = matrix[0] * cx + matrix[1] * cy + matrix[2]
        val ry = matrix[3] * cx + matrix[4] * cy + matrix[5]

        assertEquals(1900f, rx, 0.5f)
        assertEquals(500f, ry, 0.5f)
    }

    @Test
    fun testZeroDrag_returnsZeroDistance() {
        val state = PagePeelMath.calculatePeel(width, height, width, 0f)
        assertEquals(0f, state.dragDistance, 0.001f)
        assertNull(state.foldLine)
        assertFalse(state.isThresholdMet)
    }

    @Test
    fun testDiagonalDrag_calculatesValidFoldLineAndReflection() {
        // Drag diagonally down-left from (2400, 0) to (2000, 400)
        val touchX = 2000f
        val touchY = 400f
        val state = PagePeelMath.calculatePeel(width, height, touchX, touchY)

        val expectedDist = sqrt((400f * 400f) + (400f * 400f))
        assertEquals(expectedDist, state.dragDistance, 0.1f)
        assertNotNull(state.foldLine)

        val foldLine = state.foldLine!!
        // Midpoint should be halfway between (2400, 0) and (2000, 400) => (2200, 200)
        assertEquals(2200f, foldLine.midpoint.x, 0.1f)
        assertEquals(200f, foldLine.midpoint.y, 0.1f)

        // Corner (2400, 0) reflected across the fold line must match the touch point (2000, 400)!
        val corner = PeelPoint(width, 0f)
        val reflectedCorner = PagePeelMath.reflectPoint(corner, foldLine)
        assertEquals(touchX, reflectedCorner.x, 0.5f)
        assertEquals(touchY, reflectedCorner.y, 0.5f)
    }

    @Test
    fun testThresholdDetection() {
        // Small drag (100px) => threshold not met
        val smallPeel = PagePeelMath.calculatePeel(width, height, width - 100f, 100f)
        assertFalse(smallPeel.isThresholdMet)

        // Large drag past 50% screen width => threshold met
        val largePeel = PagePeelMath.calculatePeel(width, height, width * 0.3f, height * 0.7f)
        assertTrue(largePeel.isThresholdMet)
    }

    @Test
    fun testCardPeel_reflectsCornerOnTouch() {
        val rect = PeelRect(100f, 100f, 2300f, 1000f)
        // Drag from card's top right (2300, 100) to (1900, 500)
        val state = PagePeelMath.calculateCardPeel(rect, 1900f, 500f)
        assertNotNull(state.foldLine)
        val cardCorner = PeelPoint(rect.right, rect.top)
        val reflected = PagePeelMath.reflectPoint(cardCorner, state.foldLine!!)
        assertEquals(1900f, reflected.x, 0.5f)
        assertEquals(500f, reflected.y, 0.5f)
    }
}
