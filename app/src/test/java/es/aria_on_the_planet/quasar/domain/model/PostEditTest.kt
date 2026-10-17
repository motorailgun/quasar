package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class PostEditTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val durations = listOf(30.minutes, 6.hours, 1.days, 3.days)

    private fun post(
        media: List<MediaAttachment> = emptyList(),
        poll: Poll? = null,
        sensitive: Boolean = false,
    ) = Post(
        "1", User("me", "me", "", "https://e.invalid", "", "", emptyList()), "<p>html</p>", Instant.EPOCH, 0, 0, 0,
        emptyList(), mediaAttachments = media, poll = poll, sensitive = sensitive, visibility = PostVisibility.Unlisted,
        contentWarning = "html cw",
    )

    private fun poll(expiresAt: Instant?) = Poll(
        "p", expiresAt, expired = false, multiple = true, votesCount = 0, votersCount = 0,
        options = listOf(PollOption("a", 1), PollOption("b", 2)), emojis = emptyList(), voted = false, ownVotes = emptyList(),
    )

    private fun media(id: String, type: MediaType) =
        MediaAttachment(id, type, "https://e.invalid/$id", null, "https://e.invalid/$id-small", null, null, "alt $id")

    @Test
    fun textAndContentWarningComeFromTheSource() {
        val draft = editDraftOf(post(), StatusSource("plain @alice", "cw"), durations, now)
        assertEquals("plain @alice", draft.text)
        assertEquals("cw", draft.contentWarning)
        assertEquals(PostVisibility.Unlisted, draft.visibility)
        assertNull(draft.inReplyToId)
    }

    @Test
    fun emptyContentWarningMeansNone() {
        assertNull(editDraftOf(post(), StatusSource("x", ""), durations, now).contentWarning)
    }

    @Test
    fun mediaIsAlreadyUploaded() {
        val draft = editDraftOf(
            post(listOf(media("1", MediaType.Image), media("2", MediaType.Gifv)), sensitive = true),
            StatusSource("x", ""), durations, now,
        )
        assertEquals(listOf(UploadState.Uploaded("1"), UploadState.Uploaded("2")), draft.attachments.map { it.state })
        assertEquals(listOf("alt 1", "alt 2"), draft.attachments.map { it.description })
        assertEquals(listOf(false, true), draft.attachments.map { it.isVideoLike })
        assertEquals(true, draft.sensitive)
    }

    @Test
    fun pollDurationIsTheClosestChoiceToTheRemainingTime() {
        val draft = editDraftOf(post(poll = poll(now.plusSeconds(5 * 3600))), StatusSource("x", ""), durations, now)
        assertEquals(PollDraft(listOf("a", "b"), 6.hours, multiple = true), draft.poll)
        val noExpiry = editDraftOf(post(poll = poll(null)), StatusSource("x", ""), durations, now)
        assertEquals(PollDraft().expiresIn, noExpiry.poll?.expiresIn)
    }
}
