package es.aria_on_the_planet.quasar.ui.feed

import es.aria_on_the_planet.quasar.domain.model.Page
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
    /**
     * 直前の書き換えで先頭に新しい項目が入ったとき、先頭にいたら先頭に留まって新着を見せるか
     * (`StickToTopEffect`)。ストリームの新着は `true`、補完 (切断中の分をまとめて取ったもの) は `false` で、
     * 読んでいた項目の位置を保ったまま上に入れる。
     */
    val stickToTop: Boolean = true,
)

/**
 * `max_id` で古い方向へページングする一覧の状態管理。ポスト・通知など ID を持つ一覧で共通に使う。
 *
 * 次ページの `max_id` は [fetchPage] が返す [Page.nextMaxId] で決める。項目の ID から決まる一覧 (ポスト・通知) は
 * `nextMaxIdOf` を取る副コンストラクタを使う。
 *
 * @param R API から取得する 1 件の型。
 * @param T 表示する 1 件の型 (グルーピング等で [R] と異なりうる)。
 * @param append 現在の一覧に、取得した (より古い) ページを足した一覧を返す純粋関数。refresh では空の一覧に足す。
 * @param trim [updateList] (ストリーム・補完による新しい側への追加) の後に一覧を最大件数に切り詰める純粋関数。
 *   読み進めて伸ばした分 (loadMore) には適用しない。
 * @param nextMaxIdOfItems 切り詰めで末尾が変わったときに、残った一覧から次ページの `max_id` を求め直す。
 * @param mergeCached [showCached] で先に出した一覧に、最初の取得の結果を当てる純粋関数。
 *   `null` を返したら (または未指定なら) 取得の結果で置き換える。
 * @param fetchPage `maxId` より古いページを返す。`null` なら最新から。
 */
open class PagedFeed<R, T>(
    private val scope: CoroutineScope,
    private val append: (current: List<T>, page: List<R>) -> List<T>,
    private val trim: (List<T>) -> List<T> = { it },
    private val nextMaxIdOfItems: (List<T>) -> String? = { null },
    private val mergeCached: ((current: List<T>, page: List<R>) -> List<T>?)? = null,
    private val fetchPage: suspend (maxId: String?) -> Page<R>,
) {
    /**
     * @param nextMaxIdOf 取得したページから次ページの `max_id` を決める (ページ中で最も古い ID)。
     * @param fetch `maxId` より古いものを返す。`null` なら最新から。
     */
    constructor(
        scope: CoroutineScope,
        nextMaxIdOf: (page: List<R>) -> String?,
        append: (current: List<T>, page: List<R>) -> List<T>,
        trim: (List<T>) -> List<T> = { it },
        nextMaxIdOfItems: (List<T>) -> String? = { null },
        fetch: suspend (maxId: String?) -> List<R>,
    ) : this(
        scope = scope,
        append = append,
        trim = trim,
        nextMaxIdOfItems = nextMaxIdOfItems,
        fetchPage = { maxId -> fetch(maxId).let { Page(it, nextMaxIdOf(it)) } },
    )

    private val _state = MutableStateFlow(FeedState<T>())
    val state: StateFlow<FeedState<T>> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        scope.launch {
            try {
                val page = fetchPage(null)
                _state.update {
                    // まだ取得していない (= 出ているのは showCached の分) なら、位置を保つよう当てる
                    val merged = if (!it.loaded) mergeCached?.invoke(it.items, page.items) else null
                    it.copy(
                        items = merged ?: append(emptyList(), page.items),
                        stickToTop = true,
                        isRefreshing = false,
                        loaded = true,
                        endReached = page.endReached,
                        nextMaxId = merged?.let(nextMaxIdOfItems) ?: page.nextMaxId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRefreshing = false, loaded = true, error = e.message ?: e.toString()) }
            }
        }
    }

    /**
     * 前に取ったもの (キャッシュ) を、最初の取得が終わるまでの間だけ先に出す。取得済み・既に項目があれば何もしない。
     * 取得の結果は [mergeCached] で当てる。取得が終わるまで [FeedState.loaded] は `false` のまま (裏の取得なので、
     * 引っ張って更新のインジケータは出ない)。
     */
    fun showCached(items: List<T>) {
        _state.update {
            if (it.loaded || it.items.isNotEmpty()) it else it.copy(items = items, nextMaxId = nextMaxIdOfItems(items))
        }
    }

    /** まだ読み込んでいなければ読み込む (タブを初めて開いたとき等)。 */
    fun ensureLoaded() {
        val current = _state.value
        if (!current.loaded && !current.isRefreshing) refresh()
    }

    /** 読み込み済みの項目を書き換える (投票結果の反映など)。 */
    fun updateItems(transform: (T) -> T) {
        _state.update { it.copy(items = it.items.map(transform)) }
    }

    /**
     * 一覧全体を書き換える (ストリーミングの新着・削除の反映など)。[transform] は純粋関数にする。
     * その後 [trim] で最大件数に切り詰め、末尾が捨てられたら次ページの `max_id` を残った一覧から求め直す
     * (捨てた分は下へスクロールすれば読み直せる)。
     *
     * @param stickToTop 先頭に新しい項目が入ったとき、先頭にいたら先頭に留まるか ([FeedState.stickToTop])。
     */
    fun updateList(stickToTop: Boolean = true, transform: (List<T>) -> List<T>) {
        _state.update { s ->
            val items = transform(s.items)
            val trimmed = trim(items)
            if (trimmed.size == items.size) {
                s.copy(items = items, stickToTop = stickToTop)
            } else {
                s.copy(
                    items = trimmed,
                    nextMaxId = nextMaxIdOfItems(trimmed) ?: s.nextMaxId,
                    endReached = false,
                    stickToTop = stickToTop,
                )
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
                val page = fetchPage(maxId)
                _state.update { s ->
                    s.copy(
                        items = append(s.items, page.items),
                        isLoadingMore = false,
                        endReached = page.endReached,
                        nextMaxId = page.nextMaxId ?: s.nextMaxId,
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
