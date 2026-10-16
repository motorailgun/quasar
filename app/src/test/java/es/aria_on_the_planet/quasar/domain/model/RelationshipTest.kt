package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipTest {
    private fun relationship(
        following: Boolean = false,
        requested: Boolean = false,
        blocking: Boolean = false,
        blockedBy: Boolean = false,
    ) = Relationship(following, requested, followedBy = false, blocking = blocking, blockedBy = blockedBy)

    @Test
    fun followState() {
        assertEquals(FollowState.NotFollowing, relationship().followState)
        assertEquals(FollowState.Requested, relationship(requested = true).followState)
        assertEquals(FollowState.Following, relationship(following = true).followState)
        // 承認直後など両方立っていてもフォロー中
        assertEquals(FollowState.Following, relationship(following = true, requested = true).followState)
    }

    @Test
    fun blocksTakePrecedence() {
        assertEquals(FollowState.Blocking, relationship(following = true, blocking = true).followState)
        assertEquals(FollowState.BlockedBy, relationship(requested = true, blockedBy = true).followState)
    }
}
