package es.aria_on_the_planet.quasar.ui.component.image.loader

import okio.BufferedSource
import okio.ByteString.Companion.decodeHex
import okio.IOException

/** APNG のヘッダから読んだ情報。 */
internal data class ApngInfo(val width: Int, val height: Int, val frameCount: Int) {
    /** 全フレームを展開したときのおおよそのバイト数 (ARGB_8888)。apng-drawable は全フレームを先に展開する。 */
    val decodedByteCount: Long get() = width.toLong() * height * BytesPerPixel * frameCount
}

/**
 * [source] が 2 フレーム以上の APNG ならその情報、そうでなければ `null`。[source] は消費しない (peek で読む)。
 *
 * APNG はチャンクの並びで判定する: `acTL` (アニメーション制御) が最初の `IDAT` (画像データ) より前にあれば APNG。
 * URL の拡張子や Content-Type は普通の PNG と同じなので当てにしない。
 */
internal fun readApngInfo(source: BufferedSource): ApngInfo? {
    val peek = source.peek()
    try {
        if (peek.readByteString(PngSignature.size.toLong()) != PngSignature) return null
        var width = 0
        var height = 0
        var scanned = PngSignature.size.toLong()
        while (scanned < MaxHeaderScanBytes) {
            val length = peek.readInt().toLong() and 0xFFFFFFFFL
            when (peek.readUtf8(4)) {
                "IHDR" -> {
                    width = peek.readInt()
                    height = peek.readInt()
                    peek.skip(length - 8)
                }
                "acTL" -> {
                    val frameCount = peek.readInt()
                    return if (width > 0 && height > 0 && frameCount > 1) ApngInfo(width, height, frameCount) else null
                }
                "IDAT", "IEND" -> return null
                else -> peek.skip(length)
            }
            peek.skip(4) // CRC
            scanned += ChunkOverheadBytes + length
        }
        return null
    } catch (_: IOException) {
        // 途中で切れている (EOFException 等)
        return null
    }
}

private val PngSignature = "89504e470d0a1a0a".decodeHex()

/** 長さ・種類・CRC の分。 */
private const val ChunkOverheadBytes = 12

/** `acTL` を探すのはこの範囲まで (普通は先頭の数十バイトにある)。巨大なメタデータで読み続けないため。 */
private const val MaxHeaderScanBytes = 256L * 1024

private const val BytesPerPixel = 4
