package es.aria_on_the_planet.quasar.data.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageMetadataTest {
    // ---- JPEG ----

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val app0 = segment(0xE0, "JFIF\u0000".toByteArray() + ByteArray(9))
    private val exif = segment(0xE1, "Exif\u0000\u0000".toByteArray() + ByteArray(20) { 7 })
    private val xmp = segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000".toByteArray() + ByteArray(10) { 3 })
    private val iptc = segment(0xED, "Photoshop 3.0\u0000".toByteArray())
    private val comment = segment(0xFE, "secret".toByteArray())
    private val icc = segment(0xE2, "ICC_PROFILE\u0000".toByteArray() + ByteArray(8) { 1 })
    private val dqt = segment(0xDB, ByteArray(65) { 2 })
    // SOS から先 (画像データ + EOI) はそのまま残るはず
    private val scan = segment(0xDA, ByteArray(10) { 4 }) + byteArrayOf(9, 8, 0xFF.toByte(), 0, 7) +
        byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    @Test
    fun jpegRemovesMetadataAndKeepsTheRest() {
        val jpeg = soi + app0 + exif + xmp + iptc + comment + icc + dqt + scan
        assertArrayEquals(soi + app0 + icc + dqt + scan, stripImageMetadata(jpeg))
    }

    @Test
    fun jpegKeepsOrientation() {
        val jpeg = soi + app0 + exif + dqt + scan
        // 向きだけの EXIF を JFIF (APP0) の後に入れ直す
        assertArrayEquals(soi + app0 + orientationExif(6) + dqt + scan, stripImageMetadata(jpeg, orientation = 6))
        // JFIF が無ければ先頭に
        assertArrayEquals(soi + orientationExif(6) + dqt + scan, stripImageMetadata(soi + exif + dqt + scan, orientation = 6))
    }

    @Test
    fun orientationExifLayout() {
        val segment = orientationExif(8)
        assertEquals(0xFF.toByte(), segment[0])
        assertEquals(0xE1.toByte(), segment[1])
        // 長さは印の 2 バイトを除いた分
        assertEquals(segment.size - 2, ((segment[2].toInt() and 0xFF) shl 8) or (segment[3].toInt() and 0xFF))
        assertEquals("Exif", String(segment, 4, 4))
        // 値 (Orientation = 8) の位置: APP1 の頭 4 + "Exif\0\0" 6 + TIFF ヘッダ 8 + 個数 2 + タグ・型・個数 8
        assertEquals(8, segment[4 + 6 + 8 + 2 + 8 + 1].toInt())
    }

    @Test
    fun jpegWithoutMetadataIsLeftAlone() {
        assertNull(stripImageMetadata(soi + app0 + dqt + scan))
    }

    @Test
    fun brokenJpeg() {
        // 長さが中身を超えている
        assertNull(stripImageMetadata(soi + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x10, 0)))
    }

    // ---- PNG ----

    private fun chunk(type: String, payload: ByteArray = ByteArray(4)): ByteArray {
        val length = payload.size
        return byteArrayOf((length shr 24).toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte()) +
            type.toByteArray(Charsets.US_ASCII) + payload + ByteArray(4)
    }

    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    @Test
    fun pngRemovesTextAndExifChunks() {
        val png = pngSignature + chunk("IHDR") + chunk("eXIf") + chunk("tEXt") + chunk("iTXt") + chunk("zTXt") +
            chunk("acTL") + chunk("IDAT") + chunk("IEND", ByteArray(0))
        // APNG の印 (acTL) 等は残す
        assertArrayEquals(
            pngSignature + chunk("IHDR") + chunk("acTL") + chunk("IDAT") + chunk("IEND", ByteArray(0)),
            stripImageMetadata(png),
        )
        assertNull(stripImageMetadata(pngSignature + chunk("IHDR") + chunk("IDAT") + chunk("IEND", ByteArray(0))))
    }

    // ---- WebP ----

    private fun webpChunk(type: String, payload: ByteArray): ByteArray {
        val size = payload.size
        val padding = if (size % 2 == 1) ByteArray(1) else ByteArray(0)
        return type.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) + payload + padding
    }

    private fun riff(body: ByteArray): ByteArray {
        val size = body.size + 4
        return "RIFF".toByteArray() + byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) +
            "WEBP".toByteArray() + body
    }

    @Test
    fun webpRemovesExifAndXmpAndClearsFlags() {
        val vp8x = { flags: Int -> webpChunk("VP8X", byteArrayOf(flags.toByte()) + ByteArray(9)) }
        val image = webpChunk("VP8L", ByteArray(5) { 6 })
        val webp = riff(vp8x(0x08 or 0x04 or 0x10) + image + webpChunk("EXIF", ByteArray(7)) + webpChunk("XMP ", ByteArray(4)))
        // 透過 (0x10) の印は残す
        assertArrayEquals(riff(vp8x(0x10) + image), stripImageMetadata(webp))
        assertNull(stripImageMetadata(riff(vp8x(0x10) + image)))
    }

    @Test
    fun otherFormats() {
        assertEquals(ImageContainer.Gif, imageContainerOf("GIF89a".toByteArray() + ByteArray(4)))
        assertNull(stripImageMetadata("GIF89a".toByteArray() + ByteArray(4)))
        assertEquals(ImageContainer.Other, imageContainerOf(ByteArray(12)))
        assertNull(stripImageMetadata(ByteArray(12)))
    }
}
