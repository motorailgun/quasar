package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserTest {
    private fun user(screenName: String) =
        User("1", screenName, "", "https://home.example", "", "", emptyList())

    @Test
    fun fullScreenNameAddsDomainForLocalUser() {
        assertEquals("alice@home.example", user("alice").fullScreenName)
        assertEquals("bob@remote.example", user("bob@remote.example").fullScreenName)
    }
}
