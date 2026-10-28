package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileEditTest {
    private val limits = ProfileLimits(
        maxDisplayNameCharacters = 5,
        maxNoteCharacters = 30,
        maxFields = 2,
        maxFieldNameCharacters = 3,
        maxFieldValueCharacters = 4,
    )

    @Test
    fun noProblems() {
        val source = ProfileSource("abcde", "note", listOf(ProfileFieldSource("abc", "abcd"), ProfileFieldSource("", "")))
        assertEquals(emptySet<ProfileProblem>(), source.problems(limits))
    }

    @Test
    fun tooLong() {
        val source = ProfileSource("abcdef", "x".repeat(31), listOf(ProfileFieldSource("abcd", "")))
        assertEquals(
            setOf(ProfileProblem.DisplayNameTooLong, ProfileProblem.NoteTooLong, ProfileProblem.FieldTooLong),
            source.problems(limits),
        )
    }

    @Test
    fun emptyFieldsDoNotCount() {
        val fields = listOf(ProfileFieldSource("a", ""), ProfileFieldSource("", ""), ProfileFieldSource("", "b"))
        assertEquals(emptySet<ProfileProblem>(), ProfileSource("", "", fields).problems(limits))
        assertEquals(
            setOf(ProfileProblem.TooManyFields),
            ProfileSource("", "", fields + ProfileFieldSource("c", "")).problems(limits),
        )
    }

    @Test
    fun noteCountsUrlsAsFixedLength() {
        // URL は長さに関わらず 23 文字 (サーバの NoteLengthValidator と同じ)
        val note = "see https://example.invalid/" + "a".repeat(100)
        assertEquals(4 + 23, countNoteCharacters(note))
    }

    @Test
    fun counting() {
        // 表示名はコードポイント、フィールドは書記素
        assertEquals(2, countDisplayNameCharacters("👍🏽"))
        assertEquals(1, countFieldCharacters("👍🏽"))
        assertEquals(0, countFieldCharacters(""))
    }
}
