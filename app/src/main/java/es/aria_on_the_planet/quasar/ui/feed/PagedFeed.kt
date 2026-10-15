package es.aria_on_the_planet.quasar.ui.feed

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedState<T>(
    val items: List<T> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

/**
 * `max_id` で古い方向へページングする一覧の状態管理。ポスト・通知など ID を持つ一覧で共通に使う。
 *
 * @param idOf 次ページの `max_id` と重複除去に使う ID。
 * @param fetch `maxId` より古いものを返す。`null` なら最新から。
 */
open class PagedFeed<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> String,
    private val fetch: suspend (maxId: String?) -> List<T>,
) {
    private val _state = MutableStateFlow(FeedState<T>())
    val state: StateFlow<FeedState<T>> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        scope.launch {
            try {
                val items = fetch(null)
                _state.update {
                    it.copy(items = items, isRefreshing = false, loaded = true, endReached = items.isEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRefreshing = false, loaded = true, error = e.message ?: e.toString()) }
            }
        }
    }

    /** 読み込み済みの項目を書き換える (投票結果の反映など)。 */
    fun updateItems(transform: (T) -> T) {
        _state.update { it.copy(items = it.items.map(transform)) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isRefreshing || current.isLoadingMore || current.endReached) return
        val maxId = current.items.lastOrNull()?.let(idOf) ?: return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        scope.launch {
            try {
                val older = fetch(maxId)
                _state.update { s ->
                    val known = s.items.mapTo(HashSet(), idOf)
                    s.copy(
                        items = s.items + older.filter { idOf(it) !in known },
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
