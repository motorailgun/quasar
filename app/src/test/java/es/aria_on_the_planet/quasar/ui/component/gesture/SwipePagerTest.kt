package es.aria_on_the_planet.quasar.ui.component.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePagerTest {
    private fun target(base: Int, offset: Float, velocity: Float = 0f, count: Int = 4) =
        swipeTargetPage(base, count, offset, velocity, switchDistance = 100f, flingThreshold = 1000f)

    @Test
    fun staysWhenNotFarEnough() {
        assertEquals(1, target(1, 50f))
        assertEquals(1, target(1, -50f))
    }

    @Test
    fun switchesAfterEnoughDistance() {
        // 前のページ側 (正) は番号が小さい側
        assertEquals(0, target(1, 120f))
        assertEquals(2, target(1, -120f))
    }

    @Test
    fun flingSwitchesEvenWithShortDistance() {
        assertEquals(0, target(1, 10f, velocity = 2000f))
        assertEquals(2, target(1, -10f, velocity = -2000f))
        assertEquals(2, target(1, 0f, velocity = -2000f))
    }

    @Test
    fun flingBackCancelsTheSwitch() {
        assertEquals(1, target(1, 120f, velocity = -2000f))
        assertEquals(1, target(1, -120f, velocity = 2000f))
    }

    @Test
    fun staysAtEdges() {
        assertEquals(0, target(0, 300f, velocity = 3000f))
        assertEquals(3, target(3, -300f, velocity = -3000f))
    }

    @Test
    fun inverseUndoesRubberBand() {
        val max = 500f
        for (finger in listOf(-800f, -100f, 0f, 30f, 400f, 1500f)) {
            assertEquals(finger, rubberBandInverse(rubberBand(finger, max), max), 0.5f)
        }
        // 上限以上は手前で打ち切る (無限大にならない)
        assertTrue(rubberBandInverse(max, max).isFinite())
    }

    @Test
    fun slopeMatchesRubberBand() {
        val max = 500f
        for (finger in listOf(0f, 200f, -600f)) {
            val numeric = (rubberBand(finger + 0.5f, max) - rubberBand(finger - 0.5f, max))
            assertEquals(numeric, rubberBandSlope(finger, max), 0.001f)
        }
    }
}
