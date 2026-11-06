package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

data class Post(
    /** Status ID。ブーストの場合は外側 (ブースト自体) の ID で、ページングに使う。 */
    val id: String,
    val author: User,
    /** 本文 (HTML)。カスタム絵文字を含みうる。 */
    val text: String,
    val createdAt: Instant,
    val repliesCount: Int,
    val boostsCount: Int,
    val reactionsCount: Int,
    val emojis: List<CustomEmoji>,
    /** 添付メディア (0〜15 件)。 */
    val mediaAttachments: List<MediaAttachment> = emptyList(),
    /** 本文中の URL のリンクプレビュー。 */
    val card: PreviewCard? = null,
    val poll: Poll? = null,
    /** 引用しているポスト。 */
    val quote: Quote? = null,
    /**
     * ログイン中のユーザーがこのポストを引用できるか (`quote_approval.current_user`)。`null` は返らなかった
     * (引用に対応していないサーバ。Mastodon 4.5 未満)。
     */
    val quotePermission: QuotePermission? = null,
    /** 投稿に使われたアプリ。他サーバからのポストでは通常 `null`。 */
    val application: PostApplication? = null,
    /** Content Warning (spoiler_text)。無ければ `null`。カスタム絵文字は [emojis] を使う。 */
    val contentWarning: String? = null,
    /** メディアが閲覧注意 (NSFW) か。 */
    val sensitive: Boolean = false,
    /** 公開範囲。 */
    val visibility: PostVisibility = PostVisibility.Public,
    /** ログイン中ユーザーがこのポストに対して行った操作。 */
    val viewer: PostViewerState = PostViewerState(),
    /** ブーストされたポストの場合、ブーストしたユーザー。 */
    val boostedBy: User? = null,
    /** 本文を持つ Status の ID。ブーストの場合は中身の ID で、スレッド表示などに使う。 */
    val statusId: String = id,
    /** リプライ先の Status ID。リプライでなければ `null`。 */
    val inReplyToId: String? = null,
    /** リプライ先アカウントの acct (`username` または `username@domain`)。特定できなければ `null`。 */
    val inReplyToAcct: String? = null,
    /** 本文でメンションしているアカウント (mentions)。返信の宛先・本文のメンションのタップに使う。 */
    val mentions: List<Mention> = emptyList(),
    /** 本文のハッシュタグ (tags)。本文のハッシュタグのタップに使う。 */
    val tags: List<Hashtag> = emptyList(),
    /** 元のサーバでのページ (`url`、無ければ `uri`)。 */
    val url: String? = null,
    /** 本文の言語 (`language`、ISO 639 / BCP 47。投稿者が選んだもの)。無いことがある。 */
    val language: String? = null,
)

/**
 * 引用して投稿できるか。サーバが引用できると答えたもの (承認待ちになるものを含む) だけ。方針が分からない・
 * サーバが引用に対応していない場合は不可 (Mastodon Web と同じ)。
 */
val Post.quotable: Boolean
    get() = quotePermission == QuotePermission.Automatic || quotePermission == QuotePermission.Manual

/** ポストの公開範囲 (visibility)。 */
enum class PostVisibility {
    Public,
    /** 未収載 (Mastodon の UI では「ひかえめな公開」)。 */
    Unlisted,
    /** フォロワー限定。 */
    Private,
    /** 言及した相手のみ。 */
    Direct;

    /**
     * ブーストできる公開範囲か。フォロワー限定は投稿者自身ならブーストできるが、
     * ログイン中のアカウントをまだ把握していないので一律に不可として扱う。
     */
    val boostable: Boolean
        get() = this == Public || this == Unlisted
}

/** 投稿に使われたアプリ (User Agent)。 */
data class PostApplication(
    val name: String,
    val website: String?,
)

/** ログイン中ユーザーから見たポストの状態。 */
data class PostViewerState(
    val bookmarked: Boolean = false,
    val reblogged: Boolean = false,
    val favourited: Boolean = false,
)
