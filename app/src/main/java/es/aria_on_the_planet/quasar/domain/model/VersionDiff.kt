package es.aria_on_the_planet.quasar.domain.model

import com.github.difflib.DiffUtils
import com.github.difflib.patch.AbstractDelta
import java.text.BreakIterator
import java.util.Locale

/** 差分の 1 区間の種類。 */
enum class DiffKind {
    /** 前の版と同じ。 */
    Equal,

    /** 今の版で足された。 */
    Inserted,

    /** 前の版から消された。 */
    Deleted,
}

/**
 * 文字列の差分の 1 区間。[oldRange] は前の版の文字列、[newRange] は今の版の文字列の範囲 (終わりは含まない)。
 * [DiffKind.Inserted] なら [oldRange] が、[DiffKind.Deleted] なら [newRange] が空。
 */
data class TextDiffSegment(val kind: DiffKind, val oldRange: IntRange, val newRange: IntRange) {
    init {
        require(kind != DiffKind.Inserted || oldRange.isEmpty())
        require(kind != DiffKind.Deleted || newRange.isEmpty())
    }
}

/**
 * [old] から [new] への差分を、単語 ([wordTokens]) 単位で求める (Myers 法、java-diff-utils)。
 *
 * 区間は前から順で、変わった所は「消された分 → 足された分」の順に 1 つずつにまとめる。変わった所に挟まれた短い一致
 * (空白・記号や、空白を除いて [ShortEqualityLength] 文字以下。日本語の助詞「を」等) も変わった所に含める
 * (「~~誤字~~CW と添付を~~直~~足した」のように細切れにしない)。[DiffKind.Equal] と [DiffKind.Inserted] の
 * [TextDiffSegment.newRange] をつなぐと [new] に、[DiffKind.Equal] と [DiffKind.Deleted] の [TextDiffSegment.oldRange]
 * をつなぐと [old] になる。
 */
fun diffText(old: String, new: String): List<TextDiffSegment> {
    val oldTokens = wordTokens(old)
    val newTokens = wordTokens(new)
    val deltas = DiffUtils.diff(oldTokens.map { old.substring(it) }, newTokens.map { new.substring(it) }).deltas
    val raw = mutableListOf<TextDiffSegment>()
    var oldAt = 0
    var newAt = 0
    fun equalUntil(oldTo: Int, newTo: Int) {
        if (newTo > newAt) {
            raw += TextDiffSegment(DiffKind.Equal, charRange(oldTokens, oldAt, oldTo), charRange(newTokens, newAt, newTo))
        }
        oldAt = oldTo
        newAt = newTo
    }
    for (delta in deltas.sortedBy { it.source.position }) {
        equalUntil(delta.source.position, delta.target.position)
        raw += delta.segments(oldTokens, newTokens)
        oldAt = delta.source.position + delta.source.size()
        newAt = delta.target.position + delta.target.size()
    }
    equalUntil(oldTokens.size, newTokens.size)
    return raw.absorbShortEqualities(old).groupChanges()
}

/** トークンの区間 [from, to) の文字の範囲。空なら [from] 番目のトークンの先頭 (末尾なら文字列の終わり) の空の範囲。 */
private fun charRange(tokens: List<IntRange>, from: Int, to: Int): IntRange =
    if (from == to) {
        val at = if (from < tokens.size) tokens[from].first else (tokens.lastOrNull()?.let { it.last + 1 } ?: 0)
        at until at
    } else {
        tokens[from].first..tokens[to - 1].last
    }

private fun AbstractDelta<String>.segments(oldTokens: List<IntRange>, newTokens: List<IntRange>): List<TextDiffSegment> {
    val oldFrom = source.position
    val newFrom = target.position
    return listOfNotNull(
        if (source.size() > 0) {
            TextDiffSegment(DiffKind.Deleted, charRange(oldTokens, oldFrom, oldFrom + source.size()), charRange(newTokens, newFrom, newFrom))
        } else null,
        if (target.size() > 0) {
            TextDiffSegment(DiffKind.Inserted, charRange(oldTokens, oldFrom + source.size(), oldFrom + source.size()), charRange(newTokens, newFrom, newFrom + target.size()))
        } else null,
    )
}

/** 変わった所に挟まれても残す一致の最短の長さ (空白を除いた文字数)。これ以下なら変わった所に含める。 */
private const val ShortEqualityLength = 2

/** 変わった所に挟まれた短い [DiffKind.Equal] を、消された分と足された分の両方に入れる。 */
private fun List<TextDiffSegment>.absorbShortEqualities(old: String): List<TextDiffSegment> =
    flatMapIndexed { i, segment ->
        val between = i > 0 && i < lastIndex && this[i - 1].kind != DiffKind.Equal && this[i + 1].kind != DiffKind.Equal
        if (segment.kind == DiffKind.Equal && between && old.substring(segment.oldRange).trim().length <= ShortEqualityLength) {
            listOf(
                TextDiffSegment(DiffKind.Deleted, segment.oldRange, segment.newRange.first until segment.newRange.first),
                TextDiffSegment(DiffKind.Inserted, segment.oldRange.last + 1 until segment.oldRange.last + 1, segment.newRange),
            )
        } else {
            listOf(segment)
        }
    }

/** 続いた変わった区間を、消された分 1 つ + 足された分 1 つにまとめる (前の版・今の版のどちらでも続いた範囲になる)。 */
private fun List<TextDiffSegment>.groupChanges(): List<TextDiffSegment> {
    val result = mutableListOf<TextDiffSegment>()
    var i = 0
    while (i < size) {
        if (this[i].kind == DiffKind.Equal) {
            result += this[i++]
            continue
        }
        var j = i
        while (j < size && this[j].kind != DiffKind.Equal) j++
        val group = subList(i, j)
        val deleted = group.filter { it.kind == DiffKind.Deleted }
        val inserted = group.filter { it.kind == DiffKind.Inserted }
        val oldStart = group.minOf { it.oldRange.first }
        val newStart = group.minOf { it.newRange.first }
        if (deleted.isNotEmpty()) {
            result += TextDiffSegment(DiffKind.Deleted, oldStart..deleted.maxOf { it.oldRange.last }, newStart until newStart)
        }
        if (inserted.isNotEmpty()) {
            val oldEnd = (deleted.maxOfOrNull { it.oldRange.last } ?: (oldStart - 1)) + 1
            result += TextDiffSegment(DiffKind.Inserted, oldEnd until oldEnd, newStart..inserted.maxOf { it.newRange.last })
        }
        i = j
    }
    return result
}

/** 区切りの細かさ。カスタム絵文字の `:shortcode:` は 1 つの単位にする (途中で分けると絵文字にならない)。 */
private val Shortcode = Regex(""":[A-Za-z0-9_]+:""")

/**
 * [text] を単語の区間に分ける (空白・記号も 1 つの区間。全部つなぐと [text] になる)。単語の区切りは
 * [BreakIterator] (Android では ICU で、日本語も辞書で区切る)。
 */
internal fun wordTokens(text: String): List<IntRange> {
    val tokens = mutableListOf<IntRange>()
    var last = 0
    fun words(from: Int, to: Int) {
        if (from >= to) return
        val iterator = BreakIterator.getWordInstance(Locale.ROOT)
        iterator.setText(text.substring(from, to))
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            tokens += (from + start) until (from + end)
            start = end
            end = iterator.next()
        }
    }
    for (match in Shortcode.findAll(text)) {
        words(last, match.range.first)
        tokens += match.range
        last = match.range.last + 1
    }
    words(last, text.length)
    return tokens
}

/** 一覧の差分の 1 項目の種類。 */
enum class ListDiffKind {
    /** 前の版と同じ。 */
    Equal,

    /** 今の版で足された。 */
    Inserted,

    /** 前の版から消された (前の版にだけある)。 */
    Deleted,

    /** 同じ位置の項目の中身が変わった ([diffList] の `pairChanges` のときだけ)。前の版の項目は [ListDiffItem.previous]。 */
    Changed,
}

/** 一覧の差分の 1 項目。[previous] は [ListDiffKind.Changed] のときの前の版の項目。 */
data class ListDiffItem<T>(val item: T, val kind: ListDiffKind, val previous: T? = null)

/**
 * [old] から [new] への一覧の差分。今の版の並びに、消された項目を元の位置 (直後に足された項目があればその前) に
 * 挟んで返す。項目が同じかは [key] で比べる (添付なら ID)。
 *
 * @param pairChanges 置き換えられた範囲 (前の版の項目が消え、同じ所に別の項目が入った) の項目を、前から順に前の版と
 *   今の版で組にして [ListDiffKind.Changed] にする (数が合わない残りは消された / 足されたまま)。投票の選択肢のように
 *   位置で対応するもので、文言を直しただけの項目を「消して足した」にしないため。
 */
fun <T> diffList(
    old: List<T>,
    new: List<T>,
    key: (T) -> Any? = { it },
    pairChanges: Boolean = false,
): List<ListDiffItem<T>> {
    val deltas = DiffUtils.diff(old, new) { a, b -> key(a) == key(b) }.deltas.sortedBy { it.source.position }
    val result = mutableListOf<ListDiffItem<T>>()
    var newAt = 0
    for (delta in deltas) {
        while (newAt < delta.target.position) result += ListDiffItem(new[newAt++], ListDiffKind.Equal)
        val removed = delta.source.lines
        val added = delta.target.lines
        val paired = if (pairChanges) minOf(removed.size, added.size) else 0
        for (i in 0 until paired) result += ListDiffItem(added[i], ListDiffKind.Changed, previous = removed[i])
        removed.drop(paired).forEach { result += ListDiffItem(it, ListDiffKind.Deleted) }
        added.drop(paired).forEach { result += ListDiffItem(it, ListDiffKind.Inserted) }
        newAt = delta.target.position + delta.target.size()
    }
    while (newAt < new.size) result += ListDiffItem(new[newAt++], ListDiffKind.Equal)
    return result
}
