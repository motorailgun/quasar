package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.ApplicationDto
import es.aria_on_the_planet.quasar.data.remote.dto.FieldDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaAttachmentDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaMetaDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaSizeDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollOptionDto
import es.aria_on_the_planet.quasar.data.remote.dto.PreviewCardDto
import es.aria_on_the_planet.quasar.data.remote.dto.QuoteDto
import es.aria_on_the_planet.quasar.domain.model.QuoteState
import es.aria_on_the_planet.quasar.domain.model.MediaType
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

    @Test
    fun mapsMediaAndCard() {
        val post = StatusDto(
            id = "10", createdAt = "2026-10-15T00:00:00Z", account = alice,
            mediaAttachments = listOf(
                MediaAttachmentDto(
                    id = "m1", type = "video", url = "https://e/v.mp4", previewUrl = "https://e/v.png",
                    meta = MediaMetaDto(original = MediaSizeDto(864.0, 1080.0, 21.7), small = MediaSizeDto(512.0, 640.0)),
                    blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj", description = "",
                ),
                MediaAttachmentDto(id = "m2", type = "unknown", remoteUrl = "https://r/x"),
            ),
            card = PreviewCardDto(url = "https://example.com", title = "Example"),
        ).toDomain()
        val video = post.mediaAttachments[0]
        assertEquals(MediaType.Video, video.type)
        assertEquals(0.8f, video.meta!!.aspectRatio!!, 0.001f)
        assertEquals(21.7, video.meta!!.durationSeconds!!, 0.0)
        assertNull(video.description)
        assertEquals(MediaType.Unknown, post.mediaAttachments[1].type)
        assertEquals("https://r/x", post.mediaAttachments[1].url)
        assertEquals("Example", post.card?.title)
        assertNull(post.card?.imageUrl)
    }

    @Test
    fun mapsProfile() {
        val profile = alice.copy(
            header = "https://remote.example/headers/original/missing.png",
            statusesCount = 19600, followingCount = 683, followersCount = 435,
            createdAt = "2018-02-01T00:00:00.000Z", locked = true,
            fields = listOf(
                FieldDto("Web", "<a href=\"https://example.com\">example.com</a>", "2024-01-01T00:00:00.000+00:00"),
                FieldDto("Lang", "ja", null),
            ),
        ).toProfile()
        assertNull(profile.headerUrl)
        assertEquals(19600, profile.postsCount)
        assertEquals(683, profile.followingCount)
        assertEquals(435, profile.followersCount)
        assertEquals(true, profile.locked)
        assertEquals("2018-02-01T00:00:00Z", profile.createdAt.toString())
        assertEquals(listOf(true, false), profile.fields.map { it.verifiedAt != null })

        val withHeader = alice.copy(header = "https://remote.example/h.png").toProfile()
        assertEquals("https://remote.example/h.png", withHeader.headerUrl)
    }

    @Test
    fun mapsPollAndQuote() {
        val quoted = StatusDto(id = "q", createdAt = "2026-10-11T00:00:00Z", account = bob, content = "<p>quoted</p>")
        val post = StatusDto(
            id = "10", createdAt = "2026-10-15T00:00:00Z", account = alice,
            poll = PollDto(
                id = "p", expiresAt = "2026-10-21T00:00:00.000Z", multiple = true, votesCount = 3, votersCount = 2,
                options = listOf(PollOptionDto("A", 2), PollOptionDto("B", null)), ownVotes = listOf(0), voted = true,
            ),
            quote = QuoteDto(state = "accepted", quotedStatus = quoted),
        ).toDomain()
        val poll = post.poll!!
        assertEquals(true, poll.multiple)
        assertEquals(2, poll.votersCount)
        assertEquals(listOf(2, null), poll.options.map { it.votesCount })
        assertEquals(listOf(0), poll.ownVotes)
        assertEquals(true, poll.voted)
        assertEquals("2026-10-21T00:00:00Z", poll.expiresAt.toString())
        assertEquals(QuoteState.Accepted, post.quote!!.state)
        assertEquals("q", post.quote!!.post!!.statusId)

        // ShallowQuote (quoted_status_id のみ) と未知の state
        val shallow = QuoteDto(state = "accepted", quotedStatusId = "q").toDomain()
        assertNull(shallow.post)
        assertEquals(QuoteState.Unauthorized, QuoteDto(state = "something_new").toDomain().state)
        assertEquals(QuoteState.Pending, QuoteDto(state = "pending").toDomain().state)
    }

    @Test
    fun mapsApplication() {
        fun status(app: ApplicationDto?) =
            StatusDto(id = "1", createdAt = "2026-10-15T00:00:00Z", account = alice, application = app).toDomain()
        assertEquals("Web", status(ApplicationDto("Web", null)).application!!.name)
        assertNull(status(ApplicationDto("Web", "")).application!!.website)
        assertEquals("https://quasar.example", status(ApplicationDto("Quasar", "https://quasar.example")).application!!.website)
        assertNull(status(null).application)
    }
}
