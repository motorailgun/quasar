package es.aria_on_the_planet.quasar.domain.model

/** 引用。表示できるのは [state] が [QuoteState.Accepted] で [post] がある場合のみ。 */
data class Quote(
    val state: QuoteState,
    /** 引用元ポスト。取得できない状態や、shallow な表現の場合は `null`。 */
    val post: Post?,
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
