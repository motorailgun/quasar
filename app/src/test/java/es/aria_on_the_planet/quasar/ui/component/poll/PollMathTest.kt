package es.aria_on_the_planet.quasar.ui.component.poll

import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.PollOption
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.Locale

class PollMathTest {
    private fun poll(votes: List<Int?>, multiple: Boolean = false, voters: Int? = null) = Poll(
        id = "1", expiresAt = null, expired = true, multiple = multiple,
        votesCount = votes.sumOf { it ?: 0 }, votersCount = voters,
        options = votes.mapIndexed { i, v -> PollOption("o$i", v) },
        emojis = emptyList(), voted = false, ownVotes = emptyList(),
    )

    @Test
    fun singleChoiceUsesTotalVotes() {
        val p = poll(listOf(45, 2, 44, 9))
        assertEquals(listOf(0.45f, 0.02f, 0.44f, 0.09f), p.fractions())
        assertEquals(setOf(0), p.leadingOptions())
    }

    @Test
    fun multipleChoiceUsesVoters() {
        // 票数合計 250 / 投票者 123
        val p = poll(listOf(93, 32, 77, 48), multiple = true, voters = 123)
        assertEquals(93f / 123f, p.fractions()[0]!!, 0.0001f)
    }

    @Test
    fun hiddenResultsAndTies() {
        assertEquals(listOf(null, null), poll(listOf(null, null)).fractions())
        assertEquals(setOf(0, 1), poll(listOf(3, 3, 1)).leadingOptions())
        assertEquals(emptySet<Int>(), poll(listOf(0, 0)).leadingOptions())
        assertEquals(listOf(0f, 0f), poll(listOf(0, 0)).fractions())
    }

    @Test
    fun formatsPercent() {
        assertEquals("45%", formatPercent(0.45f, Locale.US))
        assertEquals("1.8%", formatPercent(0.018f, Locale.US))
        assertEquals("0%", formatPercent(0f, Locale.US))
        assertEquals("100%", formatPercent(1f, Locale.US))
    }

    @Test
    fun computesTimeLeft() {
        val now = Instant.parse("2026-10-15T00:00:00Z")
        assertEquals(TimeLeft.Days(6), timeLeft(Instant.parse("2026-10-21T05:00:00Z"), now))
        assertEquals(TimeLeft.Hours(3), timeLeft(Instant.parse("2026-10-15T03:30:00Z"), now))
        assertEquals(TimeLeft.Minutes(1), timeLeft(Instant.parse("2026-10-15T00:00:20Z"), now))
        assertEquals(TimeLeft.Ended, timeLeft(Instant.parse("2026-10-14T00:00:00Z"), now))
    }
}
