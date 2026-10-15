package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CoroutineScope

typealias PostFeedState = FeedState<Post>

val FeedState<Post>.posts: List<Post> get() = items

/**
 * ポスト一覧の [PagedFeed]。タイムライン・プロフィールなど、ポスト一覧を持つ ViewModel で共通に使う。
 * ページングにはポストの ID (ブーストならブースト自体の ID) を使う。
 */
class PostFeed(
    scope: CoroutineScope,
    fetch: suspend (maxId: String?) -> List<Post>,
) : PagedFeed<Post, Post>(
    scope = scope,
    // タイムラインは新しい順なので末尾が最も古い
    nextMaxIdOf = { page -> page.lastOrNull()?.id },
    append = appendDistinct(idOf = { it.id }),
    fetch = fetch,
) {
    /** 読み込み済みのポストを書き換える (投票結果の反映など)。 */
    fun updatePosts(transform: (Post) -> Post) = updateItems(transform)
}
