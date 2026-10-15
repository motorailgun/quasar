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
    val header: String? = null,
    val emojis: List<CustomEmojiDto> = emptyList(),
    val locked: Boolean = false,
    val bot: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("statuses_count") val statusesCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("followers_count") val followersCount: Int = 0,
    val fields: List<FieldDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/Context/ */
@Serializable
data class ContextDto(
    val ancestors: List<StatusDto> = emptyList(),
    val descendants: List<StatusDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/FamiliarFollowers/ */
@Serializable
data class FamiliarFollowersDto(
    val id: String,
    val accounts: List<AccountDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/Account/#Field */
@Serializable
data class FieldDto(
    val name: String,
    val value: String,
    @SerialName("verified_at") val verifiedAt: String? = null,
)

/** https://docs.joinmastodon.org/entities/PreviewCard/ */
@Serializable
data class PreviewCardDto(
    val url: String,
    val title: String = "",
    val description: String = "",
    val image: String? = null,
    val blurhash: String? = null,
)

/** https://docs.joinmastodon.org/entities/MediaAttachment/ */
@Serializable
data class MediaAttachmentDto(
    val id: String,
    val type: String,
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
    val meta: MediaMetaDto? = null,
    val description: String? = null,
    val blurhash: String? = null,
)

/** `meta` はメディア種別やサーバによって中身が異なるため、使うキーだけを緩く受ける。 */
@Serializable
data class MediaMetaDto(
    val original: MediaSizeDto? = null,
    val small: MediaSizeDto? = null,
)

@Serializable
data class MediaSizeDto(
    val width: Double? = null,
    val height: Double? = null,
    val duration: Double? = null,
)

/** https://docs.joinmastodon.org/entities/Status/ */
@Serializable
data class StatusDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val account: AccountDto,
    val content: String = "",
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    @SerialName("replies_count") val repliesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
    val emojis: List<CustomEmojiDto> = emptyList(),
    val reblog: StatusDto? = null,
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
    val card: PreviewCardDto? = null,
    // 以下は認証時のみ返される
    val favourited: Boolean? = null,
    val reblogged: Boolean? = null,
    val bookmarked: Boolean? = null,
)
