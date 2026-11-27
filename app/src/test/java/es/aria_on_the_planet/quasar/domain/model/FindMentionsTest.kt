package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 期待値は Mastodon 4.7.3 の `Extractor.extract_mentions_or_lists_with_indices` を Ruby で走らせた結果。 */
class FindMentionsTest {
    private fun mentions(text: String): List<String> = findMentions(text).map { text.substring(it) }

    private fun check(text: String, vararg expected: String) = assertEquals(text, expected.toList(), mentions(text))

    @Test
    fun local() {
        check("@alice", "@alice")
        check("hi @alice!", "@alice")
        check("(@alice)", "@alice")
        check("@alice.", "@alice")
        check("@alice-", "@alice")
        check("@alice_", "@alice_")
        check("@_a", "@_a")
        check("@ALICE", "@ALICE")
        check("@a.b-c_d", "@a.b-c_d")
        check("@a..b", "@a..b")
        check("@alice @bob", "@alice", "@bob")
        check("@alice\n@bob", "@alice", "@bob")
        check("こんにちは @alice さん", "@alice")
    }

    @Test
    fun remote() {
        check("@alice@example.com", "@alice@example.com")
        check("@alice@ex", "@alice@ex")
        check("@alice@ex-", "@alice@ex")
        check("@alice@ex.", "@alice@ex")
        check("@alice@ドメイン.jp", "@alice@ドメイン.jp")
    }

    @Test
    fun notAfterWordCharactersOrUrlParts() {
        check("mail@example.com")
        check("https://example.com/@alice")
        check("a=@alice")
        check("こんにちは@alice")
        check("#tag@alice")
        check("_@alice")
        check("1@alice")
        check("@@alice", "@alice")
    }

    @Test
    fun rejectedByWhatFollows() {
        // 打ちかけのドメイン・全角の @ が続くもの
        check("@alice@")
        check("@alice@.ex")
        check("@alice＠ex")
        check("@alice@b@c")
        // アクセント付きのラテン文字・URL のスキーム
        check("@alicé")
        check("@alice://x")
    }

    @Test
    fun notMentions() {
        check("@")
        check("@.a")
        check("＠alice")
    }

    @Test
    fun ranges() {
        assertEquals(listOf(0 until 6, 7 until 11), findMentions("@alice @bob"))
        assertEquals(listOf(6 until 12), findMentions("こんにちは @alice さん"))
    }
}
