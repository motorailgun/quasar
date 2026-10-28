package es.aria_on_the_planet.quasar.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReencodeTest {
    private val limits = ImageUploadLimits(maxBytes = 100, acceptedMimeTypes = setOf("image/jpeg", "image/png", "image/webp"))

    @Test
    fun reencodesOnlyOversizedOrUnsupportedImages() {
        assertFalse(needsReencode("image/jpeg", 99, limits))
        // サーバは上限「未満」を受け付ける
        assertTrue(needsReencode("image/jpeg", 100, limits))
        assertTrue(needsReencode("image/heic", 10, limits))
        // 画像以外は触らない
        assertFalse(needsReencode("video/mp4", 1000, limits))
        assertFalse(needsReencode(null, 1000, limits))
        // 形式を返さないサーバでは形式は確かめない
        assertFalse(needsReencode("image/heic", 10, limits.copy(acceptedMimeTypes = null)))
    }

    @Test
    fun format() {
        assertEquals(ReencodeFormat.Webp, reencodeFormat(setOf("image/webp"), hasAlpha = true))
        assertEquals(ReencodeFormat.Jpeg, reencodeFormat(setOf("image/jpeg"), hasAlpha = false))
        assertEquals(ReencodeFormat.Jpeg, reencodeFormat(null, hasAlpha = false))
        // WebP が使えず透過がある画像は作り直さない
        assertNull(reencodeFormat(setOf("image/jpeg", "image/png"), hasAlpha = true))
    }

    @Test
    fun fileName() {
        assertEquals("IMG_0001.webp", reencodedFileName("IMG_0001.HEIC", ReencodeFormat.Webp))
        assertEquals("photo.jpg", reencodedFileName("photo", ReencodeFormat.Jpeg))
        assertEquals("archive.tar.webp", reencodedFileName("archive.tar.png", ReencodeFormat.Webp))
    }

    @Test
    fun animatedPng() {
        assertTrue(isAnimatedPng(png("IHDR", "acTL", "IDAT")))
        assertFalse(isAnimatedPng(png("IHDR", "IDAT", "acTL")))
        assertFalse(isAnimatedPng(png("IHDR", "IDAT")))
        assertFalse(isAnimatedPng(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun profileLimits() {
        assertEquals(
            ImageUploadLimits(2L * 1024 * 1024, setOf("image/jpeg", "image/png", "image/gif", "image/webp")),
            profileImageLimits(setOf("image/jpeg", "image/webp", "image/heic")),
        )
        // WebP を受け付けないサーバ (3.x) では WebP にしない
        assertEquals(setOf("image/jpeg", "image/png", "image/gif"), profileImageLimits(null).acceptedMimeTypes)
    }

    /** 中身 4 バイトのチャンクを並べた PNG。 */
    private fun png(vararg chunks: String): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return chunks.fold(signature) { bytes, type ->
            bytes + byteArrayOf(0, 0, 0, 4) + type.toByteArray(Charsets.US_ASCII) + ByteArray(4) + ByteArray(4)
        }
    }
}
