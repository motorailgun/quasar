package es.aria_on_the_planet.quasar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionDiffTest {
    /** 区間を「=x」「-x」「+x」の文字列にする (読みやすく比べるため)。 */
    private fun render(old: String, new: String): List<String> = diffText(old, new).map { segment ->
        when (segment.kind) {
            DiffKind.Equal -> "=" + new.substring(segment.newRange)
            DiffKind.Inserted -> "+" + new.substring(segment.newRange)
            DiffKind.Deleted -> "-" + old.substring(segment.oldRange)
        }
    }

    /** Equal + Inserted をつなぐと今の版、Equal + Deleted をつなぐと前の版に戻る。 */
    private fun assertReconstructs(old: String, new: String) {
        val segments = diffText(old, new)
        assertEquals(new, segments.filter { it.kind != DiffKind.Deleted }.joinToString("") { new.substring(it.newRange) })
        assertEquals(old, segments.filter { it.kind != DiffKind.Inserted }.joinToString("") { old.substring(it.oldRange) })
    }

    @Test
    fun sameTextIsOneEqualSegment() {
        assertEquals(listOf("=hello world"), render("hello world", "hello world"))
    }

    @Test
    fun replacedWordIsDeletedThenInserted() {
        assertEquals(
            listOf("=the ", "-quick", "+slow", "= brown fox"),
            render("the quick brown fox", "the slow brown fox"),
        )
    }

    @Test
    fun whitespaceBetweenChangesJoinsTheChange() {
        // 「a」「b」がどちらも変わったら、間の空白ごと 1 つの変更にする
        assertEquals(listOf("-a b", "+x y", "= c"), render("a b c", "x y c"))
    }

    @Test
    fun shortEqualityBetweenChangesJoinsTheChange() {
        // 変わった単語に挟まれた短い一致 (「of」) は細切れにしない
        assertEquals(listOf("-x of y", "+p of q", "= end"), render("x of y end", "p of q end"))
        // 長い一致は残す
        assertEquals(listOf("-x", "+p", "= brown ", "-y", "+q"), render("x brown y", "p brown q"))
    }

    @Test
    fun insertionAndDeletionOnly() {
        // 足した単語の前後どちらの空白を変更に含めるかはアルゴリズム次第なので、単語と区間の数だけを見る
        val inserted = render("hello world", "hello there world")
        assertEquals(listOf("there"), inserted.filter { it.startsWith("+") }.map { it.drop(1).trim() })
        assertEquals(3, inserted.size)
        val deleted = render("hello there world", "hello world")
        assertEquals(listOf("there"), deleted.filter { it.startsWith("-") }.map { it.drop(1).trim() })
        assertEquals(3, deleted.size)
    }

    @Test
    fun fromAndToEmpty() {
        assertEquals(listOf("+new text"), render("", "new text"))
        assertEquals(listOf("-old text"), render("old text", ""))
        assertEquals(emptyList<String>(), render("", ""))
    }

    @Test
    fun shortcodeIsOneToken() {
        // 絵文字の中の単語だけが変わったことにしない (途中で分けると絵文字にならない)
        assertEquals(listOf("-:blob_ok:", "+:blob_cat:", "= hi"), render(":blob_ok: hi", ":blob_cat: hi"))
    }

    @Test
    fun segmentsReconstructBothVersions() {
        val cases = listOf(
            "履歴の確認: 最初の版 :quasar:" to "履歴の確認: 2 番目の版 (誤字を直した) :quasar:",
            "line one\nline two\n\nline three" to "line one\nline 2\nline three!",
            "Hello, world." to "Goodbye, cruel world!",
            "abc" to "xyz",
            "same" to "same",
        )
        cases.forEach { (old, new) ->
            assertReconstructs(old, new)
            assertReconstructs(new, old)
        }
    }

    @Test
    fun wordTokensCoverTheText() {
        val text = "Hello, :blob_ok: 世界!  two  spaces"
        val tokens = wordTokens(text)
        assertEquals(text, tokens.joinToString("") { text.substring(it) })
        assert(tokens.any { text.substring(it) == ":blob_ok:" })
    }

    @Test
    fun listDiffPlacesDeletedBeforeInsertedAtTheirPosition() {
        val diff = diffList(listOf("A", "B", "C"), listOf("A", "X", "C", "D"))
        assertEquals(
            listOf(
                ListDiffItem("A", ListDiffKind.Equal),
                ListDiffItem("B", ListDiffKind.Deleted),
                ListDiffItem("X", ListDiffKind.Inserted),
                ListDiffItem("C", ListDiffKind.Equal),
                ListDiffItem("D", ListDiffKind.Inserted),
            ),
            diff,
        )
    }

    @Test
    fun listDiffComparesByKey() {
        data class Media(val id: String, val description: String)
        val diff = diffList(listOf(Media("1", "old alt"), Media("2", "")), listOf(Media("1", "new alt")), key = { it.id })
        assertEquals(listOf(ListDiffKind.Equal, ListDiffKind.Deleted), diff.map { it.kind })
        assertEquals("new alt", diff[0].item.description)
    }

    @Test
    fun pairChangesTurnsReplacedItemsIntoChanged() {
        // 文言を直した選択肢は「変更」、増えた分は「追加」
        val diff = diffList(listOf("Apple", "Orange juice"), listOf("Apple", "Orange smoothie", "Grape"), pairChanges = true)
        assertEquals(
            listOf(
                ListDiffItem("Apple", ListDiffKind.Equal),
                ListDiffItem("Orange smoothie", ListDiffKind.Changed, previous = "Orange juice"),
                ListDiffItem("Grape", ListDiffKind.Inserted),
            ),
            diff,
        )
    }

    @Test
    fun pairChangesKeepsPureInsertionsAndDeletions() {
        // 間に足しただけ・消しただけなら組にしない
        assertEquals(
            listOf(ListDiffKind.Equal, ListDiffKind.Inserted, ListDiffKind.Equal),
            diffList(listOf("A", "B"), listOf("A", "X", "B"), pairChanges = true).map { it.kind },
        )
        assertEquals(
            listOf(ListDiffKind.Deleted, ListDiffKind.Deleted),
            diffList(listOf("A", "B"), emptyList(), pairChanges = true).map { it.kind },
        )
        // 減った分は消されたまま
        assertEquals(
            listOf(ListDiffKind.Changed, ListDiffKind.Deleted),
            diffList(listOf("A", "B"), listOf("Z"), pairChanges = true).map { it.kind },
        )
    }

    @Test
    fun withoutPairChangesReplacementIsDeletedAndInserted() {
        assertEquals(
            listOf(ListDiffKind.Deleted, ListDiffKind.Inserted),
            diffList(listOf("A"), listOf("Z")).map { it.kind },
        )
    }
}
