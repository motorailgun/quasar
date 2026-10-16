package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReplyDraftTest {
    private fun user(acct: String) = User(acct, acct, "", "https://e.invalid", "", "", emptyList())
    private fun post(
        author: String,
        mentions: List<String> = emptyList(),
        visibility: PostVisibility = PostVisibility.Public,
        cw: String? = null,
    ) = Post(
        "1", user(author), "", Instant.EPOCH, 0, 0, 0, emptyList(),
        mentions = mentions, visibility = visibility, contentWarning = cw,
    )

    private val empty = ReplyFields("", PostVisibility.Public, contentWarningEnabled = false, contentWarning = "")

    @Test
    fun mentionsPutAuthorFirstAndSkipSelfAndDuplicates() {
        val target = post("alice", mentions = listOf("me", "bob@remote.invalid", "Alice", "bob@remote.invalid"))
        assertEquals("@alice @bob@remote.invalid ", replyMentions(target, myAcct = "me"))
    }

    @Test
    fun selfReplyWithoutMentionsHasNoPrefix() {
        assertEquals("", replyMentions(post("me"), myAcct = "me"))
    }

    @Test
    fun visibilityTakesTheNarrowerOne() {
        assertEquals(PostVisibility.Private, replyVisibility(PostVisibility.Public, PostVisibility.Private))
        assertEquals(PostVisibility.Unlisted, replyVisibility(PostVisibility.Unlisted, PostVisibility.Public))
        assertEquals(PostVisibility.Direct, replyVisibility(PostVisibility.Private, PostVisibility.Direct))
    }

    @Test
    fun startOnEmptyDraftFillsEverything() {
        val (fields, memory) = startReply(empty, null, post("alice", visibility = PostVisibility.Private, cw = "spoiler"), "me")
        assertEquals(ReplyFields("@alice ", PostVisibility.Private, true, "spoiler"), fields)
        assertEquals(empty, memory.before)
        assertEquals("@alice ", memory.mentions)
    }

    @Test
    fun startKeepsUserText() {
        val (fields, memory) = startReply(empty.copy(text = "hello"), null, post("alice"), "me")
        assertEquals("hello", fields.text)
        assertEquals("", memory.mentions)
    }

    @Test
    fun startKeepsUserContentWarning() {
        val own = empty.copy(contentWarningEnabled = true, contentWarning = "mine")
        val (fields, memory) = startReply(own, null, post("alice", cw = "theirs"), "me")
        assertEquals("mine", fields.contentWarning)
        assertEquals(null, memory.contentWarning)
    }

    @Test
    fun retargetReplacesAutomaticValues() {
        val (first, memory) = startReply(empty, null, post("alice", visibility = PostVisibility.Direct, cw = "cw"), "me")
        val (second, memory2) = startReply(first, memory, post("bob"), "me")
        // 宛先・公開範囲・CW とも、返信前の値から計算し直す
        assertEquals(ReplyFields("@bob ", PostVisibility.Public, false, ""), second)
        assertEquals(empty, memory2.before)
    }

    @Test
    fun retargetKeepsWhatTheUserChanged() {
        val (first, memory) = startReply(empty, null, post("alice"), "me")
        val edited = first.copy(text = "@alice hi", visibility = PostVisibility.Unlisted)
        val (second, _) = startReply(edited, memory, post("bob", visibility = PostVisibility.Public), "me")
        assertEquals("@alice hi", second.text)
        assertEquals(PostVisibility.Unlisted, second.visibility)
    }

    @Test
    fun cancelRestoresUntouchedValues() {
        val before = empty.copy(visibility = PostVisibility.Unlisted)
        val (fields, memory) = startReply(before, null, post("alice", visibility = PostVisibility.Private, cw = "cw"), "me")
        assertEquals(before, cancelReply(fields, memory))
    }

    @Test
    fun cancelKeepsEditedValues() {
        val (fields, memory) = startReply(empty, null, post("alice", cw = "cw"), "me")
        val edited = fields.copy(text = "@alice hi", contentWarning = "edited")
        assertEquals(ReplyFields("@alice hi", PostVisibility.Public, true, "edited"), cancelReply(edited, memory))
    }

    @Test
    fun untouchedOnlyWithAutomaticContent() {
        val (fields, memory) = startReply(empty, null, post("alice", cw = "cw"), "me")
        assertTrue(empty.isUntouched(null, hasAttachments = false, hasPoll = false))
        assertTrue(fields.isUntouched(memory, hasAttachments = false, hasPoll = false))
        assertFalse(fields.copy(text = "@alice hi").isUntouched(memory, hasAttachments = false, hasPoll = false))
        assertFalse(fields.copy(contentWarning = "x").isUntouched(memory, hasAttachments = false, hasPoll = false))
        assertFalse(fields.isUntouched(memory, hasAttachments = true, hasPoll = false))
        assertFalse(empty.isUntouched(null, hasAttachments = false, hasPoll = true))
    }
}
