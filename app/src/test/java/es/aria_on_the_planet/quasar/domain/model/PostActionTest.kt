package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class PostActionTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(
        id: String,
        statusId: String = id,
        boosts: Int = 0,
        favourites: Int = 0,
        viewer: PostViewerState = PostViewerState(),
        quote: Quote? = null,
    ) = Post(id, user, "", Instant.EPOCH, 0, boosts, favourites, emptyList(), viewer = viewer, statusId = statusId, quote = quote)

    @Test
    fun toggleUndoesWhenAlreadyActive() {
        val p = post("a", viewer = PostViewerState(favourited = true))
        assertEquals(PostAction(PostActionType.Favourite, undo = true), PostActionType.Favourite.toggleOn(p))
        assertEquals(PostAction(PostActionType.Boost, undo = false), PostActionType.Boost.toggleOn(p))
    }

    @Test
    fun withActionUpdatesFlagAndCount() {
        val p = post("a", boosts = 3, favourites = 5)
        val boosted = p.withAction("a", PostAction(PostActionType.Boost, undo = false))
        assertEquals(true, boosted.viewer.reblogged)
        assertEquals(4, boosted.boostsCount)
        val faved = p.withAction("a", PostAction(PostActionType.Favourite, undo = false))
        assertEquals(true, faved.viewer.favourited)
        assertEquals(6, faved.reactionsCount)
        val bookmarked = p.withAction("a", PostAction(PostActionType.Bookmark, undo = false))
        assertEquals(true, bookmarked.viewer.bookmarked)
        assertEquals(3, bookmarked.boostsCount)
        assertEquals(5, bookmarked.reactionsCount)
    }

    @Test
    fun withActionIsIdempotentAndRevertible() {
        val p = post("a", favourites = 5)
        val action = PostAction(PostActionType.Favourite, undo = false)
        val once = p.withAction("a", action)
        assertEquals(once, once.withAction("a", action))
        assertEquals(p, once.withAction("a", action.inverse()))
        // 巻き戻しを重ねても件数は減り続けない
        assertEquals(p, once.withAction("a", action.inverse()).withAction("a", action.inverse()))
    }

    @Test
    fun undoNeverMakesCountNegative() {
        val p = post("a", favourites = 0, viewer = PostViewerState(favourited = true))
        assertEquals(0, p.withAction("a", PostAction(PostActionType.Favourite, undo = true)).reactionsCount)
    }

    @Test
    fun withActionMatchesStatusIdOfBoostAndQuote() {
        // ブースト (外側 b、中身 a) と、a を引用したポスト
        val boost = post("b", statusId = "a")
        val quoting = post("q", quote = Quote(QuoteState.Accepted, post("a")))
        val action = PostAction(PostActionType.Favourite, undo = false)
        assertEquals(true, boost.withAction("a", action).viewer.favourited)
        val updated = quoting.withAction("a", action)
        assertEquals(false, updated.viewer.favourited)
        assertEquals(true, updated.quote!!.post!!.viewer.favourited)
    }

    @Test
    fun withActionLeavesOtherPostsAlone() {
        val p = post("x")
        assertSame(p, p.withAction("a", PostAction(PostActionType.Boost, undo = false)))
    }

    @Test
    fun withInteractionsOfCopiesCountsAndViewerButKeepsBoostWrapper() {
        val boost = post("b", statusId = "a").copy(boostedBy = user)
        val server = post("a", boosts = 10, favourites = 20, viewer = PostViewerState(reblogged = true, bookmarked = true))
        val updated = boost.withInteractionsOf(server)
        assertEquals("b", updated.id)
        assertEquals(user, updated.boostedBy)
        assertEquals(10, updated.boostsCount)
        assertEquals(20, updated.reactionsCount)
        assertEquals(server.viewer, updated.viewer)
        val other = post("x")
        assertSame(other, other.withInteractionsOf(server))
    }
}
