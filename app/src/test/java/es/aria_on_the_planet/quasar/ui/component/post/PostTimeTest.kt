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

    private val now = Instant.parse("2026-10-27T03:00:00Z") // 東京では 12:00

    private fun relative(at: String, locale: Locale = Locale.US) =
        relativeTime(Instant.parse(at), now, zone = tokyo, locale = locale)

    @Test
    fun recentIsElapsed() {
        assertEquals("30s", relative("2026-10-27T02:59:30Z"))
        assertEquals("5m", relative("2026-10-27T02:55:00Z"))
        assertEquals("3h", relative("2026-10-27T00:00:00Z"))
        assertEquals("6d", relative("2026-10-20T03:00:01Z"))
    }

    @Test
    fun weekOrOlderIsDate() {
        assertEquals("Oct 20", relative("2026-10-20T03:00:00Z"))
        assertEquals("10/20", relative("2026-10-20T03:00:00Z", Locale.JAPAN))
    }

    @Test
    fun usesZoneForDay() {
        // UTC では 8/17 だが東京では 8/18
        assertEquals("Aug 18", relative("2026-08-17T16:00:00Z"))
    }

    @Test
    fun previousYearsIncludeYear() {
        assertEquals("Apr 10, 2023", relative("2023-04-10T03:00:00Z"))
        assertEquals("2023/04/10", relative("2023-04-10T03:00:00Z", Locale.JAPAN))
        // 年が変わっていれば 1 年以内でも年を付ける
        assertEquals("Dec 30, 2025", relative("2025-12-30T03:00:00Z"))
    }
}
