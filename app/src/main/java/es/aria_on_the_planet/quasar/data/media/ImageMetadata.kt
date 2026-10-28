package es.aria_on_the_planet.quasar.data.media

import java.io.ByteArrayOutputStream

/** 画像の入れ物の形式 (先頭のバイトで見分ける)。 */
enum class ImageContainer { Jpeg, Png, Webp, Gif, Other }

fun imageContainerOf(bytes: ByteArray): ImageContainer = when {
    bytes.startsWith(JpegSoi) -> ImageContainer.Jpeg
    bytes.startsWith(PngSignature) -> ImageContainer.Png
    bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" -> ImageContainer.Webp
    bytes.size >= 6 && (bytes.ascii(0, 6) == "GIF87a" || bytes.ascii(0, 6) == "GIF89a") -> ImageContainer.Gif
    else -> ImageContainer.Other
}

/**
 * 画像から EXIF (位置情報・カメラ・日時等) と XMP・IPTC・テキストのメタデータを除く。画素のデータには触れない
 * (作り直さないので画質は変わらず、APNG・アニメーション WebP も動いたまま)。
 *
 * * JPEG: APP1 (EXIF / XMP)・APP13 (IPTC)・COM を除く。向き ([orientation]、EXIF の Orientation) が 1 以外なら、向きだけの
 *   最小の EXIF を入れ直す (除くと写真が横倒しで表示されるため)。色 (APP2 の ICC プロファイル) は残す。
 * * PNG: `eXIf` と `tEXt` / `zTXt` / `iTXt` (XMP を含む) を除く。
 * * WebP: `EXIF` / `XMP ` のチャンクを除き、`VP8X` の印と RIFF の大きさを合わせる。
 *
 * 除くものが無ければ、または対応しない形式 (GIF はメタデータを持たないので対象外、HEIC 等は作り直すしかない)・壊れている
 * ものは `null`。
 */
fun stripImageMetadata(bytes: ByteArray, orientation: Int = 1): ByteArray? = try {
    when (imageContainerOf(bytes)) {
        ImageContainer.Jpeg -> stripJpeg(bytes, orientation)
        ImageContainer.Png -> stripPng(bytes)
        ImageContainer.Webp -> stripWebp(bytes)
        ImageContainer.Gif, ImageContainer.Other -> null
    }
} catch (e: IndexOutOfBoundsException) {
    // 途中で切れている等
    null
}

private fun stripJpeg(bytes: ByteArray, orientation: Int): ByteArray? {
    val out = ByteArrayOutputStream(bytes.size)
    out.write(bytes, 0, 2)
    var offset = 2
    var removed = false
    var orientationWritten = false
    val writeOrientation = {
        if (!orientationWritten && orientation != 1) out.write(orientationExif(orientation))
        orientationWritten = true
    }
    while (offset < bytes.size) {
        if (bytes[offset] != 0xFF.toByte()) return null
        val marker = bytes[offset + 1].toInt() and 0xFF
        when {
            // 埋め草
            marker == 0xFF -> {
                offset++
                continue
            }
            // 長さを持たない印
            marker == 0x01 || marker in 0xD0..0xD7 -> {
                out.write(bytes, offset, 2)
                offset += 2
                continue
            }
            // 画像データの始まり。ここから先はそのまま
            marker == 0xDA -> {
                writeOrientation()
                out.write(bytes, offset, bytes.size - offset)
                return if (removed) out.toByteArray() else null
            }
        }
        val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
        if (length < 2 || offset + 2 + length > bytes.size) return null
        if (marker == 0xE1 || marker == 0xED || marker == 0xFE) {
            removed = true
        } else {
            // JFIF (APP0) は先頭に置く決まりなので、向きはその後に入れる
            if (marker != 0xE0) writeOrientation()
            out.write(bytes, offset, 2 + length)
        }
        offset += 2 + length
    }
    return null
}

/** 向き (EXIF の Orientation) だけを持つ最小の EXIF (APP1)。ビッグエンディアン、IFD0 に 1 項目。 */
internal fun orientationExif(orientation: Int): ByteArray {
    val payload = byteArrayOf(
        // "Exif\0\0"
        0x45, 0x78, 0x69, 0x66, 0, 0,
        // TIFF のヘッダ: "MM", 42, IFD0 の位置 (8)
        0x4D, 0x4D, 0, 0x2A, 0, 0, 0, 8,
        // 項目の数: 1
        0, 1,
        // Orientation (0x0112), SHORT (3), 個数 1, 値 (2 バイト + 詰め物)
        0x01, 0x12, 0, 3, 0, 0, 0, 1, (orientation shr 8).toByte(), orientation.toByte(), 0, 0,
        // 次の IFD は無い
        0, 0, 0, 0,
    )
    val length = payload.size + 2
    return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
}

private fun stripPng(bytes: ByteArray): ByteArray? {
    val out = ByteArrayOutputStream(bytes.size)
    out.write(bytes, 0, PngSignature.size)
    var offset = PngSignature.size
    var removed = false
    while (offset + 8 <= bytes.size) {
        val length = bytes.intBigEndian(offset)
        if (length < 0 || offset + 12 + length > bytes.size) return null
        val type = bytes.ascii(offset + 4, 4)
        if (type in PngMetadataChunks) {
            removed = true
        } else {
            out.write(bytes, offset, 12 + length)
        }
        offset += 12 + length
        if (type == "IEND") break
    }
    return if (removed) out.toByteArray() else null
}

private fun stripWebp(bytes: ByteArray): ByteArray? {
    val chunks = ByteArrayOutputStream(bytes.size)
    var offset = 12
    var removed = false
    while (offset + 8 <= bytes.size) {
        val type = bytes.ascii(offset, 4)
        val size = bytes.intLittleEndian(offset + 4)
        // 中身は偶数バイトに詰める
        val total = 8 + size + (size and 1)
        if (size < 0 || offset + 8 + size > bytes.size) return null
        when (type) {
            "EXIF", "XMP " -> removed = true
            "VP8X" -> {
                val chunk = bytes.copyOfRange(offset, (offset + total).coerceAtMost(bytes.size))
                // EXIF (0x08)・XMP (0x04) の印を外す
                chunk[8] = (chunk[8].toInt() and 0x08.inv() and 0x04.inv()).toByte()
                chunks.write(chunk)
            }
            else -> chunks.write(bytes, offset, (offset + total).coerceAtMost(bytes.size) - offset)
        }
        offset += total
    }
    if (!removed) return null
    val body = chunks.toByteArray()
    val riffSize = 4 + body.size
    return "RIFF".toByteArray(Charsets.US_ASCII) +
        byteArrayOf(riffSize.toByte(), (riffSize shr 8).toByte(), (riffSize shr 16).toByte(), (riffSize shr 24).toByte()) +
        "WEBP".toByteArray(Charsets.US_ASCII) + body
}

private val JpegSoi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
private val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
private val PngMetadataChunks = setOf("eXIf", "tEXt", "zTXt", "iTXt")

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.ascii(offset: Int, length: Int): String = String(this, offset, length, Charsets.US_ASCII)

private fun ByteArray.intBigEndian(offset: Int): Int =
    ((this[offset].toInt() and 0xFF) shl 24) or ((this[offset + 1].toInt() and 0xFF) shl 16) or
        ((this[offset + 2].toInt() and 0xFF) shl 8) or (this[offset + 3].toInt() and 0xFF)

private fun ByteArray.intLittleEndian(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)
