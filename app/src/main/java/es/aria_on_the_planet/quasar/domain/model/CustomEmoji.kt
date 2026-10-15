package es.aria_on_the_planet.quasar.domain.model

/** サーバ固有のカスタム絵文字。テキスト中では `:shortcode:` として現れる。 */
data class CustomEmoji(
    val shortcode: String,
    val url: String,
    val staticUrl: String,
)
