package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import es.aria_on_the_planet.quasar.domain.model.DiffKind
import es.aria_on_the_planet.quasar.domain.model.diffText
import es.aria_on_the_planet.quasar.ui.theme.extendedColors

/** 差分の色付け。足された所 ([added]) と消された所 ([removed])。 */
@Immutable
data class TextDiffStyles(val added: SpanStyle, val removed: SpanStyle)

/** 差分の色付けの既定 (`extendedColors` の diffAdded / diffRemoved。消された所は取り消し線も)。 */
@Composable
@ReadOnlyComposable
fun textDiffStyles(): TextDiffStyles {
    val colors = MaterialTheme.extendedColors
    return TextDiffStyles(
        added = SpanStyle(color = colors.diffAdded.onContainer, background = colors.diffAdded.container),
        removed = SpanStyle(
            color = colors.diffRemoved.onContainer,
            background = colors.diffRemoved.container,
            textDecoration = TextDecoration.LineThrough,
        ),
    )
}

/**
 * この文字列 (今の版) に、[old] (前の版) との差分を書き込んだもの。変わらない所・足された所は今の版から、消された所は
 * 前の版から切り出して並べる (リンク等のスタイルはそれぞれのものを保つ)。差分は単語単位 ([diffText])。
 */
internal fun AnnotatedString.withDiffFrom(old: AnnotatedString, styles: TextDiffStyles): AnnotatedString {
    val current = this
    return buildAnnotatedString {
        for (segment in diffText(old.text, current.text)) {
            when (segment.kind) {
                DiffKind.Equal -> append(current.subSequence(segment.newRange.first, segment.newRange.last + 1))
                DiffKind.Inserted -> withStyle(styles.added) {
                    append(current.subSequence(segment.newRange.first, segment.newRange.last + 1))
                }
                DiffKind.Deleted -> withStyle(styles.removed) {
                    append(old.subSequence(segment.oldRange.first, segment.oldRange.last + 1))
                }
            }
        }
    }
}
