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
}
