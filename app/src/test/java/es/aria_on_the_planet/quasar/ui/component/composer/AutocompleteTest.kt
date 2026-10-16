package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.ui.text.TextRange
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutocompleteTest {
    /** `|` の位置をカーソルとして [findAutocompleteQuery] を呼ぶ。 */
    private fun queryAt(marked: String): AutocompleteQuery? {
        val cursor = marked.indexOf('|')
        return findAutocompleteQuery(marked.removeRange(cursor, cursor + 1), TextRange(cursor))
    }

    @Test
    fun findsQueryBeforeCursor() {
        assertEquals(AutocompleteQuery(':', "bl", TextRange(0, 3)), queryAt(":bl|"))
        assertEquals(AutocompleteQuery(':', "blob_cat", TextRange(6, 15)), queryAt("hello :blob_cat| world"))
        assertEquals(AutocompleteQuery(':', "ab", TextRange(3, 6)), queryAt("x\n\n:ab|"))
    }

    @Test
    fun acceptsFullWidthColon() {
        assertEquals(AutocompleteQuery(':', "neko", TextRange(4, 9)), queryAt("ab. ：neko|"))
        assertEquals(AutocompleteQuery(':', "neko", TextRange(0, 5)), queryAt("：neko|"))
    }

    @Test
    fun requiresTwoCharacters() {
        assertNull(queryAt(":|"))
        assertNull(queryAt(":a|"))
    }

    @Test
    fun ignoresColonsInsideWords() {
        assertNull(queryAt("12:30|"))
        assertNull(queryAt("a:bc|"))
        assertNull(queryAt("あ:bc|"))
        assertNull(queryAt("::bc|"))
    }

    @Test
    fun ignoresClosedShortcodesAndMidWord() {
        assertNull(queryAt(":blob:|"))
        assertNull(queryAt(":bl|ob"))
    }

    @Test
    fun ignoresRangeSelection() {
        assertNull(findAutocompleteQuery(":blob", TextRange(1, 5)))
    }

    private fun emoji(code: String) = CustomEmoji(code, "u/$code", "s/$code")

    @Test
    fun ranksPrefixBeforeContains() {
        val emojis = listOf("ablobcat", "blobcat_happy", "blob", "BlobFox", "cat").map(::emoji)
        assertEquals(
            listOf("blob", "BlobFox", "blobcat_happy", "ablobcat"),
            rankEmojis(emojis, "BLOB").map { it.shortcode },
        )
        assertEquals(listOf("blob"), rankEmojis(emojis, "blob", limit = 1).map { it.shortcode })
    }

    @Test
    fun insertsShortcodeWithTrailingSpace() {
        assertEquals("hi :blob: " to 10, insertEmoji("hi :bl", TextRange(3, 6), "blob"))
        // 後ろに空白があれば足さず、その空白の後ろへ
        assertEquals(":blob: x" to 7, insertEmoji(":bl x", TextRange(0, 3), "blob"))
        assertEquals(":blob: x" to 7, insertEmoji("：bl x", TextRange(0, 3), "blob"))
    }

    @Test
    fun findsOnlyKnownShortcodesWithServerBoundaries() {
        val known = setOf("blob", "cat")
        assertEquals(listOf(0..5, 7..11), findEmojiShortcodes(":blob: :cat:", known))
        // サーバと同じく、コロンで隣り合うものはどちらも絵文字にならない
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes(":blob::cat:", known))
        assertEquals(listOf(0..5, 7..11), findEmojiShortcodes(":blob:\n:cat:", known))
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes(":unknown:", known))
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes("a:blob:", known))
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes("あ:blob:", known))
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes(":blob:b", known))
        assertEquals(listOf(1..6), findEmojiShortcodes("(:blob:)", known))
        assertEquals(emptyList<IntRange>(), findEmojiShortcodes(":blob:", emptySet()))
    }

    @Test
    fun insertsAtCursorWithSpacesWhereNeeded() {
        assertEquals(":blob: " to 7, insertEmojiAtCursor("", TextRange(0), "blob"))
        // 直前が文字なら前に空白を足す
        assertEquals("hi :blob: " to 10, insertEmojiAtCursor("hi", TextRange(2), "blob"))
        assertEquals("あ :blob: " to 9, insertEmojiAtCursor("あ", TextRange(1), "blob"))
        // 直前が空白なら足さない。後ろに空白があれば足さずにその後ろへ
        assertEquals("a :blob: b" to 9, insertEmojiAtCursor("a  b", TextRange(2), "blob"))
        // 範囲選択は置き換える
        assertEquals("x :blob: z" to 9, insertEmojiAtCursor("x yy z", TextRange(2, 4), "blob"))
    }
}
