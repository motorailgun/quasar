package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsTab

/**
 * 画面からの遷移の口。画面 (`ProfileScreen` 等) はコールバックで遷移を受けるので、コールバックはここから組み立てる。
 * 実装は `QuasarNavHost` (バックスタックに積む)。デバッグ用ギャラリーは自前の実装を置く。
 */
interface Navigator {
    fun openProfile(user: User)
    fun openPost(post: Post)

    /** [name] は `#` 無し。 */
    fun openHashtag(name: String)
    fun openFollowList(accountId: String, tab: FollowListTab)

    /** 通知のユーザー単位のまとめ ([account] が [posts] にした [type]) の中身。 */
    fun openAccountReactions(account: User, type: NotificationType, posts: List<Post>)

    /** [post] (ブーストなら中身) をブースト / お気に入りにしたユーザーの一覧。 */
    fun openPostReactions(post: Post, tab: PostReactionsTab)

    /** 投稿画面 (下書きは共有)。 */
    fun openComposer()

    /** 添付メディアのタップ。unknown は元のファイルをブラウザで開き、それ以外はメディアビューア。 */
    fun openMedia(click: MediaClick)

    /** メディアビューア。ポストの添付以外 (プロフィールのアイコン等) はこちらを直接使う。 */
    fun openMediaViewer(args: MediaViewerArgs)
    fun openSettings()

    /** 今の画面を閉じる (一番下の画面ならアプリを閉じる)。 */
    fun back()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator is not provided") }
