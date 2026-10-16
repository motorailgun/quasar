package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PostLengthTest {
    @Test
    fun countsPlainText() {
        assertEquals(0, countPostCharacters(""))
        assertEquals(5, countPostCharacters("hello"))
        assertEquals(5, countPostCharacters("こんにちは"))
    }

    @Test
    fun countsUrlsAsFixedLength() {
        assertEquals(23, countPostCharacters("https://example.com/a/very/long/path?with=query&and=more"))
        assertEquals(4 + 23, countPostCharacters("see http://e.co"))
        assertEquals(23 * 2 + 1, countPostCharacters("https://a.example https://b.example"))
    }

    @Test
    fun excludesTrailingPunctuationFromUrls() {
        assertEquals(23 + 1, countPostCharacters("https://example.com."))
        assertEquals(1 + 23 + 1, countPostCharacters("(https://example.com)"))
    }

    @Test
    fun usesConfiguredUrlLength() {
        assertEquals(10, countPostCharacters("https://example.com", ComposeLimits(charactersPerUrl = 10)))
    }

    @Test
    fun ignoresUrlsWithoutScheme() {
        assertEquals("example.com".length, countPostCharacters("example.com"))
    }

    @Test
    fun countsRemoteMentionsByUsernameOnly() {
        assertEquals("@alice".length, countPostCharacters("@alice@mastodon.social"))
        assertEquals("hi @bob".length, countPostCharacters("hi @bob@example.co.jp"))
        assertEquals("@local".length, countPostCharacters("@local"))
    }

    @Test
    fun doesNotTreatEmailAddressesAsMentions() {
        assertEquals("me@example.com".length, countPostCharacters("me@example.com"))
    }

    @Test
    fun countsGraphemeClusters() {
        assertEquals(1, countPostCharacters("👍🏽"))
        assertEquals(1, countPostCharacters("👨‍👩‍👧"))
        assertEquals(1, countPostCharacters("🇯🇵"))
    }

    @Test
    fun draftCountIncludesContentWarning() {
        val draft = PostDraft(text = "body", contentWarning = "cw")
        assertEquals(6, draft.characterCount(ComposeLimits()))
    }
}
