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
    /** 元サイズのファイル (url)。処理中は `null`。リモートのメディアでは接続中サーバのキャッシュ。 */
    val url: String?,
    /** リモートサーバ上の元ファイル (remote_url)。ローカルのメディアでは `null`。 */
    val remoteUrl: String?,
    val previewUrl: String?,
    val meta: MediaMeta?,
    val blurhash: String?,
    /** 代替テキスト。 */
    val description: String?,
) {
    /** 表示・再生に使う URL の候補。先頭から試し、読み込みに失敗したら次を使う。 */
    val viewUrls: List<String> get() = listOfNotNull(url, remoteUrl).distinct()

    /** 元のファイル。保存やブラウザで開くときに使う (リモートの原本を優先)。 */
    val originalUrl: String? get() = remoteUrl ?: url
}
