package es.aria_on_the_planet.quasar.ui.component.media

import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaMeta
import es.aria_on_the_planet.quasar.domain.model.MediaSize
import es.aria_on_the_planet.quasar.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayAspectRatioTest {
    private fun media(meta: MediaMeta?) = MediaAttachment("1", MediaType.Image, "u", null, "p", meta, null, null)

    @Test
    fun prefersSmallSize() {
        val meta = MediaMeta(original = MediaSize(1000, 1000), small = MediaSize(640, 360), durationSeconds = null)
        assertEquals(640f / 360f, displayAspectRatio(media(meta)), 0.001f)
    }

    @Test
    fun fallsBackToSquareAndClampsExtremes() {
        assertEquals(1f, displayAspectRatio(media(null)), 0f)
        val panorama = MediaMeta(MediaSize(5000, 500), null, null)
        assertEquals(3f, displayAspectRatio(media(panorama)), 0f)
        val tall = MediaMeta(MediaSize(100, 1000), null, null)
        assertEquals(1f / 3f, displayAspectRatio(media(tall)), 0.0001f)
    }
}
