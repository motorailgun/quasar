package es.aria_on_the_planet.quasar.ui.navigation

import androidx.navigation3.runtime.NavKey
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import kotlinx.serialization.Serializable

/**
 * 画面 (Navigation 3 のキー)。引数は画面を開き直す (プロセスの再生成からの復元を含む) のに要る最小限にする
 * (`rememberNavBackStack` が保存する)。
 */
@Serializable
sealed interface Route : NavKey {
    /** メイン画面 (タイムライン)。ログイン中のルート。 */
    @Serializable data object Timeline : Route

    /** ログイン (サーバ URL + トークン)。ログインしていないときのルート。 */
    @Serializable data object Login : Route

    /** プロフィール。[accountId] は接続中サーバ内の ID。 */
    @Serializable data class Profile(val accountId: String) : Route

    @Serializable data class FollowList(val accountId: String, val tab: FollowListTab) : Route

    /** ハッシュタグのタイムライン。[name] は `#` 無し。 */
    @Serializable data class Hashtag(val name: String) : Route

    /**
     * 通知のユーザー単位のまとめの中身: [accountId] が [statusIds] のポストにした [type] (お気に入り / ブースト)。
     * [statusIds] は通知と同じ新しい順。
     */
    @Serializable data class AccountReactions(
        val accountId: String,
        val type: NotificationType,
        val statusIds: List<String>,
    ) : Route

    /** ポストの詳細。ブーストなら中身の ID。 */
    @Serializable data class Post(val statusId: String) : Route

    /** 投稿画面 (クイック投稿バーの無い画面からの返信用)。下書きは共有。 */
    @Serializable data object Composer : Route

    /** メディアビューア (下の画面に重ねて表示する)。 */
    @Serializable data class MediaViewer(val args: MediaViewerArgs) : Route

    /** 設定のページ。 */
    @Serializable data object Settings : Route
    @Serializable data object SettingsDisplay : Route
    @Serializable data object SettingsAnimation : Route
    @Serializable data object SettingsTimelines : Route
    @Serializable data object SettingsPosting : Route
    @Serializable data object SettingsConfirmations : Route
}
