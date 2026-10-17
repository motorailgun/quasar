package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PostLinkTargetTest {
    private val alice = Mention("1", "alice", "alice", "https://local.invalid/@alice")
    private val tag = Hashtag("Quasar", "https://local.invalid/tags/quasar")
    private val post = Post(
        "1", User("9", "bob", "", "https://local.invalid", "", "", emptyList()), "", Instant.EPOCH, 0, 0, 0, emptyList(),
        mentions = listOf(alice), tags = listOf(tag),
    )

    @Test
    fun mentionByUrl() {
        assertEquals(PostLinkTarget.Account(alice), post.linkTarget("https://local.invalid/@alice", "@alice"))
    }

    @Test
    fun hashtagByUrl() {
        assertEquals(PostLinkTarget.Tag(tag), post.linkTarget("https://local.invalid/tags/quasar", null))
    }

    @Test
    fun remoteHashtagByText() {
        // 他サーバのポストでは href が元のサーバのページになる。名前は大文字小文字を区別しない
        assertEquals(PostLinkTarget.Tag(tag), post.linkTarget("https://remote.invalid/tags/quasar", "#quasar"))
    }

    @Test
    fun unknownHashtagIsExternal() {
        // tags に無い名前 (本文中の普通のリンクの文字列が # で始まる場合など)
        val url = "https://remote.invalid/tags/other"
        assertEquals(PostLinkTarget.External(url), post.linkTarget(url, "#other"))
    }

    @Test
    fun plainLinkIsExternal() {
        val url = "https://example.invalid/page"
        assertEquals(PostLinkTarget.External(url), post.linkTarget(url, "example.invalid/page"))
        assertEquals(PostLinkTarget.External(url), post.linkTarget(url, null))
    }
}
