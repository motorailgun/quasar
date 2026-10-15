package es.aria_on_the_planet.quasar.data.mapper

import es.aria_on_the_planet.quasar.data.remote.dto.AccountDto
import es.aria_on_the_planet.quasar.data.remote.dto.CustomEmojiDto
import es.aria_on_the_planet.quasar.data.remote.dto.StatusDto
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.domain.model.Post
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

fun StatusDto.toDomain(): Post {
    val boosted = reblog
    val body = boosted ?: this
    return Post(
        id = id,
        author = body.account.toDomain(),
        text = body.content,
        createdAt = Instant.parse(body.createdAt),
        boostsCount = body.reblogsCount,
        reactionsCount = body.favouritesCount,
        emojis = body.emojis.map { it.toDomain() },
        boostedBy = if (boosted != null) account.toDomain() else null,
    )
}

/** プロフィール URL (例: `https://example.com/@alice`) からサーバ URL を取り出す。 */
internal fun serverUrlOf(profileUrl: String): String = runCatching {
    val uri = URI(profileUrl)
    val port = if (uri.port == -1) "" else ":${uri.port}"
    "${uri.scheme}://${uri.host}$port"
}.getOrDefault(profileUrl)
