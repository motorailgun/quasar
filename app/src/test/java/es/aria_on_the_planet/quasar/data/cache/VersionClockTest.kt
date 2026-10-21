package es.aria_on_the_planet.quasar.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionClockTest {
    @Test
    fun versionsAlwaysIncreaseEvenIfTheClockStallsOrGoesBack() {
        var now = 100L
        val clock = VersionClock { now }
        assertEquals(100L, clock.next())
        // 同じミリ秒
        assertEquals(101L, clock.next())
        // 時計の巻き戻り
        now = 50
        assertEquals(102L, clock.next())
        // 実時刻が追い越したら実時刻
        now = 500
        assertEquals(500L, clock.next())
    }
}
