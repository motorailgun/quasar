package es.aria_on_the_planet.quasar.domain.model

import java.time.Instant

data class Post(
    /** Status ID。ブーストの場合は外側 (ブースト自体) の ID で、ページングに使う。 */
    val id: String,
    val author: User,
    /** 本文 (HTML)。カスタム絵文字を含みうる。 */
    val text: String,
    val createdAt: Instant,
    val boostsCount: Int,
    val reactionsCount: Int,
    val emojis: List<CustomEmoji>,
    /** ブーストされたポストの場合、ブーストしたユーザー。 */
    val boostedBy: User? = null,
)
