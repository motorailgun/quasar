package es.aria_on_the_planet.quasar.ui.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeServerUrlTest {
    @Test
    fun addsSchemeAndTrimsSlash() {
        assertEquals("https://mastodon.social", normalizeServerUrl(" mastodon.social/ "))
        assertEquals("http://localhost:3000", normalizeServerUrl("http://localhost:3000"))
    }

    @Test
    fun rejectsBlankAndInvalid() {
        assertNull(normalizeServerUrl("  "))
        assertNull(normalizeServerUrl("not a host"))
    }
}
