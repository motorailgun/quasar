package es.aria_on_the_planet.quasar.ui.component.post

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class PostTimeTest {
    private val instant = Instant.parse("2026-08-10T13:30:10Z")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun formats24Hour() {
        assertEquals("2026 08/10 22:30:10", formatDetailedTime(instant, is24Hour = true, zone = tokyo, locale = Locale.US))
    }

    @Test
    fun formats12Hour() {
        assertEquals("2026 08/10 10:30:10 PM", formatDetailedTime(instant, is24Hour = false, zone = tokyo, locale = Locale.US))
    }
}
