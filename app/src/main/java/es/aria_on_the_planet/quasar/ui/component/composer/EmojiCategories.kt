package es.aria_on_the_planet.quasar.ui.component.composer

import es.aria_on_the_planet.quasar.domain.model.CustomEmoji

/** ピッカーの 1 区分。[name] が `null` なら分類なし。 */
data class EmojiCategory(val name: String?, val emojis: List<CustomEmoji>)

/**
 * カテゴリごとにまとめる (Mastodon の Web UI と同じく、分類なしを先頭、残りは名前順)。
 * 各カテゴリ内はサーバの返した順のまま。
 */
fun groupEmojisByCategory(emojis: List<CustomEmoji>): List<EmojiCategory> =
    emojis.groupBy { it.category?.takeIf(String::isNotBlank) }
        .map { (name, list) -> EmojiCategory(name, list) }
        .sortedWith(compareBy<EmojiCategory>({ it.name != null }, { it.name?.lowercase() }))
