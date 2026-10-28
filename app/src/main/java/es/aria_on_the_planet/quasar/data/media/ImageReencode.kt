package es.aria_on_the_planet.quasar.data.media

import es.aria_on_the_planet.quasar.domain.model.ComposeLimits

/**
 * 送る画像の上限。[maxBytes] を超える、または [acceptedMimeTypes] に無い形式の画像は端末で作り直す ([ImageReencoder])。
 * [acceptedMimeTypes] が `null` ならサーバが返さなかったので形式は確かめない。
 */
data class ImageUploadLimits(val maxBytes: Long, val acceptedMimeTypes: Set<String>?)

/** 投稿の添付の画像の上限 (`configuration.media_attachments`)。 */
fun ComposeLimits.toImageUploadLimits(): ImageUploadLimits = ImageUploadLimits(imageSizeLimit, supportedMimeTypes)

/**
 * 送る前の画像の手直しの選択。[compressOversized] は上限に合わない画像を作り直す、[stripMetadata] は EXIF 等を除く。
 */
data class ImagePrepareOptions(val compressOversized: Boolean, val stripMetadata: Boolean)

/** 作り直すときの形式。 */
enum class ReencodeFormat(val mimeType: String, val extension: String) {
    Webp("image/webp", "webp"),
    Jpeg("image/jpeg", "jpg"),
}

/**
 * 作り直すか。画像で、上限のサイズに収まらないか、受け付けない形式なら作り直す (大きさ (縦横) は変えない)。
 * サーバは上限「未満」を受け付ける (`validates_attachment_size less_than:`)。
 */
fun needsReencode(mimeType: String?, size: Long, limits: ImageUploadLimits): Boolean {
    if (mimeType?.startsWith("image/") != true) return false
    val accepted = limits.acceptedMimeTypes
    return !fitsSize(size, limits) || (accepted != null && mimeType !in accepted)
}

/** [size] バイトが上限に収まるか (上限ちょうどは収まらない)。 */
fun fitsSize(size: Long, limits: ImageUploadLimits): Boolean = size < limits.maxBytes

/**
 * 作り直す形式。サーバが WebP を受け付けるなら WebP (透過も残る)、そうでなければ JPEG (どのサーバも受け付ける)。
 * WebP が使えず透過がある画像は `null` (JPEG にすると透過が消え、PNG のままでは縮まないので作り直さない)。
 */
fun reencodeFormat(acceptedMimeTypes: Set<String>?, hasAlpha: Boolean): ReencodeFormat? = when {
    acceptedMimeTypes?.contains(ReencodeFormat.Webp.mimeType) == true -> ReencodeFormat.Webp
    !hasAlpha -> ReencodeFormat.Jpeg
    else -> null
}

/** 作り直すときに試す画質 (高い順)。上限に収まった最初のものを使う。 */
val ReencodeQualities = listOf(90, 80, 70, 60)

/** 作り直した画像のファイル名 (拡張子を [format] のものに替える)。 */
fun reencodedFileName(original: String, format: ReencodeFormat): String =
    original.substringBeforeLast('.', original).ifEmpty { "image" } + "." + format.extension

/**
 * アニメーションする PNG (APNG) か。最初の `IDAT` より前に `acTL` があれば APNG (Android の ImageDecoder は APNG を
 * 1 枚の画像として読むので、作り直すとアニメーションが消える)。
 */
fun isAnimatedPng(bytes: ByteArray): Boolean {
    if (bytes.size < PngSignature.size || !PngSignature.indices.all { bytes[it] == PngSignature[it] }) return false
    var offset = PngSignature.size
    while (offset + 8 <= bytes.size) {
        val length = ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
        if (type == "acTL") return true
        if (type == "IDAT" || length < 0) return false
        // 長さ + 種類 + 中身 + CRC
        offset += 12 + length
    }
    return false
}

/**
 * プロフィールの画像 (アイコン・ヘッダ) の上限。サーバは API で返さないので Mastodon のソースから:
 * サイズは 3.x が 2MB、4.x が 8MB なので小さい方。形式は JPEG / PNG / GIF と、4.x なら WebP
 * (投稿の添付で WebP を受け付けるサーバ ([attachmentMimeTypes]) なら 4.x とみなす)。
 */
fun profileImageLimits(attachmentMimeTypes: Set<String>?): ImageUploadLimits = ImageUploadLimits(
    maxBytes = ProfileImageMaxBytes,
    acceptedMimeTypes = ProfileImageMimeTypes +
        if (attachmentMimeTypes?.contains(ReencodeFormat.Webp.mimeType) == true) setOf(ReencodeFormat.Webp.mimeType) else emptySet(),
)

private const val ProfileImageMaxBytes = 2L * 1024 * 1024
private val ProfileImageMimeTypes = setOf("image/jpeg", "image/png", "image/gif")

private val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
