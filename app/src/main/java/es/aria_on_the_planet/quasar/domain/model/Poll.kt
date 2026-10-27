package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

data class Poll(
    val id: String,
    /** 終了日時。無期限なら `null`。 */
    val expiresAt: Instant?,
    val expired: Boolean,
    /** 複数選択可。 */
    val multiple: Boolean,
    val votesCount: Int,
    /** 投票した人数。複数選択では [votesCount] (票数) と異なる。 */
    val votersCount: Int?,
    val options: List<PollOption>,
    /** 選択肢に含まれるカスタム絵文字。 */
    val emojis: List<CustomEmoji>,
    /** ログイン中ユーザーが投票済みか。 */
    val voted: Boolean,
    /** ログイン中ユーザーが選んだ選択肢のインデックス。 */
    val ownVotes: List<Int>,
)

data class PollOption(
    val title: String,
    /** 得票数。結果非公開の間は `null`。 */
    val votesCount: Int?,
)
