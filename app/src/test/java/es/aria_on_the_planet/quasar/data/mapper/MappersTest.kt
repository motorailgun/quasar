package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.domain.model.PostViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    private val alice = AccountDto(
        id = "1", username = "alice", acct = "alice@remote.example",
        url = "https://remote.example/@alice", avatar = "https://remote.example/a.png",
    )
    private val bob = alice.copy(id = "2", username = "bob", acct = "bob", url = "https://home.example:8443/@bob")

    @Test
    fun mapsAccountServerUrl() {
        assertEquals("https://remote.example", alice.toDomain().serverUrl)
        assertEquals("https://home.example:8443", bob.toDomain().serverUrl)
    }

    @Test
    fun mapsPlainStatus() {
        val post = StatusDto(
            id = "10", createdAt = "2026-10-15T00:00:00.000Z", account = alice,
            content = "<p>hello</p>", repliesCount = 2, reblogsCount = 3, favouritesCount = 5,
        ).toDomain()
        assertEquals("10", post.id)
        assertEquals("alice@remote.example", post.author.screenName)
        assertEquals(2, post.repliesCount)
        assertEquals(3, post.boostsCount)
        assertEquals(5, post.reactionsCount)
        assertNull(post.boostedBy)
        // 未認証時はフラグが返らないので false 扱い
        assertEquals(PostViewerState(), post.viewer)
    }

    @Test
    fun mapsViewerFlags() {
        val post = StatusDto(
            id = "10", createdAt = "2026-10-15T00:00:00Z", account = alice,
            favourited = true, reblogged = false, bookmarked = true,
        ).toDomain()
        assertEquals(PostViewerState(bookmarked = true, reblogged = false, favourited = true), post.viewer)
    }

    @Test
    fun mapsBoostToInnerPostWithOuterId() {
        val inner = StatusDto(
            id = "10", createdAt = "2026-10-15T00:00:00Z", account = alice,
            content = "<p>original</p>", repliesCount = 4, reblogsCount = 7, reblogged = true,
        )
        val post = StatusDto(
            id = "11", createdAt = "2026-10-15T01:00:00Z", account = bob, reblog = inner, reblogged = false,
        ).toDomain()
        assertEquals("11", post.id)
        assertEquals("alice@remote.example", post.author.screenName)
        assertEquals("<p>original</p>", post.text)
        assertEquals(4, post.repliesCount)
        assertEquals(7, post.boostsCount)
        assertEquals(true, post.viewer.reblogged)
        assertEquals("bob", post.boostedBy?.screenName)
    }
}
