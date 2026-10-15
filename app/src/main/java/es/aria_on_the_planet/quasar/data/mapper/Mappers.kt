package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.CustomEmojiDto
import es.aria_on_the_planet.quasar.data.remote.dto.FieldDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaAttachmentDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaMetaDto
import es.aria_on_the_planet.quasar.data.remote.dto.MediaSizeDto
import es.aria_on_the_planet.quasar.data.remote.dto.PollDto
import es.aria_on_the_planet.quasar.data.remote.dto.PreviewCardDto
import es.aria_on_the_planet.quasar.data.remote.dto.QuoteDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.MediaMeta
import es.aria_on_the_planet.quasar.domain.model.MediaSize
import es.aria_on_the_planet.quasar.domain.model.MediaType
import es.aria_on_the_planet.quasar.domain.model.Poll
import es.aria_on_the_planet.quasar.domain.model.PollOption
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostApplication
import es.aria_on_the_planet.quasar.domain.model.PreviewCard
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.ProfileField
import es.aria_on_the_planet.quasar.domain.model.Quote
import es.aria_on_the_planet.quasar.domain.model.QuoteState
import es.aria_on_the_planet.quasar.domain.model.PostViewerState
import es.aria_on_the_planet.quasar.domain.model.User
import java.net.URI
import java.time.Instant

fun CustomEmojiDto.toDomain() = CustomEmoji(
    shortcode = shortcode,
    url = url,
    staticUrl = staticUrl,
)

fun AccountDto.toDomain() = User(
    inServerId = id,
    screenName = acct,
    displayName = displayName,
    serverUrl = serverUrlOf(url),
    iconUrl = avatar,
    note = note,
    emojis = emojis.map { it.toDomain() },
)

fun AccountDto.toProfile() = Profile(
    user = toDomain(),
    // Mastodon は未設定時に missing.png を返すので、それはヘッダ無しとして扱う
    headerUrl = header?.takeUnless { it.isBlank() || it.endsWith("/missing.png") },
    postsCount = statusesCount,
    followingCount = followingCount,
    followersCount = followersCount,
    fields = fields.map { it.toDomain() },
    createdAt = createdAt?.let { parseInstantOrNull(it) },
    locked = locked,
    bot = bot,
)

fun FieldDto.toDomain() = ProfileField(
    name = name,
    value = value,
    verifiedAt = verifiedAt?.let { parseInstantOrNull(it) },
)

private fun parseInstantOrNull(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

/**
 * リプライ先アカウントの acct。API は ID しか返さないので、自分自身へのリプライなら投稿者、
 * それ以外はメンションから同じ ID のものを探す。
 */
private fun StatusDto.replyTargetAcct(): String? {
    val targetId = inReplyToAccountId ?: return null
    if (targetId == account.id) return account.acct
    return mentions.firstOrNull { it.id == targetId }?.acct
}

fun StatusDto.toDomain(): Post {
    val boosted = reblog
    val body = boosted ?: this
    return Post(
        id = id,
        author = body.account.toDomain(),
        text = body.content,
        createdAt = Instant.parse(body.createdAt),
        repliesCount = body.repliesCount,
        boostsCount = body.reblogsCount,
        reactionsCount = body.favouritesCount,
        emojis = body.emojis.map { it.toDomain() },
        mediaAttachments = body.mediaAttachments.map { it.toDomain() },
        card = body.card?.toDomain(),
        poll = body.poll?.toDomain(),
        quote = body.quote?.toDomain(),
        contentWarning = body.spoilerText.takeIf { it.isNotBlank() },
        sensitive = body.sensitive,
        application = body.application?.let { PostApplication(it.name, it.website?.takeIf(String::isNotBlank)) },
        viewer = PostViewerState(
            bookmarked = body.bookmarked == true,
            reblogged = body.reblogged == true,
            favourited = body.favourited == true,
        ),
        boostedBy = if (boosted != null) account.toDomain() else null,
        statusId = body.id,
        inReplyToId = body.inReplyToId,
        inReplyToAcct = body.replyTargetAcct(),
    )
}

fun PollDto.toDomain() = Poll(
    id = id,
    expiresAt = expiresAt?.let { parseInstantOrNull(it) },
    expired = expired,
    multiple = multiple,
    votesCount = votesCount,
    votersCount = votersCount,
    options = options.map { PollOption(it.title, it.votesCount) },
    emojis = emojis.map { it.toDomain() },
    voted = voted == true,
    ownVotes = ownVotes.orEmpty(),
)

fun QuoteDto.toDomain() = Quote(
    state = when (state) {
        "pending" -> QuoteState.Pending
        "accepted" -> QuoteState.Accepted
        "rejected" -> QuoteState.Rejected
        "revoked" -> QuoteState.Revoked
        "deleted" -> QuoteState.Deleted
        "blocked_account" -> QuoteState.BlockedAccount
        "blocked_domain" -> QuoteState.BlockedDomain
        "muted_account" -> QuoteState.MutedAccount
        // 未知の値は unauthorized として扱う (API ドキュメントの指示)
        else -> QuoteState.Unauthorized
    },
    post = quotedStatus?.toDomain(),
)

fun PreviewCardDto.toDomain() = PreviewCard(
    url = url,
    title = title,
    description = description,
    imageUrl = image,
    blurhash = blurhash,
)

fun MediaAttachmentDto.toDomain() = MediaAttachment(
    id = id,
    type = when (type) {
        "image" -> MediaType.Image
        "video" -> MediaType.Video
        "gifv" -> MediaType.Gifv
        "audio" -> MediaType.Audio
        else -> MediaType.Unknown
    },
    url = url ?: remoteUrl ?: previewUrl.orEmpty(),
    previewUrl = previewUrl,
    meta = meta?.toDomain(),
    blurhash = blurhash,
    description = description?.takeIf { it.isNotBlank() },
)

private fun MediaMetaDto.toDomain() = MediaMeta(
    original = original?.toSize(),
    small = small?.toSize(),
    durationSeconds = original?.duration,
)

private fun MediaSizeDto.toSize(): MediaSize? {
    val w = width ?: return null
    val h = height ?: return null
    return MediaSize(w.toInt(), h.toInt())
}

/** プロフィール URL (例: `https://example.com/@alice`) からサーバ URL を取り出す。 */
internal fun serverUrlOf(profileUrl: String): String = runCatching {
    val uri = URI(profileUrl)
    val port = if (uri.port == -1) "" else ":${uri.port}"
    "${uri.scheme}://${uri.host}$port"
}.getOrDefault(profileUrl)
