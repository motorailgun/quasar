package es.aria_on_the_planet.quasar.ui.post

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun insertReplyAfterParentSubtree() {
        val descendants = listOf(post("a", "root"), post("a1", "a"), post("a2", "a1"), post("b", "root"))
        fun ids(list: List<Post>?) = list?.map { it.statusId }
        // a への返信は a の子孫 (a1, a2) の後、b の前
        assertEquals(listOf("a", "a1", "a2", "x", "b"), ids(insertReply(descendants, "root", post("x", "a"))))
        // 本体への返信は末尾
        assertEquals(listOf("a", "a1", "a2", "b", "x"), ids(insertReply(descendants, "root", post("x", "root"))))
        // 末端への返信はその直後
        assertEquals(listOf("a", "a1", "a2", "x", "b"), ids(insertReply(descendants, "root", post("x", "a2"))))
    }

    @Test
    fun insertReplyIgnoresUnrelatedOrKnown() {
        val descendants = listOf(post("a", "root"))
        assertNull(insertReply(descendants, "root", post("x", "elsewhere")))
        assertSame(descendants, insertReply(descendants, "root", post("a", "root")))
    }
}
