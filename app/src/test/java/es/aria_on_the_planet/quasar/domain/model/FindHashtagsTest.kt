package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 期待値は Mastodon 4.7.3 の `Tag::HASHTAG_RE` + `Extractor` の手順を Ruby で走らせた結果。 */
class FindHashtagsTest {
    private fun tags(text: String): List<String> = findHashtags(text).map { text.substring(it) }

    private fun check(text: String, vararg expected: String) = assertEquals(text, expected.toList(), tags(text))

    @Test
    fun ascii() {
        check("#tag", "#tag")
        check("hello #tag world", "#tag")
        check("#tag.", "#tag")
        check("#tag-name", "#tag")
        check("#tag's", "#tag")
        check("#tag:blob:", "#tag")
        check("#a", "#a")
        check("#a1", "#a1")
        check("#12a", "#12a")
        check("#café", "#café")
    }

    @Test
    fun onlyAfterSpaceOrStart() {
        check("a#tag")
        check("(#tag)")
        check("@user#tag")
        check("https://example.com/#anchor")
        check("日本語#タグ")
        check("日本語 #タグ", "#タグ")
        check("全角スペース　#タグ", "#タグ")
        check("nbsp #tag", "#tag")
        check("\n#x", "#x")
        check("#tag\n#next", "#tag", "#next")
        check("#tag #tag2", "#tag", "#tag2")
        check("#tag#tag2", "#tag")
        check("##tag")
        check("# tag")
    }

    @Test
    fun needsAlphabetic() {
        check("#123")
        check("#1")
        check("#_")
        check("#😀")
        check("#__a", "#__a")
        check("#Ⅻ", "#Ⅻ")
    }

    @Test
    fun separators() {
        check("#1_2", "#1_2")
        check("#foo_", "#foo_")
        check("#ハッシュ・タグ", "#ハッシュ・タグ")
        check("#タグ・", "#タグ")
        check("#・タグ")
        check("#foo·bar", "#foo·bar")
        check("#1·2", "#1·2")
        check("#a·", "#a")
        check("#a‍b", "#a‍b")
    }

    @Test
    fun unicode() {
        check("#ハッシュタグ", "#ハッシュタグ")
        check("＃全角", "＃全角")
        check("#é", "#é")
        check("#𠀋字", "#𠀋字")
        check("#tag😀", "#tag")
    }

    @Test
    fun trimsSchemeBeforeUrl() {
        check("#taghttps://example.com", "#tag")
        check("#taghttp://example.com", "#tag")
        check("#https://example.com", "#https")
    }

    @Test
    fun ranges() {
        assertEquals(listOf(6..9, 11..15), findHashtags("hello #tag #tag2"))
        assertEquals(emptyList<IntRange>(), findHashtags(""))
    }
}
