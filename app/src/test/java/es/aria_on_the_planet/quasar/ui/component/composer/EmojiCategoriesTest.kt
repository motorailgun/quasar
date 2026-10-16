package es.aria_on_the_planet.quasar.ui.component.composer

import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiCategoriesTest {
    private fun emoji(code: String, category: String?) = CustomEmoji(code, "u", "s", category)

    @Test
    fun uncategorizedFirstThenByName() {
        val grouped = groupEmojisByCategory(
            listOf(
                emoji("z1", "zoo"),
                emoji("a1", null),
                emoji("b1", "Blobs"),
                emoji("a2", ""),
                emoji("z2", "zoo"),
                emoji("c1", "animals"),
            ),
        )
        assertEquals(listOf(null, "animals", "Blobs", "zoo"), grouped.map { it.name })
        // 空のカテゴリは分類なし扱い、カテゴリ内はサーバの順
        assertEquals(listOf("a1", "a2"), grouped[0].emojis.map { it.shortcode })
        assertEquals(listOf("z1", "z2"), grouped[3].emojis.map { it.shortcode })
    }

    @Test
    fun empty() {
        assertEquals(emptyList<EmojiCategory>(), groupEmojisByCategory(emptyList()))
    }
}
