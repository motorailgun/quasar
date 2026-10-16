package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDraftTest {
    private val limits = ComposeLimits(maxCharacters = 10, maxMediaAttachments = 2, maxPollOptions = 3, maxPollOptionCharacters = 5)
    private fun uploaded(id: String) = DraftAttachment(id, null, state = UploadState.Uploaded("m$id"))

    @Test
    fun plainTextIsSubmittable() {
        assertEquals(emptySet<DraftProblem>(), PostDraft("hello").problems(limits))
    }

    @Test
    fun blankTextNeedsMedia() {
        assertEquals(setOf(DraftProblem.EmptyText), PostDraft("  ").problems(limits))
        assertEquals(emptySet<DraftProblem>(), PostDraft("", attachments = listOf(uploaded("1"))).problems(limits))
    }

    @Test
    fun tooLongCountsContentWarning() {
        assertTrue(DraftProblem.TooLong !in PostDraft("1234567890").problems(limits))
        assertTrue(DraftProblem.TooLong in PostDraft("1234567890", contentWarning = "x").problems(limits))
    }

    @Test
    fun mediaAndPollAreExclusive() {
        val draft = PostDraft("a", attachments = listOf(uploaded("1")), poll = PollDraft(listOf("x", "y")))
        assertTrue(DraftProblem.MediaAndPoll in draft.problems(limits))
    }

    @Test
    fun attachmentsMustBeUploadedAndWithinLimit() {
        val pending = DraftAttachment("p", null)
        assertTrue(DraftProblem.AttachmentNotUploaded in PostDraft("a", attachments = listOf(pending)).problems(limits))
        val many = PostDraft("a", attachments = listOf(uploaded("1"), uploaded("2"), uploaded("3")))
        assertTrue(DraftProblem.TooManyAttachments in many.problems(limits))
    }

    @Test
    fun pollOptionsAreValidated() {
        fun problemsOf(vararg options: String) = PostDraft("a", poll = PollDraft(options.toList())).problems(limits)
        assertEquals(emptySet<DraftProblem>(), problemsOf("x", "y"))
        assertTrue(DraftProblem.TooFewPollOptions in problemsOf("x"))
        assertTrue(DraftProblem.TooManyPollOptions in problemsOf("a", "b", "c", "d"))
        assertTrue(DraftProblem.BlankPollOption in problemsOf("x", " "))
        assertTrue(DraftProblem.PollOptionTooLong in problemsOf("x", "123456"))
    }

    @Test
    fun emptiness() {
        assertTrue(PostDraft("").isEmpty)
        assertTrue(PostDraft(" ", contentWarning = "").isEmpty)
        assertFalse(PostDraft("", contentWarning = "cw").isEmpty)
        assertFalse(PostDraft("", poll = PollDraft()).isEmpty)
    }

    @Test
    fun pollDurationsRespectLimits() {
        val durations = ComposeLimits(
            minPollExpiration = kotlin.time.Duration.parse("1h"),
            maxPollExpiration = kotlin.time.Duration.parse("1d"),
        ).pollDurations
        assertEquals(listOf("1h", "6h", "1d").map(kotlin.time.Duration::parse), durations)
    }
}
