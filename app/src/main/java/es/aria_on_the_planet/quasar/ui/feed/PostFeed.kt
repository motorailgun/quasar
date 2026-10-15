package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostFeedState(
    val posts: List<Post> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

/**
 * `max_id` で古い方向へページングするポスト一覧の状態管理。
 * タイムライン・プロフィールなど、ポスト一覧を持つ ViewModel で共通に使う。
 *
 * @param fetch `maxId` より古いポストを返す。`null` なら最新から。
 */
class PostFeed(
    private val scope: CoroutineScope,
    private val fetch: suspend (maxId: String?) -> List<Post>,
) {
    private val _state = MutableStateFlow(PostFeedState())
    val state: StateFlow<PostFeedState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        scope.launch {
            try {
                val posts = fetch(null)
                _state.update {
                    it.copy(posts = posts, isRefreshing = false, loaded = true, endReached = posts.isEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRefreshing = false, loaded = true, error = e.message ?: e.toString()) }
            }
        }
    }

    /** 読み込み済みのポストを書き換える (投票結果の反映など)。 */
    fun updatePosts(transform: (Post) -> Post) {
        _state.update { it.copy(posts = it.posts.map(transform)) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isRefreshing || current.isLoadingMore || current.endReached) return
        val maxId = current.posts.lastOrNull()?.id ?: return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        scope.launch {
            try {
                val older = fetch(maxId)
                _state.update { s ->
                    val known = s.posts.mapTo(HashSet()) { it.id }
                    s.copy(
                        posts = s.posts + older.filter { it.id !in known },
                        isLoadingMore = false,
                        endReached = older.isEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false, error = e.message ?: e.toString()) }
            }
        }
    }
}
