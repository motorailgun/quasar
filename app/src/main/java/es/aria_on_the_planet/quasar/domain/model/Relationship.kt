package es.aria_on_the_planet.quasar.domain.model

/** ログイン中ユーザーとあるアカウントとの関係 (https://docs.joinmastodon.org/entities/Relationship/ の一部)。 */
data class Relationship(
    val following: Boolean,
    /** 自分がフォローリクエストを送って承認待ち (鍵アカウント、またはリモートの応答待ち)。 */
    val requested: Boolean,
    /** 相手からフォローされている。 */
    val followedBy: Boolean,
    /** 相手から自分へのフォローリクエストが承認待ち (Mastodon 4.1+)。 */
    val requestedBy: Boolean = false,
    /** 自分が相手をブロックしている。 */
    val blocking: Boolean = false,
    /** 相手から自分がブロックされている。 */
    val blockedBy: Boolean = false,
) {
    /** 自分 → 相手のフォローの状態。ブロックが絡むとフォローできないので最優先。 */
    val followState: FollowState
        get() = when {
            blocking -> FollowState.Blocking
            blockedBy -> FollowState.BlockedBy
            following -> FollowState.Following
            requested -> FollowState.Requested
            else -> FollowState.NotFollowing
        }
}

enum class FollowState {
    NotFollowing,

    /** フォローリクエストを送信済み (承認待ち)。 */
    Requested,
    Following,

    /** 自分がブロック中。ブロック解除は未実装なのでフォローもできない。 */
    Blocking,

    /** 相手にブロックされている (フォローできない)。 */
    BlockedBy,
}
