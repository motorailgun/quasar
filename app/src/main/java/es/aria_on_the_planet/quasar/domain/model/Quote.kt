package es.aria_on_the_planet.quasar.domain.model

/** 引用。表示できるのは [state] が [QuoteState.Accepted] で [post] がある場合のみ。 */
data class Quote(
    val state: QuoteState,
    /** 引用元ポスト。取得できない状態や、shallow な表現の場合は `null`。 */
    val post: Post?,
    /**
     * 引用元ポストの ID ([post] があればその ID)。引用の引用は API が中身を返さず ID だけ (ShallowQuote) なので、
     * そこから開くのに使う。分からなければ `null`。
     */
    val postId: String? = post?.statusId,
)

/** https://docs.joinmastodon.org/entities/Quote/#state */
enum class QuoteState {
    Pending,
    Accepted,
    Rejected,
    Revoked,
    Deleted,
    Unauthorized,
    BlockedAccount,
    BlockedDomain,
    MutedAccount,
}

/**
 * ログイン中のユーザーがポストを引用できるか (Status の `quote_approval.current_user`、Mastodon 4.5+)。
 * https://docs.joinmastodon.org/entities/Status/#quote_approval
 */
enum class QuotePermission {
    /** すぐ引用になる。 */
    Automatic,
    /** 引用できるが、投稿者の承認待ちになる。 */
    Manual,
    Denied,
    /** 投稿者の方針が分からない (未知の値もこれ)。 */
    Unknown,
}
