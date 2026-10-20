package es.aria_on_the_planet.quasar.domain.model.settings

import es.aria_on_the_planet.quasar.domain.model.PostAction
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.PostVisibility

/** アプリの設定全体。`SettingsStore` が保存し、設定画面で変える。 */
data class AppSettings(
    val display: DisplaySettings = DisplaySettings(),
    val notificationApi: NotificationApi = NotificationApi.Grouped,
    val streaming: StreamingSettings = StreamingSettings(),
    /** 送る前に確認する操作。既定は自分のポストの編集・削除だけ ([DefaultConfirmations])。 */
    val confirmations: Set<ConfirmTarget> = DefaultConfirmations,
    /** 新しい投稿の公開範囲。`null` ならアカウントの既定 (`verify_credentials` の `source.privacy`)。 */
    val defaultVisibility: PostVisibility? = null,
    val predictiveBack: PredictiveBackSettings = PredictiveBackSettings(),
)

/** 通知の取得に使う API。 */
enum class NotificationApi {
    /** `GET /api/v2/notifications`。サーバがグルーピングする (Mastodon 4.3+)。 */
    Grouped,

    /** `GET /api/v1/notifications`。グルーピングしない (各通知を 1 件のグループとして扱う)。 */
    Ungrouped,
}

/**
 * カラムごとの自動更新 (ストリーミング + 再接続時の補完) の有無。オフのカラムは pull-to-refresh でだけ新しくなる。
 * グローバルは流量が多いので既定はオフ。
 */
data class StreamingSettings(
    val home: Boolean = true,
    val notifications: Boolean = true,
    val local: Boolean = true,
    val global: Boolean = false,
)

/** 送る前に確認できる操作。 */
enum class ConfirmTarget {
    Post, Boost, Unboost, Favourite, Unfavourite, Bookmark, Unbookmark,

    /** 自分のポストの編集の保存。 */
    EditPost,

    /** 自分のポストの削除。 */
    DeletePost,

    /** 相手から自分へのフォローリクエストの拒否。 */
    RejectFollowRequest,
}

/**
 * 既定で確認する操作。取り消せない (削除・フォローリクエストの拒否) ・やり直しが面倒な (編集) 操作。
 * 足すときは `SettingsPreferences.kt` に個別のキーも足す (まとめたキーには入れない)。
 */
val DefaultConfirmations: Set<ConfirmTarget> =
    setOf(ConfirmTarget.EditPost, ConfirmTarget.DeletePost, ConfirmTarget.RejectFollowRequest)

/** この操作に対応する確認の対象。 */
val PostAction.confirmTarget: ConfirmTarget
    get() = when (type) {
        PostActionType.Boost -> if (undo) ConfirmTarget.Unboost else ConfirmTarget.Boost
        PostActionType.Favourite -> if (undo) ConfirmTarget.Unfavourite else ConfirmTarget.Favourite
        PostActionType.Bookmark -> if (undo) ConfirmTarget.Unbookmark else ConfirmTarget.Bookmark
    }

/** 新しい投稿の公開範囲を選べる値 (Mastodon の `source.privacy` と同じく、ダイレクトは既定にできない)。 */
val DefaultVisibilityChoices = listOf(PostVisibility.Public, PostVisibility.Unlisted, PostVisibility.Private)
