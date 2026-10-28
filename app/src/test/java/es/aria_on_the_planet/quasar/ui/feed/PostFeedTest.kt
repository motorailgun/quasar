package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Page
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
        assertEquals(listOf("3", "2", "1"), feed.state.value.items.posts.map { it.id })
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
        assertEquals(listOf("1"), state.items.posts.map { it.id })
        assertEquals("boom", state.error)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun usesCursorFromRawPageNotDisplayOrder() {
        // 表示用の並び替え (append) をしても、次ページは取得した生ページの最古 ID から取る
        val requested = mutableListOf<String?>()
        val feed = PagedFeed<String, String>(
            scope = scope,
            nextMaxIdOf = { page -> page.minWithOrNull(MastodonIdComparator) },
            // 毎回新しいページを先頭に入れる (= 末尾が最古でなくなる) 並べ方
            append = { current, page -> page + current },
        ) { maxId ->
            requested += maxId
            when (maxId) {
                null -> listOf("30", "20")
                "20" -> listOf("10", "9")
                else -> emptyList()
            }
        }
        feed.refresh()
        feed.loadMore()
        feed.loadMore()
        assertEquals(listOf(null, "20", "9"), requested)
        assertEquals(listOf("10", "9", "30", "20"), feed.state.value.items)
        assertTrue(feed.state.value.endReached)
    }

    @Test
    fun pagesByCursorFromPageWhenIdsAreNotCursors() {
        // フォロー / フォロワー一覧: max_id は項目 (アカウント) の ID ではなく Link ヘッダの値
        val requested = mutableListOf<String?>()
        val feed = PagedFeed<String, String>(
            scope = scope,
            append = appendDistinct(idOf = { it }),
        ) { maxId ->
            requested += maxId
            when (maxId) {
                null -> Page(listOf("a", "b"), nextMaxId = "f2")
                "f2" -> Page(listOf("b", "c"), nextMaxId = null) // 最後のページは next が無い
                else -> error("unexpected $maxId")
            }
        }
        feed.refresh()
        assertFalse(feed.state.value.endReached)
        feed.loadMore()
        assertTrue(feed.state.value.endReached)
        feed.loadMore() // 終端到達後は取得しない
        assertEquals(listOf(null, "f2"), requested)
        assertEquals(listOf("a", "b", "c"), feed.state.value.items)
    }

    @Test
    fun ensureLoadedFetchesOnlyOnce() {
        var fetched = 0
        val feed = PostFeed(scope) { fetched++; emptyList() }
        feed.ensureLoaded()
        feed.ensureLoaded()
        assertEquals(1, fetched)
        assertTrue(feed.state.value.loaded)
    }

    @Test
    fun appendDistinctMergesSameKey() {
        val append = appendDistinct<Pair<String, Int>>(idOf = { it.first }, merge = { a, b -> a.first to a.second + b.second })
        assertEquals(listOf("a" to 1, "b" to 5, "c" to 1), append(listOf("a" to 1, "b" to 2), listOf("b" to 3, "c" to 1)))
        val dropping = appendDistinct<Pair<String, Int>>(idOf = { it.first })
        assertEquals(listOf("a" to 1), dropping(listOf("a" to 1), listOf("a" to 9)))
    }

    @Test
    fun mastodonIdsCompareNumerically() {
        assertEquals("99", listOf("100", "99", "1000").minWithOrNull(MastodonIdComparator))
    }

    @Test
    fun cachedItemsAreMergedWithFirstPage() {
        val requested = mutableListOf<String?>()
        val feed = PostFeed(scope, firstPageLimit = 2) { maxId ->
            requested += maxId
            when (maxId) {
                null -> listOf(post("4"), post("3"))
                else -> emptyList()
            }
        }
        feed.showCached(listOf(post("3"), post("2"), post("1")).map(TimelineItem::PostItem))
        assertFalse(feed.state.value.loaded)
        feed.refresh()
        assertEquals(listOf("4", "3", "2", "1"), feed.state.value.items.posts.map { it.id })
        // 続きは残したキャッシュの末尾から
        feed.loadMore()
        assertEquals(listOf(null, "1"), requested)
    }

    @Test
    fun cachedItemsAreIgnoredAfterLoad() {
        val feed = PostFeed(scope, firstPageLimit = 2) { listOf(post("4"), post("3")) }
        feed.refresh()
        feed.showCached(listOf(post("2")).map(TimelineItem::PostItem))
        assertEquals(listOf("4", "3"), feed.state.value.items.posts.map { it.id })
    }
}
