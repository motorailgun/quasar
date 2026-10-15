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
    /** 次ページ取得に使う `max_id`。取得した生のページから決め、表示上の並び (再グルーピング等) には依存しない。 */
    val nextMaxId: String? = null,
)

/**
 * `max_id` で古い方向へページングする一覧の状態管理。ポスト・通知など ID を持つ一覧で共通に使う。
 *
 * @param R API から取得する 1 件の型。
 * @param T 表示する 1 件の型 (グルーピング等で [R] と異なりうる)。
 * @param nextMaxIdOf 取得したページから次ページの `max_id` を決める (ページ中で最も古い ID)。
 * @param append 現在の一覧に、取得した (より古い) ページを足した一覧を返す純粋関数。refresh では空の一覧に足す。
 * @param trim [updateList] (ストリーム・補完による新しい側への追加) の後に一覧を最大件数に切り詰める純粋関数。
 *   読み進めて伸ばした分 (loadMore) には適用しない。
 * @param nextMaxIdOfItems 切り詰めで末尾が変わったときに、残った一覧から次ページの `max_id` を求め直す。
 * @param fetch `maxId` より古いものを返す。`null` なら最新から。
 */
open class PagedFeed<R, T>(
    private val scope: CoroutineScope,
    private val nextMaxIdOf: (page: List<R>) -> String?,
    private val append: (current: List<T>, page: List<R>) -> List<T>,
    private val trim: (List<T>) -> List<T> = { it },
    private val nextMaxIdOfItems: (List<T>) -> String? = { null },
    private val fetch: suspend (maxId: String?) -> List<R>,
) {
    private val _state = MutableStateFlow(FeedState<T>())
    val state: StateFlow<FeedState<T>> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        scope.launch {
            try {
                val page = fetch(null)
                _state.update {
                    it.copy(
                        items = append(emptyList(), page),
                        isRefreshing = false,
                        loaded = true,
                        endReached = page.isEmpty(),
                        nextMaxId = nextMaxIdOf(page),
                    )
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

    /**
     * 一覧全体を書き換える (ストリーミングの新着・削除の反映など)。[transform] は純粋関数にする。
     * その後 [trim] で最大件数に切り詰め、末尾が捨てられたら次ページの `max_id` を残った一覧から求め直す
     * (捨てた分は下へスクロールすれば読み直せる)。
     */
    fun updateList(transform: (List<T>) -> List<T>) {
        _state.update { s ->
            val items = transform(s.items)
            val trimmed = trim(items)
            if (trimmed.size == items.size) {
                s.copy(items = items)
            } else {
                s.copy(items = trimmed, nextMaxId = nextMaxIdOfItems(trimmed) ?: s.nextMaxId, endReached = false)
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isRefreshing || current.isLoadingMore || current.endReached) return
        val maxId = current.nextMaxId ?: return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        scope.launch {
            try {
                val page = fetch(maxId)
                _state.update { s ->
                    s.copy(
                        items = append(s.items, page),
                        isLoadingMore = false,
                        endReached = page.isEmpty(),
                        nextMaxId = nextMaxIdOf(page) ?: s.nextMaxId,
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

/**
 * 既存と同じキーの項目を捨てて (または [merge] で統合して) 末尾に足す、標準的な append。
 */
fun <T> appendDistinct(idOf: (T) -> String, merge: ((existing: T, older: T) -> T)? = null): (List<T>, List<T>) -> List<T> =
    { items, older ->
        val indexByKey = HashMap<String, Int>()
        val result = items.toMutableList()
        result.forEachIndexed { i, item -> indexByKey[idOf(item)] = i }
        for (item in older) {
            val key = idOf(item)
            val existing = indexByKey[key]
            if (existing == null) {
                indexByKey[key] = result.size
                result += item
            } else if (merge != null) {
                result[existing] = merge(result[existing], item)
            }
        }
        result
    }

/**
 * Mastodon の ID (数値の文字列) の大小比較。桁数 → 辞書順で比べる。
 * ID は数値である保証は無いが、Mastodon は Snowflake 風の数値 ID なのでこれで順序が付く。
 */
val MastodonIdComparator: Comparator<String> = compareBy<String> { it.length }.thenBy { it }
