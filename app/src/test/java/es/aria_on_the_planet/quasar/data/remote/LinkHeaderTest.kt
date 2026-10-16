package es.aria_on_the_planet.quasar.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkHeaderTest {
    @Test
    fun takesMaxIdOfNextLink() {
        val header = "<https://mastodon.example/api/v1/accounts/1/followers?limit=2&max_id=7628164>; rel=\"next\", " +
            "<https://mastodon.example/api/v1/accounts/1/followers?limit=2&since_id=7628165>; rel=\"prev\""
        assertEquals("7628164", nextMaxIdFromLinkHeader(header))
    }

    @Test
    fun orderOfLinksDoesNotMatter() {
        val header = "<https://e/api/v1/accounts/1/following?since_id=9>; rel=\"prev\", " +
            "<https://e/api/v1/accounts/1/following?max_id=5>; rel=\"next\""
        assertEquals("5", nextMaxIdFromLinkHeader(header))
    }

    @Test
    fun nullOnLastPage() {
        assertNull(nextMaxIdFromLinkHeader("<https://e/api/v1/accounts/1/followers?since_id=9>; rel=\"prev\""))
        assertNull(nextMaxIdFromLinkHeader(null))
        assertNull(nextMaxIdFromLinkHeader(""))
    }
}
