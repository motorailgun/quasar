package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.ApplicationDto
import es.aria_on_the_planet.quasar.data.remote.dto.FieldDto
import es.aria_on_the_planet.quasar.data.remote.dto.GroupedNotificationsResultsDto
import es.aria_on_the_planet.quasar.data.remote.dto.NotificationGroupDto
import es.aria_on_the_planet.quasar.data.remote.dto.MentionDto
import es.aria_on_the_planet.quasar.data.remote.dto.NotificationDto
import es.aria_on_the_planet.quasar.domain.model.NotificationType
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
import kotlinx.serialization.json.Json
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

    @Test
    fun mapsContentWarningAndSensitive() {
        fun status(spoiler: String, sensitive: Boolean) = StatusDto(
            id = "1", createdAt = "2026-10-15T00:00:00Z", account = alice, spoilerText = spoiler, sensitive = sensitive,
        ).toDomain()
        assertEquals("NSFW", status("NSFW", true).contentWarning)
        assertEquals(true, status("NSFW", true).sensitive)
        assertNull(status(" ", false).contentWarning)
        assertEquals(false, status("", false).sensitive)
    }

    @Test
    fun resolvesReplyTargetAcct() {
        fun reply(toAccount: String?, mentions: List<MentionDto> = emptyList()) = StatusDto(
            id = "1", createdAt = "2026-10-15T00:00:00Z", account = alice,
            inReplyToId = toAccount?.let { "p" }, inReplyToAccountId = toAccount, mentions = mentions,
        ).toDomain().inReplyToAcct
        // 自分へのリプライ (スレッドの続き)
        assertEquals("alice@remote.example", reply("1"))
        // 他人へのリプライはメンションから
        assertEquals("bob", reply("2", listOf(MentionDto("9", "carol", "carol@x"), MentionDto("2", "bob", "bob"))))
        // メンションに無い
        assertNull(reply("3"))
        // リプライでない
        assertNull(reply(null))
    }

    @Test
    fun mapsNotifications() {
        val status = StatusDto(id = "s", createdAt = "2026-10-15T00:00:00Z", account = alice)
        fun n(type: String, withStatus: Boolean) =
            NotificationDto(
                id = "n", type = type, createdAt = "2026-10-15T01:00:00.000Z", account = bob,
                status = if (withStatus) status else null,
            ).toDomain()
        assertEquals(NotificationType.Favourite, n("favourite", true).type)
        assertEquals("s", n("favourite", true).post!!.statusId)
        assertEquals(NotificationType.Follow, n("follow", false).type)
        assertNull(n("follow", false).post)
        assertEquals("bob", n("follow", false).account.screenName)
        assertEquals(NotificationType.QuotedUpdate, n("quoted_update", true).type)
        // 未対応・将来の種別
        assertEquals(NotificationType.Unknown, n("admin.sign_up", false).type)
        assertEquals(NotificationType.Unknown, n("added_to_collection", false).type)
    }

    @Test
    fun mapsGroupedNotifications() {
        val status = StatusDto(id = "s", createdAt = "2026-10-15T00:00:00Z", account = alice)
        val groups = GroupedNotificationsResultsDto(
            accounts = listOf(alice, bob),
            statuses = listOf(status),
            notificationGroups = listOf(
                NotificationGroupDto(
                    groupKey = "favourite-s-1", notificationsCount = 3, type = "favourite",
                    mostRecentNotificationId = "30", pageMinId = "10", pageMaxId = "30",
                    latestPageNotificationAt = "2026-10-15T02:00:00.000Z",
                    sampleAccountIds = listOf("2", "1", "missing"), statusId = "s",
                ),
                NotificationGroupDto(
                    groupKey = "ungrouped-5", type = "mention", mostRecentNotificationId = "5",
                    sampleAccountIds = listOf("1"),
                ),
            ),
        ).toDomain()
        val fav = groups[0]
        assertEquals(NotificationType.Favourite, fav.type)
        assertEquals(3, fav.count)
        // サンプルの順序を保ち、見つからない ID は捨てる
        assertEquals(listOf("bob", "alice@remote.example"), fav.accounts.map { it.screenName })
        assertEquals("s", fav.post!!.statusId)
        assertEquals("10", fav.pageMinId)
        assertEquals("2026-10-15T02:00:00Z", fav.latestAt.toString())
        // page_min_id が無ければ most_recent_notification_id
        assertEquals("5", groups[1].pageMinId)
        assertNull(groups[1].post)
        assertNull(groups[1].latestAt)
    }

    @Test
    fun decodesNumericNotificationIds() {
        // mstdn.maud.io (4.8.0-alpha) で実際に返った形: most_recent_notification_id が数値
        val json = """{"accounts":[],"statuses":[],"notification_groups":[{"group_key":"ungrouped-5980585",
            "notifications_count":1,"type":"mention","most_recent_notification_id":5980585,
            "page_min_id":"5980585","page_max_id":"5980585","sample_account_ids":[]}]}"""
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString<GroupedNotificationsResultsDto>(json)
        assertEquals("5980585", decoded.notificationGroups[0].mostRecentNotificationId)
    }
}
