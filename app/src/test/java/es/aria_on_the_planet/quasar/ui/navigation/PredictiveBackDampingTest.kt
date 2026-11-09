package es.aria_on_the_planet.quasar.ui.navigation

import es.aria_on_the_planet.quasar.domain.model.settings.PredictiveBackSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveBackDampingTest {
    @Test
    fun dampedProgressStartsAtZeroAndEndsAtMax() {
        listOf(0.1f, 25f, 100f).forEach { steepness ->
            val settings = PredictiveBackSettings(maxDragFraction = 0.25f, dragLogSteepness = steepness)
            assertEquals(0f, settings.dampedProgress(0f), 1e-6f)
            assertEquals(0.25f, settings.dampedProgress(1f), 1e-6f)
        }
    }

    @Test
    fun dampedProgressIsIncreasingAndAheadOfLinear() {
        val settings = PredictiveBackSettings(maxDragFraction = 1f, dragLogSteepness = 25f)
        val values = (0..20).map { settings.dampedProgress(it / 20f) }
        assertTrue(values.zipWithNext().all { (a, b) -> a < b })
        // log なので途中は直線 (p そのもの) より前に出る
        (1 until 20).forEach { assertTrue(values[it] > it / 20f) }
    }

    @Test
    fun steeperCurveMovesMoreAtTheStart() {
        val gentle = PredictiveBackSettings(dragLogSteepness = 3f).dampedProgress(0.1f)
        val steep = PredictiveBackSettings(dragLogSteepness = 30f).dampedProgress(0.1f)
        assertTrue(steep > gentle)
    }

    private val settings = PredictiveBackSettings(maxDragFraction = 0.2f, dragLogSteepness = 30f)

    @Test
    fun trackerFollowsLogWhileAdvancing() {
        val tracker = BackDragTracker(settings)
        tracker.offset(0f, 10f)
        assertEquals(settings.dampedProgress(0.3f), tracker.offset(0.3f, 310f), 1e-6f)
    }

    @Test
    fun trackerRetreatsInProportionToFingerDistance() {
        val tracker = BackDragTracker(settings)
        tracker.offset(0f, 0f)
        val peak = tracker.offset(0.3f, 300f)
        // 進み具合が急に落ちても、指を 10% 戻しただけならずれも 10% だけ戻る
        assertEquals(peak * 0.9f, tracker.offset(0.02f, 270f), 1e-6f)
        assertEquals(0f, tracker.offset(0f, 0f), 1e-6f)
    }

    @Test
    fun trackerReturnsToLogPastThePeak() {
        val tracker = BackDragTracker(settings)
        tracker.offset(0f, 0f)
        val peak = tracker.offset(0.3f, 300f)
        assertEquals(peak / 2, tracker.offset(0.15f, 150f), 1e-6f)
        assertEquals(peak, tracker.offset(0.3f, 300f), 1e-6f)
        assertEquals(settings.dampedProgress(0.5f), tracker.offset(0.5f, 500f), 1e-6f)
    }

    @Test
    fun trackerUsesProgressWithoutTouchPosition() {
        val tracker = BackDragTracker(settings)
        val peak = tracker.offset(0.4f, 0f)
        assertEquals(peak / 2, tracker.offset(0.2f, 0f), 1e-6f)
    }
}
