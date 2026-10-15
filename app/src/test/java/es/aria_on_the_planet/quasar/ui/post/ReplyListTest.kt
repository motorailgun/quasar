package es.aria_on_the_planet.quasar.ui.post

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReplyListTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(id: String, replyTo: String?) =
        Post(id, user, "", Instant.EPOCH, 0, 0, 0, emptyList(), statusId = id, inReplyToId = replyTo)

    @Test
    fun keepsOrderAndMarksAdjacentReplies() {
        // a → a1 → a2 が連続、b は本体への別リプライ、c は a1 へのリプライ (隣接していない)
        val rows = linearReplies(
            listOf(post("a", "root"), post("a1", "a"), post("a2", "a1"), post("b", "root"), post("c", "a1")),
        )
        assertEquals(listOf("a", "a1", "a2", "b", "c"), rows.map { it.post.statusId })
        assertEquals(listOf(false, true, true, false, false), rows.map { it.connectedToPrevious })
        assertEquals(listOf(true, true, false, false, false), rows.map { it.connectedToNext })
    }
}
