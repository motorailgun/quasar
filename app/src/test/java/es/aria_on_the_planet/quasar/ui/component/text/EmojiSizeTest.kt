package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiSizeTest {
    @Test
    fun aspectRatioFromImageSize() {
        assertEquals(1f, emojiAspectRatio(128f, 128f)!!, 0f)
        assertEquals(4f, emojiAspectRatio(512f, 128f)!!, 0f)
        // 極端な比率は 1/4〜8 に丸める
        assertEquals(8f, emojiAspectRatio(2000f, 100f)!!, 0f)
        assertEquals(0.25f, emojiAspectRatio(10f, 100f)!!, 0f)
    }

    @Test
    fun unknownSizeIsNull() {
        assertNull(emojiAspectRatio(Float.NaN, 10f))
        assertNull(emojiAspectRatio(10f, 0f))
        assertNull(emojiAspectRatio(Float.POSITIVE_INFINITY, 10f))
    }

    @Test
    fun heightFollowsLineHeight() {
        assertEquals(20.sp, emojiHeight(TextStyle(fontSize = 14.sp, lineHeight = 20.sp)))
        assertEquals(1.2.em, emojiHeight(TextStyle(fontSize = 14.sp)))
    }
}
