package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionTest {
    private val alice = Mention("1", "alice", "alice", "https://local.invalid/@alice")
    private val bob = Mention("2", "bob", "bob@remote.invalid", "https://remote.invalid/users/bob")
    private val mentions = listOf(alice, bob)

    @Test
    fun findsMentionByProfileUrl() {
        assertEquals(alice, mentions.findByUrl("https://local.invalid/@alice"))
        assertEquals(bob, mentions.findByUrl("https://remote.invalid/users/bob"))
        // 末尾のスラッシュの有無は区別しない
        assertEquals(bob, mentions.findByUrl("https://remote.invalid/users/bob/"))
    }

    @Test
    fun otherLinksAreNotMentions() {
        // ハッシュタグ・普通のリンク・同じサーバの別ユーザー
        assertNull(mentions.findByUrl("https://local.invalid/tags/alice"))
        assertNull(mentions.findByUrl("https://example.invalid/"))
        assertNull(mentions.findByUrl("https://local.invalid/@alice2"))
        // url の無いメンション (古い応答) とは一致させない
        assertNull(listOf(Mention("3", "c", "c", "")).findByUrl(""))
    }

    @Test
    fun asUserKeepsIdAndServer() {
        val user = bob.asUser()
        assertEquals("2", user.inServerId)
        assertEquals("bob@remote.invalid", user.screenName)
        assertEquals("https://remote.invalid", user.serverUrl)
    }
}
