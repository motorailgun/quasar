package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WebPageTest {
    private val local = user("1", "alice", "https://local.invalid/@alice")
    private val remote = user("2", "bob@remote.invalid", "https://remote.invalid/users/bob")

    @Test
    fun localPagesUseConnectedServer() {
        assertEquals("https://local.invalid/@alice", local.localWebUrl("https://local.invalid"))
        // リモートのユーザー・ポストも接続中サーバで見られる
        assertEquals("https://local.invalid/@bob@remote.invalid", remote.localWebUrl("https://local.invalid/"))
        assertEquals(
            "https://local.invalid/@bob@remote.invalid/200",
            post(remote, id = "100", statusId = "200").localWebUrl("https://local.invalid"),
        )
    }

    @Test
    fun remotePagesOnlyForRemoteAccounts() {
        assertNull(local.remoteWebUrl())
        assertEquals("https://remote.invalid/users/bob", remote.remoteWebUrl())
        assertNull(post(local, url = "https://local.invalid/@alice/1").remoteWebUrl())
        assertEquals("https://remote.invalid/@bob/9", post(remote, url = "https://remote.invalid/@bob/9").remoteWebUrl())
        // http(s) でない URL・URL の無いものは出さない
        assertNull(post(remote, url = "tag:remote.invalid,2024:9").remoteWebUrl())
        assertNull(post(remote, url = null).remoteWebUrl())
    }

    @Test
    fun hostOfUrl() {
        assertEquals("remote.invalid", webHostOf("https://remote.invalid/users/bob"))
        assertNull(webHostOf("not a url"))
    }

    @Test
    fun hiddenPostsAfterRelationshipChange() {
        val none = Relationship(following = true, requested = false, followedBy = false)
        val byBob = post(remote)
        val boostOfAlice = post(local, boostedBy = remote)
        assertFalse(byBob.isHiddenBy("2", none))
        assertTrue(byBob.isHiddenBy("2", none.copy(blocking = true)))
        assertTrue(byBob.isHiddenBy("2", none.copy(muting = true)))
        assertTrue(boostOfAlice.isHiddenBy("2", none.copy(muting = true)))
        // ブーストの非表示はブーストだけ
        assertFalse(byBob.isHiddenBy("2", none.copy(showingReblogs = false)))
        assertTrue(boostOfAlice.isHiddenBy("2", none.copy(showingReblogs = false)))
        // 別の人には関係しない
        assertFalse(byBob.isHiddenBy("1", none.copy(blocking = true)))
    }

    private fun user(id: String, acct: String, url: String) = User(
        inServerId = id,
        screenName = acct,
        displayName = acct,
        serverUrl = url.substringBefore("/users").substringBefore("/@"),
        iconUrl = "",
        note = "",
        emojis = emptyList(),
        url = url,
    )

    private fun post(
        author: User,
        id: String = "1",
        statusId: String = id,
        url: String? = null,
        boostedBy: User? = null,
    ) = Post(
        id = id,
        author = author,
        text = "",
        createdAt = Instant.EPOCH,
        repliesCount = 0,
        boostsCount = 0,
        reactionsCount = 0,
        emojis = emptyList(),
        statusId = statusId,
        url = url,
        boostedBy = boostedBy,
    )
}
