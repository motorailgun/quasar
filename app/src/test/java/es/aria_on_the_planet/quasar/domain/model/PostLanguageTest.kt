package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostLanguageTest {
    @Test
    fun preferredOrderAndDedup() {
        assertEquals(
            listOf("ja", "en", "fr"),
            preferredPostLanguages(selected = "fr", accountDefault = "ja", deviceLanguages = listOf("ja-JP", "en-US", "ja")),
        )
    }

    @Test
    fun preferredWithoutAccountDefaultOrSelection() {
        assertEquals(listOf("en"), preferredPostLanguages(null, null, listOf("en-GB", "und")))
    }

    @Test
    fun codesAreTwoLettersAndModern() {
        val codes = postLanguageCodes()
        assertTrue(codes.all { it.length == 2 })
        assertTrue("ja" in codes && "he" in codes && "id" in codes)
        assertFalse("iw" in codes)
        assertEquals(codes.size, codes.toSet().size)
    }
}
