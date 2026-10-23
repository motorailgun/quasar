package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CoroutineScope

typealias PostFeedState = FeedState<TimelineItem>

/**
 * ポスト一覧の [PagedFeed]。タイムライン・プロフィールなど、ポスト一覧を持つ ViewModel で共通に使う。
 * 一覧の要素は [TimelineItem] (ポストか gap)。ページングにはポストの ID (ブーストならブースト自体の ID) を使う。
 * ストリーム・補完で伸びたら [MaxTimelinePosts] 件を超えた古い側を捨てる。
 */
class PostFeed private constructor(
    scope: CoroutineScope,
    nextMaxIdOfItems: (List<TimelineItem>) -> String?,
    fetchPage: suspend (maxId: String?) -> Page<Post>,
) : PagedFeed<Post, TimelineItem>(
    scope = scope,
    append = ::appendOlderPosts,
    trim = ::trimTimeline,
    nextMaxIdOfItems = nextMaxIdOfItems,
    fetchPage = fetchPage,
) {
    /** ポストの ID で `max_id` を決める一覧 (タイムライン・プロフィール等)。 */
    constructor(scope: CoroutineScope, fetch: suspend (maxId: String?) -> List<Post>) : this(
        scope = scope,
        nextMaxIdOfItems = ::oldestPostId,
        // タイムラインは新しい順なので末尾が最も古い
        fetchPage = { maxId -> fetch(maxId).let { Page(it, it.lastOrNull()?.id) } },
    )

    companion object {
        /**
         * `max_id` がポストの ID ではなく Link ヘッダで渡される一覧 (お気に入り等)。次ページは [Page.nextMaxId] で決め、
         * 切り詰めた後に一覧から求め直すこともしない。
         */
        fun linked(scope: CoroutineScope, fetchPage: suspend (maxId: String?) -> Page<Post>): PostFeed =
            PostFeed(scope, nextMaxIdOfItems = { null }, fetchPage = fetchPage)
    }

    /** 読み込み済みのポストを書き換える (投票結果の反映など)。 */
    fun updatePosts(transform: (Post) -> Post) = updateList { it.mapPosts(transform) }
}
