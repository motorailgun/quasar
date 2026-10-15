package es.aria_on_the_planet.quasar.ui.component.player

import androidx.media3.common.C
import es.aria_on_the_planet.quasar.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {
    @Test
    fun formatsPlaybackTime() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:00", formatPlaybackTime(-500))
        assertEquals("0:09", formatPlaybackTime(9_999))
        assertEquals("1:05", formatPlaybackTime(65_000))
        assertEquals("59:59", formatPlaybackTime(3_599_000))
        assertEquals("1:00:00", formatPlaybackTime(3_600_000))
        assertEquals("10:02:03", formatPlaybackTime((10 * 3600 + 2 * 60 + 3) * 1000L))
    }

    @Test
    fun resolvesDurationFromPlayerThenMeta() {
        assertEquals(12_000L, resolveDurationMs(12_000, 21.7))
        assertEquals(21_700L, resolveDurationMs(C.TIME_UNSET, 21.7))
        assertNull(resolveDurationMs(C.TIME_UNSET, null))
        assertNull(resolveDurationMs(C.TIME_UNSET, 0.0))
    }

    @Test
    fun playbackFractionIsClampedAndSafe() {
        assertEquals(0.25f, playbackFraction(15_000, 60_000), 0.001f)
        assertEquals(1f, playbackFraction(70_000, 60_000), 0.001f)
        assertEquals(0f, playbackFraction(5_000, null), 0.001f)
        assertEquals(0f, playbackFraction(5_000, 0), 0.001f)
    }

    @Test
    fun seekZonesAreOuterThirds() {
        assertEquals(SeekZone.Backward, seekZoneOf(10f, 300f))
        assertEquals(SeekZone.None, seekZoneOf(150f, 300f))
        assertEquals(SeekZone.Forward, seekZoneOf(290f, 300f))
        assertEquals(SeekZone.None, seekZoneOf(10f, 0f))
    }

    @Test
    fun playableTypes() {
        assertTrue(MediaType.Video.isPlayable)
        assertTrue(MediaType.Gifv.isPlayable)
        assertTrue(MediaType.Audio.isPlayable)
        assertFalse(MediaType.Image.isPlayable)
        assertFalse(MediaType.Unknown.isPlayable)
    }
}
