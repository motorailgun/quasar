package es.aria_on_the_planet.quasar.ui.component.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentLanguageTest {
    @Test
    fun languageCodes() {
        assertEquals("ja", contentLanguageTag("ja"))
        assertEquals("ja", contentLanguageTag(" ja "))
        assertEquals("zh-TW", contentLanguageTag("zh-tw"))
        // 翻訳のモデルの言語コード (訳文に使う)
        assertEquals("zh-Hans", contentLanguageTag("zh-hans"))
    }

    @Test
    fun missingOrUnreadable() {
        assertNull(contentLanguageTag(null))
        assertNull(contentLanguageTag(""))
        assertNull(contentLanguageTag("  "))
        assertNull(contentLanguageTag("ja,en"))
        assertNull(contentLanguageTag("!!"))
    }
}
