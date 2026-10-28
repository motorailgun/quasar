package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.runtime.Immutable
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick

/**
 * ポストへの操作の口。画面で 1 つ作り、[PostList] / [PostCard] / [PostDetail] / [QuotedPost] 等へそのまま渡す
 * (途中の部品は中身を見ずに中継する)。
 *
 * data class + [Immutable] なので、組み立て直しで作り直しても中身 (Compose が覚えているラムダ・関数参照) が同じなら
 * equals で等しく、一覧の項目の skip を妨げない。[Immutable] が無いと、suspend 関数型 ([onVote]) の安定性が推論できず
 * インスタンスの同一性で比べられ、画面の組み立て直しのたびに一覧の全項目が組み立て直される。
 *
 * @param onVote 投票する。`null` なら投票できず、投票は結果表示になる (デバッグ用ギャラリー等)。
 * @param onAction アクション行のブースト・お気に入り・ブックマーク ([rememberPostActionHandler])。`null` なら表示のみ。
 * @param onReply アクション行のリプライ。`null` なら表示のみ。
 * @param onHashtagClick 本文のハッシュタグ (`#` を除いた名前)。`null` ならブラウザで開く。
 */
@Immutable
data class PostCallbacks(
    val onUserClick: (User) -> Unit = {},
    val onPostClick: (Post) -> Unit = {},
    val onMediaClick: (MediaClick) -> Unit = {},
    val onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    val onAction: OnPostAction? = null,
    val onReply: ((Post) -> Unit)? = null,
    val onHashtagClick: ((String) -> Unit)? = null,
) {
    companion object {
        /** 何もしない (表示のみ)。 */
        val None = PostCallbacks()
    }
}
