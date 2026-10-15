package es.aria_on_the_planet.quasar.domain.model

/** 本文中の URL から生成されるリンクプレビュー。 */
data class PreviewCard(
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val blurhash: String?,
)
