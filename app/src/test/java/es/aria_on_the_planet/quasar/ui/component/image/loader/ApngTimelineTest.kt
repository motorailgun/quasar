package es.aria_on_the_planet.quasar.ui.component.image.loader

import es.aria_on_the_planet.quasar.ui.component.image.loader.ApngTimeline.Frame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApngTimelineTest {
    private val timeline = ApngTimeline(intArrayOf(30, 40, 30), loopCount = 0)

    @Test
    fun findsFrameAndTimeUntilNext() {
        assertEquals(Frame(0, 30), timeline.frameAt(0))
        assertEquals(Frame(0, 1), timeline.frameAt(29))
        assertEquals(Frame(1, 40), timeline.frameAt(30))
        assertEquals(Frame(1, 10), timeline.frameAt(60))
        assertEquals(Frame(2, 30), timeline.frameAt(70))
    }

    @Test
    fun loopsForeverWhenLoopCountIsZero() {
        assertEquals(Frame(0, 30), timeline.frameAt(100))
        assertEquals(Frame(1, 25), timeline.frameAt(100L * 1000 + 45))
        assertFalse(timeline.isFinished(Long.MAX_VALUE / 2))
    }

    @Test
    fun stopsOnLastFrameAfterLoopCount() {
        val twice = ApngTimeline(intArrayOf(30, 40, 30), loopCount = 2)
        assertEquals(Frame(2, 1), twice.frameAt(199))
        assertTrue(twice.isFinished(200))
        assertEquals(Frame(2, null), twice.frameAt(200))
        assertEquals(Frame(2, null), twice.frameAt(10_000))
    }

    @Test
    fun tooShortDelaysAreTreatedAsDefault() {
        // 0ms・10ms のコマは 100ms として扱う (描き直し続けないため)
        val fast = ApngTimeline(intArrayOf(0, 10, 11), loopCount = 0)
        assertEquals(Frame(0, 100), fast.frameAt(0))
        assertEquals(Frame(1, 100), fast.frameAt(100))
        assertEquals(Frame(2, 11), fast.frameAt(200))
    }

    @Test
    fun singleFrameIsStatic() {
        assertEquals(Frame(0, null), ApngTimeline(intArrayOf(50), loopCount = 0).frameAt(123))
        assertEquals(Frame(0, null), ApngTimeline(intArrayOf(), loopCount = 0).frameAt(0))
    }

    @Test
    fun negativeElapsedIsClampedToStart() {
        assertEquals(Frame(0, 30), timeline.frameAt(-5))
    }
}
