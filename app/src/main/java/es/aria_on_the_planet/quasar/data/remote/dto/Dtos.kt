package es.aria_on_the_planet.quasar.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** https://docs.joinmastodon.org/entities/CustomEmoji/ */
@Serializable
data class CustomEmojiDto(
    val shortcode: String,
    val url: String,
    @SerialName("static_url") val staticUrl: String,
    /** `GET /api/v1/custom_emojis` のときだけ意味がある (ピッカー・補完に出すか)。 */
    @SerialName("visible_in_picker") val visibleInPicker: Boolean = true,
    val category: String? = null,
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
    /** アニメーションしない版 (アニメーションしない画像なら [avatar] と同じ)。 */
    @SerialName("avatar_static") val avatarStatic: String? = null,
    val header: String? = null,
    @SerialName("header_static") val headerStatic: String? = null,
    val emojis: List<CustomEmojiDto> = emptyList(),
    val locked: Boolean = false,
    val bot: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("statuses_count") val statusesCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("followers_count") val followersCount: Int = 0,
    val fields: List<FieldDto> = emptyList(),
    /** verify_credentials (CredentialAccount) のときだけ返る。 */
    val source: AccountSourceDto? = null,
    /** 引っ越し先のアカウント (引っ越し済みのときだけ)。 */
    val moved: AccountDto? = null,
)

/** https://docs.joinmastodon.org/entities/Account/#source */
@Serializable
data class AccountSourceDto(
    val privacy: String? = null,
    val sensitive: Boolean = false,
    /** プロフィール文の原文 (HTML ではない)。 */
    val note: String? = null,
    /** フィールドの原文 (値は HTML ではない)。 */
    val fields: List<FieldDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/Status/#Mention */
@Serializable
data class MentionDto(
    val id: String,
    val username: String,
    val acct: String,
    val url: String = "",
)

/** https://docs.joinmastodon.org/entities/Status/#Tag */
@Serializable
data class StatusTagDto(
    val name: String,
    val url: String = "",
)

/** https://docs.joinmastodon.org/entities/Search/ (ハッシュタグは名前と URL だけ使う) */
@Serializable
data class SearchResultsDto(
    val accounts: List<AccountDto> = emptyList(),
    val statuses: List<StatusDto> = emptyList(),
    val hashtags: List<StatusTagDto> = emptyList(),
)

/** Status.application: https://docs.joinmastodon.org/entities/Status/#application */
@Serializable
data class ApplicationDto(
    val name: String,
    val website: String? = null,
)

/** https://docs.joinmastodon.org/entities/Poll/ */
@Serializable
data class PollDto(
    val id: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    @SerialName("votes_count") val votesCount: Int = 0,
    @SerialName("voters_count") val votersCount: Int? = null,
    val options: List<PollOptionDto> = emptyList(),
    val emojis: List<CustomEmojiDto> = emptyList(),
    val voted: Boolean? = null,
    @SerialName("own_votes") val ownVotes: List<Int>? = null,
)

@Serializable
data class PollOptionDto(
    val title: String,
    @SerialName("votes_count") val votesCount: Int? = null,
)

/**
 * https://docs.joinmastodon.org/entities/Quote/ と https://docs.joinmastodon.org/entities/ShallowQuote/ の両方を受ける。
 * Quote は `quoted_status`、ShallowQuote は `quoted_status_id` を持つ。
 */
@Serializable
data class QuoteDto(
    val state: String,
    @SerialName("quoted_status") val quotedStatus: StatusDto? = null,
    @SerialName("quoted_status_id") val quotedStatusId: String? = null,
)

/** https://docs.joinmastodon.org/entities/Notification/ */
@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    @SerialName("group_key") val groupKey: String? = null,
    @SerialName("created_at") val createdAt: String,
    val account: AccountDto,
    val status: StatusDto? = null,
)

/** https://docs.joinmastodon.org/methods/grouped_notifications/#GroupedNotificationsResults */
@Serializable
data class GroupedNotificationsResultsDto(
    val accounts: List<AccountDto> = emptyList(),
    val statuses: List<StatusDto> = emptyList(),
    @SerialName("notification_groups") val notificationGroups: List<NotificationGroupDto> = emptyList(),
)

/** https://docs.joinmastodon.org/methods/grouped_notifications/#NotificationGroup */
@Serializable
data class NotificationGroupDto(
    @SerialName("group_key") val groupKey: String,
    @SerialName("notifications_count") val notificationsCount: Int = 1,
    val type: String,
    // ドキュメントでは String だが数値で返すサーバがある
    @Serializable(with = StringOrNumberSerializer::class)
    @SerialName("most_recent_notification_id") val mostRecentNotificationId: String,
    @SerialName("page_min_id") val pageMinId: String? = null,
    @SerialName("page_max_id") val pageMaxId: String? = null,
    @SerialName("latest_page_notification_at") val latestPageNotificationAt: String? = null,
    @SerialName("sample_account_ids") val sampleAccountIds: List<String> = emptyList(),
    @SerialName("status_id") val statusId: String? = null,
)

/** https://docs.joinmastodon.org/entities/Instance/ のうち使う部分。 */
@Serializable
data class InstanceDto(
    val configuration: InstanceConfigurationDto? = null,
)

@Serializable
data class InstanceConfigurationDto(
    val urls: InstanceUrlsDto? = null,
    val statuses: InstanceStatusesDto? = null,
    @SerialName("media_attachments") val mediaAttachments: InstanceMediaDto? = null,
    val polls: InstancePollsDto? = null,
    val accounts: InstanceAccountsDto? = null,
)

@Serializable
data class InstanceStatusesDto(
    @SerialName("max_characters") val maxCharacters: Int? = null,
    @SerialName("max_media_attachments") val maxMediaAttachments: Int? = null,
    @SerialName("characters_reserved_per_url") val charactersReservedPerUrl: Int? = null,
)

@Serializable
data class InstanceMediaDto(
    // 新しいサーバではこちらにも載る。無ければ statuses.max_media_attachments を使う
    @SerialName("max_media_attachments") val maxMediaAttachments: Int? = null,
    @SerialName("supported_mime_types") val supportedMimeTypes: List<String>? = null,
    @SerialName("image_size_limit") val imageSizeLimit: Long? = null,
    @SerialName("video_size_limit") val videoSizeLimit: Long? = null,
    @SerialName("description_limit") val descriptionLimit: Int? = null,
)

@Serializable
data class InstancePollsDto(
    @SerialName("max_options") val maxOptions: Int? = null,
    @SerialName("max_characters_per_option") val maxCharactersPerOption: Int? = null,
    @SerialName("min_expiration") val minExpiration: Long? = null,
    @SerialName("max_expiration") val maxExpiration: Long? = null,
)

/** プロフィールの上限。4.x の途中から (無ければ Mastodon の既定値)。 */
@Serializable
data class InstanceAccountsDto(
    @SerialName("max_display_name_length") val maxDisplayNameLength: Int? = null,
    @SerialName("max_note_length") val maxNoteLength: Int? = null,
    @SerialName("max_profile_fields") val maxProfileFields: Int? = null,
    @SerialName("profile_field_name_limit") val profileFieldNameLimit: Int? = null,
    @SerialName("profile_field_value_limit") val profileFieldValueLimit: Int? = null,
)

@Serializable
data class InstanceUrlsDto(
    val streaming: String? = null,
)

/** https://docs.joinmastodon.org/entities/Context/ */
@Serializable
data class ContextDto(
    val ancestors: List<StatusDto> = emptyList(),
    val descendants: List<StatusDto> = emptyList(),
)

/** https://docs.joinmastodon.org/entities/Relationship/ (使う項目のみ) */
@Serializable
data class RelationshipDto(
    val id: String,
    val following: Boolean = false,
    /** フォローリクエストを送って承認待ち (鍵アカウント)。 */
    val requested: Boolean = false,
    @SerialName("followed_by") val followedBy: Boolean = false,
    /** 相手から自分へのフォローリクエストが承認待ち (Mastodon 4.1+)。 */
    @SerialName("requested_by") val requestedBy: Boolean = false,
    val blocking: Boolean = false,
    @SerialName("blocked_by") val blockedBy: Boolean = false,
    val muting: Boolean = false,
    /** フォロー中の相手のブーストをホーム等に出すか (フォローしていなければ意味を持たない)。 */
    @SerialName("showing_reblogs") val showingReblogs: Boolean = true,
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

/** 編集用のポストの原文。https://docs.joinmastodon.org/entities/StatusSource/ */
@Serializable
data class StatusSourceDto(
    val id: String,
    /** 本文 (プレーンテキスト)。 */
    val text: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
)

/** https://docs.joinmastodon.org/entities/Status/ */
@Serializable
data class StatusDto(
    val id: String,
    @SerialName("created_at") val createdAt: String,
    val account: AccountDto,
    /** ActivityPub の ID。 */
    val uri: String? = null,
    /** 元のサーバでの HTML のページ (無いこともある)。 */
    val url: String? = null,
    val content: String = "",
    @SerialName("spoiler_text") val spoilerText: String = "",
    val sensitive: Boolean = false,
    val visibility: String = "public",
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    @SerialName("in_reply_to_account_id") val inReplyToAccountId: String? = null,
    val mentions: List<MentionDto> = emptyList(),
    val tags: List<StatusTagDto> = emptyList(),
    @SerialName("replies_count") val repliesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
    val emojis: List<CustomEmojiDto> = emptyList(),
    val reblog: StatusDto? = null,
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachmentDto> = emptyList(),
    val card: PreviewCardDto? = null,
    val poll: PollDto? = null,
    val quote: QuoteDto? = null,
    val application: ApplicationDto? = null,
    // 以下は認証時のみ返される
    val favourited: Boolean? = null,
    val reblogged: Boolean? = null,
    val bookmarked: Boolean? = null,
)
