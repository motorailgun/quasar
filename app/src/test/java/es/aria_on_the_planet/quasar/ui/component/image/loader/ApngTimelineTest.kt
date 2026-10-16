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

    @Test
    fun nextDrawTimeWithoutLimitIsFrameChange() {
        assertEquals(1_234L, nextDrawTime(1_234, maxFps = null))
        assertEquals(1_234L, nextDrawTime(1_234, maxFps = 0))
    }

    @Test
    fun nextDrawTimeIsRoundedUpToCommonTicks() {
        // 30fps の刻み: 0, 34, 67, 100, 134, ... (k * 1000 / 30 の切り上げ)
        assertEquals(34L, nextDrawTime(1, maxFps = 30))
        assertEquals(34L, nextDrawTime(34, maxFps = 30))
        assertEquals(67L, nextDrawTime(35, maxFps = 30))
        assertEquals(100L, nextDrawTime(100, maxFps = 30))
        // 刻みは時刻 0 起点なので、別々の画像でも同じ時刻にそろう
        assertEquals(nextDrawTime(1_000_005, 30), nextDrawTime(1_000_020, 30))
    }

    @Test
    fun ticksNeverExceedMaxFps() {
        // 毎回直後の時刻を求め直しても、1 秒あたり maxFps 回を超えない
        for (fps in listOf(1, 24, 30, 60)) {
            var t = 0L
            var draws = 0
            while (t < 10_000) {
                t = nextDrawTime(t + 1, fps)
                draws++
            }
            assertTrue("fps=$fps draws=$draws", draws <= fps * 10 + 1)
        }
    }
}
