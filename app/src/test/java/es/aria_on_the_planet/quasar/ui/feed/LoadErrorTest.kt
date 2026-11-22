package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.data.remote.MastodonApiException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadErrorTest {
    @Test
    fun `401 and 403 ask to log in again`() {
        assertTrue(MastodonApiException(401, "HTTP 401: revoked").toLoadError().needsLogin)
        assertTrue(MastodonApiException(403, "HTTP 403: disabled").toLoadError().needsLogin)
    }

    @Test
    fun `other failures do not`() {
        assertFalse(MastodonApiException(404, "HTTP 404").toLoadError().needsLogin)
        assertFalse(MastodonApiException(422, "HTTP 422").toLoadError().needsLogin)
        assertFalse(MastodonApiException(500, "HTTP 500").toLoadError().needsLogin)
        assertFalse(IOException("timeout").toLoadError().needsLogin)
    }

    @Test
    fun `uses the server's error text`() {
        val e = MastodonApiException(401, """HTTP 401: {"error":"The access token was revoked"}""")
        assertEquals("The access token was revoked", e.toLoadError().message)
    }

    @Test
    fun `falls back to the exception message`() {
        assertEquals("HTTP 502: <html>", MastodonApiException(502, "HTTP 502: <html>").toLoadError().message)
        assertEquals("timeout", IOException("timeout").toLoadError().message)
    }
}
