package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

/** プロフィール画面で表示するアカウントの詳細情報。 */
data class Profile(
    val user: User,
    val headerUrl: String?,
    val postsCount: Int,
    val followingCount: Int,
    val followersCount: Int,
    val fields: List<ProfileField>,
    val createdAt: Instant?,
    val locked: Boolean,
    val bot: Boolean,
)

/** プロフィールの追加情報 (key: value)。いずれもカスタム絵文字を含みうる。 */
data class ProfileField(
    val name: String,
    /** HTML。 */
    val value: String,
    /** リンク先で所有確認が取れた日時。未確認なら `null`。 */
    val verifiedAt: Instant?,
)
