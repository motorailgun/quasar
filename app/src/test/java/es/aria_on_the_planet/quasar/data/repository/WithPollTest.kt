package es.aria_on_the_planet.quasar.data.repository

import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Quote
import es.aria_on_the_planet.quasar.domain.model.QuoteState
import es.aria_on_the_planet.quasar.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class WithPollTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun poll(id: String, voted: Boolean) =
        Poll(id, null, false, false, 0, null, emptyList(), emptyList(), voted, emptyList())
    private fun post(id: String, poll: Poll?, quote: Quote? = null) =
        Post(id, user, "", Instant.EPOCH, 0, 0, 0, emptyList(), poll = poll, quote = quote)

    @Test
    fun replacesMatchingPollIncludingQuoted() {
        val quoted = post("q", poll("p1", voted = false))
        val p = post("a", poll("p2", voted = false), Quote(QuoteState.Accepted, quoted))
        val updated = p.withPoll(poll("p1", voted = true))
        assertEquals(false, updated.poll!!.voted) // 別の投票はそのまま
        assertEquals(true, updated.quote!!.post!!.poll!!.voted)
    }

    @Test
    fun leavesUnrelatedPostsAlone() {
        val other = poll("p9", voted = false)
        val p = post("a", other)
        assertSame(other, p.withPoll(poll("p1", voted = true)).poll)
    }
}
