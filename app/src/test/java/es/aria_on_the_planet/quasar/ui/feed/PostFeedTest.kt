package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PostFeedTest {
    private val user = User("1", "alice", "", "https://e", "", "", emptyList())
    private fun post(id: String) = Post(id, user, "", Instant.EPOCH, 0, 0, 0, emptyList())

    // Unconfined なので launch は fetch 完了まで同期的に進む
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun refreshThenLoadMorePagesByMaxId() {
        val requested = mutableListOf<String?>()
        val feed = PostFeed(scope) { maxId ->
            requested += maxId
            when (maxId) {
                null -> listOf(post("3"), post("2"))
                "2" -> listOf(post("2"), post("1")) // 重複は除外される
                else -> emptyList()
            }
        }
        feed.refresh()
        feed.loadMore()
        assertEquals(listOf("3", "2", "1"), feed.state.value.posts.map { it.id })
        assertFalse(feed.state.value.endReached)

        feed.loadMore()
        assertTrue(feed.state.value.endReached)
        feed.loadMore() // 終端到達後は取得しない
        assertEquals(listOf(null, "2", "1"), requested)
    }

    @Test
    fun keepsPostsAndReportsErrorOnFailure() {
        var fail = false
        val feed = PostFeed(scope) { if (fail) throw IOException("boom") else listOf(post("1")) }
        feed.refresh()
        fail = true
        feed.loadMore()
        val state = feed.state.value
        assertEquals(listOf("1"), state.posts.map { it.id })
        assertEquals("boom", state.error)
        assertFalse(state.isLoadingMore)
    }
}
