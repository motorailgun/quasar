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
    val predictiveBack: PredictiveBackSettings = PredictiveBackSettings(),
    val translation: TranslationSettings = TranslationSettings(),
)

/**
 * ポストの翻訳 (端末内のモデル。full のフレーバーだけ)。言語は翻訳のモデル (PLaMo 3 Translate) の言語コード
 * (`ja`・`en`・`zh-hans` 等)。
 *
 * 言語ごとの扱いは「その他の言語」([defaultMode]) と、それと違う扱いにする言語の一覧 ([languageModes]) で決める。
 * 訳す先の言語のポストには、どちらにも関わらず出さない。
 */
data class TranslationSettings(
    /** 翻訳を使う (オンにするとモデルをダウンロードする)。オフなら翻訳の表示を出さない。 */
    val enabled: Boolean = false,
    /** 一覧 ([languageModes]) に無い言語の扱い。 */
    val defaultMode: TranslationMode = TranslationMode.Offer,
    /**
     * 言語ごとの扱い。`null` はまだ一覧を変えていない状態で、端末の言語を [TranslationMode.Hidden] にした一覧として扱う
     * ([languageModesOr])。端末の言語を変えたときに追従させるため、変えるまでは保存しない。
     */
    val languageModes: Map<String, TranslationMode>? = null,
    /** 訳す先。`null` なら端末の言語。 */
    val targetLanguage: String? = null,
) {
    /** 言語ごとの扱いの一覧。まだ変えていなければ [deviceLanguages] (端末の言語) を出さない一覧。 */
    fun languageModesOr(deviceLanguages: List<String>): Map<String, TranslationMode> =
        languageModes ?: deviceLanguages.associateWith { TranslationMode.Hidden }

    /** [language] のポストの扱い。 */
    fun modeOf(language: String, deviceLanguages: List<String>): TranslationMode =
        languageModesOr(deviceLanguages)[language] ?: defaultMode

    /** 一覧の [language] を [mode] にする (無ければ足す)。 */
    fun withLanguageMode(language: String, mode: TranslationMode, deviceLanguages: List<String>): TranslationSettings =
        copy(languageModes = languageModesOr(deviceLanguages) + (language to mode))

    /** 一覧から [language] を外す (「その他の言語」の扱いになる)。 */
    fun withoutLanguage(language: String, deviceLanguages: List<String>): TranslationSettings =
        copy(languageModes = languageModesOr(deviceLanguages) - language)
}

/** ある言語のポストに翻訳をどう出すか。 */
enum class TranslationMode {
    /** 表示したときに自動で訳す。 */
    Automatic,

    /** 「翻訳を表示」を出す (押したら訳す)。 */
    Offer,

    /** 翻訳の表示を出さない。 */
    Hidden,
    ;

    /** 「その他の言語」がこれのとき、一覧に足した言語の最初の扱い (足すのは違う扱いにしたいから)。 */
    val addedLanguageMode: TranslationMode
        get() = when (this) {
            Automatic -> Hidden
            Offer -> Automatic
            Hidden -> Offer
        }
}

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
