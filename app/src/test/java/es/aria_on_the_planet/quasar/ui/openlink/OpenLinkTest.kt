package es.aria_on_the_planet.quasar.ui.openlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenLinkTest {
    @Test
    fun `url in text is taken`() {
        assertEquals(
            "https://example.invalid/@alice/123",
            linkQueryOf("Alice's post", "Alice's post https://example.invalid/@alice/123"),
        )
    }

    @Test
    fun `text is preferred over subject`() {
        assertEquals("https://b.invalid/x", linkQueryOf("https://a.invalid/y", "see https://b.invalid/x"))
    }

    @Test
    fun `subject is used when text has no link`() {
        assertEquals("https://a.invalid/y", linkQueryOf("https://a.invalid/y", "no link here"))
    }

    @Test
    fun `first url wins`() {
        assertEquals("https://a.invalid/1", findUrl("https://a.invalid/1 and https://b.invalid/2"))
    }

    @Test
    fun `trailing punctuation is not part of the url`() {
        assertEquals("https://a.invalid/notes/9x", findUrl("見て (https://a.invalid/notes/9x)。"))
        assertEquals("https://a.invalid/p", findUrl("「https://a.invalid/p」"))
        assertEquals("https://a.invalid/p", findUrl("https://a.invalid/p."))
    }

    @Test
    fun `query and fragment are kept`() {
        assertEquals("https://a.invalid/p?x=1#c", findUrl("https://a.invalid/p?x=1#c"))
    }

    @Test
    fun `url without host is ignored`() {
        assertNull(findUrl("https:// broken"))
    }

    @Test
    fun `acct is used when there is no url`() {
        assertEquals("@alice@example.invalid", linkQueryOf(null, "follow @alice@example.invalid!"))
        assertEquals("@bob@sub.example.invalid", linkQueryOf(null, "bob@sub.example.invalid"))
    }

    @Test
    fun `url wins over acct`() {
        assertEquals("https://a.invalid/x", linkQueryOf(null, "@alice@example.invalid https://a.invalid/x"))
    }

    @Test
    fun `acct inside a word is ignored`() {
        assertNull(findAcct("x@alice@example.invalid"))
        assertNull(findAcct("alice@localhost"))
    }

    @Test
    fun `nothing to open`() {
        assertNull(linkQueryOf(null, "hello"))
        assertNull(linkQueryOf(null, null))
    }

    @Test
    fun `hashtag pages`() {
        assertEquals("quasar", hashtagOfUrl("https://example.invalid/tags/quasar"))
        assertEquals("quasar", hashtagOfUrl("https://example.invalid/tag/quasar"))
        assertEquals("日本語", hashtagOfUrl("https://example.invalid/tags/%E6%97%A5%E6%9C%AC%E8%AA%9E"))
    }

    @Test
    fun `other pages are not hashtags`() {
        assertNull(hashtagOfUrl("https://example.invalid/@alice/123"))
        assertNull(hashtagOfUrl("https://example.invalid/tags"))
        assertNull(hashtagOfUrl("https://example.invalid/tags/a/b"))
        assertNull(hashtagOfUrl("@alice@example.invalid"))
    }
}
