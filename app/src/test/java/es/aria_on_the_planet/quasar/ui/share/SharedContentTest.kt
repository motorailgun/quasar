package es.aria_on_the_planet.quasar.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentTest {
    private val own = "es.aria_on_the_planet.quasar.provider"
    private val isOwn: (String) -> Boolean = { it == own }

    @Test
    fun `title and url are joined`() {
        assertEquals("Page title\nhttps://example.invalid/a", sharedText("Page title", "https://example.invalid/a"))
    }

    @Test
    fun `title already in text is not repeated`() {
        assertEquals("Page title https://example.invalid/a", sharedText("Page title", " Page title https://example.invalid/a "))
    }

    @Test
    fun `either part alone`() {
        assertEquals("only text", sharedText(null, "only text"))
        assertEquals("only title", sharedText("only title", "  "))
        assertEquals("", sharedText(null, null))
    }

    @Test
    fun `content uris of other apps are safe`() {
        assertTrue(isSafeSharedUri("content://media/external/images/media/1", isOwn))
        assertTrue(isSafeSharedUri("CONTENT://com.example.files/x.png", isOwn))
    }

    @Test
    fun `file uris and own provider are rejected`() {
        assertFalse(isSafeSharedUri("file:///data/data/es.aria_on_the_planet.quasar/shared_prefs/secure.xml", isOwn))
        assertFalse(isSafeSharedUri("content://$own/files/x", isOwn))
        assertFalse(isSafeSharedUri("content://$own?x=1", isOwn))
        assertFalse(isSafeSharedUri("content:///no-authority", isOwn))
        assertFalse(isSafeSharedUri("content:relative", isOwn))
        assertFalse(isSafeSharedUri("https://example.invalid/a.png", isOwn))
        assertFalse(isSafeSharedUri("no-scheme", isOwn))
    }

    @Test
    fun `media are deduplicated in order and unsafe ones are counted`() {
        val content = sharedContentOf(
            type = "image/*",
            subject = null,
            text = "caption",
            streams = listOf("content://a/1", "file:///sdcard/x.png", "content://a/2", "content://a/1", "content://$own/y"),
            isOwnProvider = isOwn,
        )
        assertEquals(SharedContent("caption", listOf("content://a/1", "content://a/2"), rejectedMedia = 2), content)
    }

    @Test
    fun `streams of text shares are ignored`() {
        // 共有シートのサムネイル用の ClipData 等。添付しないし、添付できなかったとも知らせない
        val content = sharedContentOf(
            type = "text/plain",
            subject = null,
            text = "https://example.invalid/a",
            streams = listOf("content://a/thumbnail"),
            isOwnProvider = isOwn,
        )
        assertEquals(SharedContent("https://example.invalid/a"), content)
    }
}
