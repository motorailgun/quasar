package es.aria_on_the_planet.quasar.domain.model.settings

import es.aria_on_the_planet.quasar.domain.model.PostVisibility

/**
 * アカウントごとの設定 (サーバ・アカウントの使い方で変えたくなるもの)。アカウントごとのファイルに保存し
 * (`AccountContext.settings`)、設定画面の「アカウントの設定」で変える。新しく足したアカウントは既定値から始まる。
 * 端末共通の設定は [DeviceSettings]。
 */
data class AccountSettings(
    val notificationApi: NotificationApi = NotificationApi.Grouped,
    val streaming: StreamingSettings = StreamingSettings(),
    /** 新しい投稿の公開範囲。`null` ならアカウントの既定 (`verify_credentials` の `source.privacy`)。 */
    val defaultVisibility: PostVisibility? = null,
    /**
     * 投稿の添付で、サーバの上限を超える (または受け付けない形式の) 画像を送る前に端末で圧縮し直すか
     * (大きさ (縦横) は変えず、WebP / JPEG で画質を下げる)。既定はオフ (そのまま送り、上限を超えれば送れない)。
     */
    val compressOversizedImages: Boolean = false,
    /**
     * 投稿の添付の画像から、送る前に EXIF (位置情報・カメラ・日時等) と XMP 等のメタデータを除くか。上限に合っている画像は
     * 作り直さずメタデータだけを除く (HEIC 等それができない形式は作り直す)。既定はオフ。
     */
    val stripImageMetadata: Boolean = false,
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

/** 新しい投稿の公開範囲を選べる値 (Mastodon の `source.privacy` と同じく、ダイレクトは既定にできない)。 */
val DefaultVisibilityChoices = listOf(PostVisibility.Public, PostVisibility.Unlisted, PostVisibility.Private)
