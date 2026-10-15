package es.aria_on_the_planet.quasar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://docs.joinmastodon.org/entities/CustomEmoji/ */
@Serializable
data class CustomEmojiDto(
    val shortcode: String,
    val url: String,
    @SerialName("static_url") val staticUrl: String,
)

/** https://docs.joinmastodon.org/entities/Account/ */
@Serializable
data class AccountDto(
    val id: String,
    val username: String,
    val acct: String,
    val url: String,
    @SerialName("display_name") val displayName: String = "",
    val note: String = "",
    val avatar: String,
    val emojis: List<CustomEmojiDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/Status/ */
@Serializable
data class StatusDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val account: AccountDto,
    val content: String = "",
    @SerialName("replies_count") val repliesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
    val emojis: List<CustomEmojiDto> = emptyList(),
    val reblog: StatusDto? = null,
    // 以下は認証時のみ返される
    val favourited: Boolean? = null,
    val reblogged: Boolean? = null,
    val bookmarked: Boolean? = null,
)
