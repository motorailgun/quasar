package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.data.repository.PostChange
import es.aria_on_the_planet.quasar.data.repository.RelationshipChange
import es.aria_on_the_planet.quasar.domain.model.Page
import es.aria_on_the_planet.quasar.domain.model.isHiddenBy
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
    mergeCached: ((List<TimelineItem>, List<Post>) -> List<TimelineItem>?)?,
    fetchPage: suspend (maxId: String?) -> Page<Post>,
) : PagedFeed<Post, TimelineItem>(
    scope = scope,
    append = ::appendOlderPosts,
    trim = ::trimTimeline,
    nextMaxIdOfItems = nextMaxIdOfItems,
    mergeCached = mergeCached,
    fetchPage = fetchPage,
) {
    /**
     * ポストの ID で `max_id` を決める一覧 (タイムライン・プロフィール等)。
     * @param firstPageLimit [showCached] で先に出す一覧なら、最初のページの `limit`。取得の結果を [mergeRefreshedPosts] で当てる。
     */
    constructor(
        scope: CoroutineScope,
        firstPageLimit: Int? = null,
        fetch: suspend (maxId: String?) -> List<Post>,
    ) : this(
        scope = scope,
        nextMaxIdOfItems = ::oldestPostId,
        mergeCached = firstPageLimit?.let { limit -> { current, page -> mergeRefreshedPosts(current, page, limit) } },
        // タイムラインは新しい順なので末尾が最も古い
        fetchPage = { maxId -> fetch(maxId).let { Page(it, it.lastOrNull()?.id) } },
    )

    companion object {
        /**
         * `max_id` がポストの ID ではなく Link ヘッダで渡される一覧 (お気に入り等)。次ページは [Page.nextMaxId] で決め、
         * 切り詰めた後に一覧から求め直すこともしない。
         */
        fun linked(scope: CoroutineScope, fetchPage: suspend (maxId: String?) -> Page<Post>): PostFeed =
            PostFeed(scope, nextMaxIdOfItems = { null }, mergeCached = null, fetchPage = fetchPage)
    }

    /** 読み込み済みのポストを書き換える (投票結果の反映など)。 */
    fun updatePosts(transform: (Post) -> Post) = updateList { it.mapPosts(transform) }

    /** [change] で見えなくなったポスト (ブロック・ミュートした相手のポスト等、[isHiddenBy]) を除く。 */
    fun hidePostsBy(change: RelationshipChange) =
        updateList { items -> removePosts(items) { it.isHiddenBy(change.accountId, change.relationship) } }

    /** 自分の編集・削除を当てる (編集は差し替え、削除はブーストごと除く)。 */
    fun applyChange(change: PostChange) = updateList { applyPostChange(it, change) }
}
