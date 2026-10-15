package es.aria_on_the_planet.quasar.ui.component.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BlurHashTest {
    @Test
    fun decodesDcOnlyHashToSolidColor() {
        // 1x1 成分、DC = #FF8000
        val hash = "00" + encode83(0xFF8000, 4)
        val pixels = decodeBlurHash(hash, 4, 4)!!
        pixels.forEach { assertEquals(0xFFFF8000.toInt(), it) }
    }

    @Test
    fun decodesRealHash() {
        val pixels = decodeBlurHash("LEHV6nWB2yk8pyo0adR*.7kCMdnj", 8, 6)
        assertNotNull(pixels)
        assertEquals(48, pixels!!.size)
        pixels.forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test
    fun rejectsInvalidHash() {
        assertNull(decodeBlurHash("", 4, 4))
        assertNull(decodeBlurHash("LEHV6nWB2yk8", 4, 4)) // 長さ不一致
        assertNull(decodeBlurHash("00\"\"\"\"", 4, 4)) // 不正文字
    }

    private fun encode83(value: Int, length: Int): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
        var v = value
        val out = CharArray(length)
        for (i in length - 1 downTo 0) {
            out[i] = chars[v % 83]
            v /= 83
        }
        return String(out)
    }
}
