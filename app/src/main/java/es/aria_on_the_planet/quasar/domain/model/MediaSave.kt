package es.aria_on_the_planet.quasar.domain.model

import java.net.URI

/**
 * 保存先の公開ディレクトリ (端末の共有ストレージの最上位)。その下のアプリ名のディレクトリ ([MediaSaveTarget.subPath]) に置く。
 * MediaProvider は最上位ごとに置ける種類を制限している (Pictures は画像・動画のみ) ので、音声は Music に分ける。
 */
enum class SaveDirectory { Pictures, Music, Downloads }

/**
 * メディアを保存するときの取得元と保存先。
 *
 * @param subPath [directory] の中のパス (アプリのディレクトリ + ファイル名)。
 * @param mimeType 拡張子から分かれば。`null` なら取得時の Content-Type に任せる。
 */
data class MediaSaveTarget(
    val url: String,
    val directory: SaveDirectory,
    val subPath: String,
    val fileName: String,
    val mimeType: String?,
)

/** 保存先のアプリのディレクトリ名。 */
const val MediaSaveFolder = "Quasar"

/**
 * [media] の保存先を決める。取得元は元ファイル ([MediaAttachment.originalUrl]、リモートの原本優先)。
 * ファイル名は `<取得元のホスト>_<メディア ID>.<拡張子>` (メディア ID はサーバ内で一意なので、ホストと合わせて衝突を避ける)。
 * URL が無ければ `null`。
 */
fun mediaSaveTarget(media: MediaAttachment): MediaSaveTarget? {
    val url = media.originalUrl ?: return null
    val uri = runCatching { URI(url) }.getOrNull()
    val extension = uri?.rawPath?.let(::extensionOf) ?: defaultExtension(media.type)
    val host = uri?.host?.takeIf { it.isNotBlank() }
    val baseName = listOfNotNull(host, media.id).joinToString("_").toSafeFileName()
    val fileName = if (extension.isEmpty()) baseName else "$baseName.$extension"
    return MediaSaveTarget(
        url = url,
        directory = when (media.type) {
            MediaType.Image, MediaType.Video, MediaType.Gifv -> SaveDirectory.Pictures
            MediaType.Audio -> SaveDirectory.Music
            MediaType.Unknown -> SaveDirectory.Downloads
        },
        subPath = "$MediaSaveFolder/$fileName",
        fileName = fileName,
        mimeType = MimeTypes[extension],
    )
}

/** パスの最後の要素の拡張子 (小文字)。英数字 1〜5 文字のものだけ認める。 */
private fun extensionOf(path: String): String? {
    val name = path.substringAfterLast('/')
    if ('.' !in name) return null
    return name.substringAfterLast('.').lowercase().takeIf { ExtensionPattern.matches(it) }
}

private fun defaultExtension(type: MediaType) = when (type) {
    MediaType.Image -> "jpg"
    MediaType.Video, MediaType.Gifv -> "mp4"
    MediaType.Audio -> "mp3"
    MediaType.Unknown -> ""
}

/** ファイル名に使えない・紛らわしい文字 (`:` `/` 空白等) を `_` にする。 */
private fun String.toSafeFileName() = replace(UnsafeFileNameChars, "_")

private val ExtensionPattern = Regex("[a-z0-9]{1,5}")
private val UnsafeFileNameChars = Regex("[^A-Za-z0-9._-]")

private val MimeTypes = mapOf(
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "avif" to "image/avif",
    "heic" to "image/heic",
    "mp4" to "video/mp4",
    "m4v" to "video/mp4",
    "webm" to "video/webm",
    "mov" to "video/quicktime",
    "mp3" to "audio/mpeg",
    "m4a" to "audio/mp4",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/ogg",
    "wav" to "audio/wav",
    "flac" to "audio/flac",
    "aac" to "audio/aac",
)
