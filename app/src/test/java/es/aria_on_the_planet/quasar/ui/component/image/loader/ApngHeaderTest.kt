package es.aria_on_the_planet.quasar.ui.component.image.loader

import okio.Buffer
import okio.ByteString.Companion.decodeHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApngHeaderTest {
    @Test
    fun readsApng() {
        val source = png(ihdr(width = 128, height = 64), chunk("acTL", int(12) + int(0)), chunk("IDAT", ByteArray(10)))
        assertEquals(ApngInfo(width = 128, height = 64, frameCount = 12), readApngInfo(source))
        assertEquals(128L * 64 * 4 * 12, readApngInfo(source)?.decodedByteCount)
    }

    @Test
    fun doesNotConsumeSource() {
        val source = png(ihdr(16, 16), chunk("acTL", int(2) + int(0)))
        val size = source.size
        readApngInfo(source)
        assertEquals(size, source.size)
    }

    @Test
    fun skipsChunksBeforeAnimationControl() {
        val source = png(ihdr(16, 16), chunk("tEXt", ByteArray(300)), chunk("acTL", int(3) + int(0)))
        assertEquals(3, readApngInfo(source)?.frameCount)
    }

    @Test
    fun plainPngIsNotApng() {
        assertNull(readApngInfo(png(ihdr(16, 16), chunk("IDAT", ByteArray(10)), chunk("IEND", ByteArray(0)))))
    }

    @Test
    fun animationControlAfterImageDataIsIgnored() {
        // acTL は IDAT より前に無ければならない (後ろにあっても APNG ではない)
        assertNull(readApngInfo(png(ihdr(16, 16), chunk("IDAT", ByteArray(10)), chunk("acTL", int(2) + int(0)))))
    }

    @Test
    fun singleFrameApngIsTreatedAsStatic() {
        assertNull(readApngInfo(png(ihdr(16, 16), chunk("acTL", int(1) + int(0)))))
    }

    @Test
    fun otherFormatsAndTruncatedDataAreNotApng() {
        assertNull(readApngInfo(Buffer().writeUtf8("GIF89a....")))
        assertNull(readApngInfo(Buffer()))
        // シグネチャと IHDR の途中で切れている
        val truncated = png(ihdr(16, 16))
        assertNull(readApngInfo(Buffer().write(truncated.readByteArray(truncated.size - 6))))
    }

    private fun png(vararg chunks: ByteArray) = Buffer().apply {
        write("89504e470d0a1a0a".decodeHex())
        chunks.forEach { write(it) }
    }

    private fun ihdr(width: Int, height: Int) =
        chunk("IHDR", int(width) + int(height) + byteArrayOf(8, 6, 0, 0, 0))

    /** CRC は読まないので 0 で埋める。 */
    private fun chunk(type: String, data: ByteArray) =
        int(data.size) + type.toByteArray(Charsets.US_ASCII) + data + int(0)

    private fun int(value: Int) = Buffer().writeInt(value).readByteArray()
}
