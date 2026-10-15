package es.aria_on_the_planet.quasar.domain.model

enum class MediaType {
    Image,
    Video,
    Gifv,
    Audio,
    Unknown,
}

data class MediaSize(val width: Int, val height: Int) {
    val aspectRatio: Float? get() = if (width > 0 && height > 0) width.toFloat() / height else null
}

data class MediaMeta(
    val original: MediaSize?,
    /** プレビュー画像のサイズ。 */
    val small: MediaSize?,
    val durationSeconds: Double?,
) {
    /** 幅 / 高さ。不明な場合は `null`。 */
    val aspectRatio: Float? get() = small?.aspectRatio ?: original?.aspectRatio
}

data class MediaAttachment(
    val id: String,
    val type: MediaType,
    val url: String,
    val previewUrl: String?,
    val meta: MediaMeta?,
    val blurhash: String?,
    /** 代替テキスト。 */
    val description: String?,
)
