package es.aria_on_the_planet.quasar.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FollowNotificationRefreshTest {
    private val fetchedAt = Instant.parse("2026-11-23T12:00:00Z")

    @Test
    fun `not fetched yet does not refresh`() {
        assertFalse(needsRefreshForFollowNotification(fetchedAt.plusSeconds(10), meFetchedAt = null, handledAt = null))
    }

    @Test
    fun `notification after the fetch refreshes`() {
        assertTrue(needsRefreshForFollowNotification(fetchedAt.plusSeconds(10), fetchedAt, handledAt = null))
    }

    @Test
    fun `notification slightly before the fetch refreshes for clock skew`() {
        assertTrue(needsRefreshForFollowNotification(fetchedAt.minusSeconds(30), fetchedAt, handledAt = null))
    }

    @Test
    fun `old notification does not refresh`() {
        assertFalse(needsRefreshForFollowNotification(fetchedAt.minusSeconds(3600), fetchedAt, handledAt = null))
    }

    @Test
    fun `already handled notification does not refresh again`() {
        val at = fetchedAt.minusSeconds(30)
        // 取り直した後も時計のずれの分は「後」に見えるが、同じ通知では取り直さない
        assertFalse(needsRefreshForFollowNotification(at, fetchedAt.plusSeconds(5), handledAt = at))
        assertTrue(needsRefreshForFollowNotification(at.plusSeconds(1), fetchedAt.plusSeconds(5), handledAt = at))
    }
}
