package es.aria_on_the_planet.quasar.domain.model

/** ポストに対して取り消しのできる操作 (状態が [PostViewerState] に現れるもの)。 */
enum class PostActionType { Boost, Favourite, Bookmark }

/**
 * ポストに対する操作 1 回分。[undo] が `true` なら取り消し (unreblog / unfavourite / unbookmark)。
 * どちらにするかはタップ時のポストの状態から決める ([toggleOn])。
 */
data class PostAction(val type: PostActionType, val undo: Boolean) {
    /** 逆の操作 (失敗時の巻き戻しに使う)。 */
    fun inverse(): PostAction = copy(undo = !undo)
}

/** [post] の現在の状態に対して、この種類の操作を切り替える操作 (済みなら取り消し)。 */
fun PostActionType.toggleOn(post: Post): PostAction = PostAction(this, undo = post.viewer.isActive(this))

fun PostViewerState.isActive(type: PostActionType): Boolean = when (type) {
    PostActionType.Boost -> reblogged
    PostActionType.Favourite -> favourited
    PostActionType.Bookmark -> bookmarked
}

/**
 * [statusId] のポスト (引用先も含む) に [action] の結果を見込みで反映する。
 * 既にその状態なら何もしないので、同じ操作や巻き戻しを重ねて適用しても件数がずれない。
 */
fun Post.withAction(statusId: String, action: PostAction): Post {
    val quoted = quote?.post
    val withQuote = if (quoted != null) copy(quote = quote.copy(post = quoted.withAction(statusId, action))) else this
    if (this.statusId != statusId) return withQuote
    val active = !action.undo
    if (viewer.isActive(action.type) == active) return withQuote
    val delta = if (active) 1 else -1
    return when (action.type) {
        PostActionType.Boost -> withQuote.copy(
            viewer = viewer.copy(reblogged = active),
            boostsCount = (boostsCount + delta).coerceAtLeast(0),
        )
        PostActionType.Favourite -> withQuote.copy(
            viewer = viewer.copy(favourited = active),
            reactionsCount = (reactionsCount + delta).coerceAtLeast(0),
        )
        PostActionType.Bookmark -> withQuote.copy(viewer = viewer.copy(bookmarked = active))
    }
}

/**
 * サーバから返った [source] の件数と操作状態を、同じ Status のポスト (引用先も含む) に反映する。
 * ブーストの外側 ([Post.id] / [Post.boostedBy]) は書き換えない。
 */
fun Post.withInteractionsOf(source: Post): Post {
    val quoted = quote?.post
    val withQuote = if (quoted != null) copy(quote = quote.copy(post = quoted.withInteractionsOf(source))) else this
    if (statusId != source.statusId) return withQuote
    return withQuote.copy(
        repliesCount = source.repliesCount,
        boostsCount = source.boostsCount,
        reactionsCount = source.reactionsCount,
        viewer = source.viewer,
    )
}
