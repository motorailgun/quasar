package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage

/** 入力中の `:sho` に対する絵文字の候補。 */
@Stable
class EmojiSuggestions(
    private val field: TextFieldState,
    val query: AutocompleteQuery,
    val emojis: List<CustomEmoji>,
) {
    /** [emoji] で `:sho` を置き換え、後ろにカーソルを置く。 */
    fun select(emoji: CustomEmoji) {
        val (text, cursor) = insertEmoji(field.text, query.range, emoji.shortcode)
        field.edit {
            replace(0, length, text)
            selection = TextRange(cursor)
        }
    }
}

/**
 * [field] のカーソル直前の `:sho` から絵文字の候補を求める。入力に追従し、候補が無ければ `null`。
 * [field] を差し替えれば (本文 / CW 欄のうちフォーカスのある方など) そちらを見る。
 */
@Composable
fun rememberEmojiSuggestions(field: TextFieldState, emojis: List<CustomEmoji>): State<EmojiSuggestions?> =
    remember(field, emojis) {
        derivedStateOf {
            if (emojis.isEmpty()) return@derivedStateOf null
            val query = findAutocompleteQuery(field.text, field.selection) ?: return@derivedStateOf null
            rankEmojis(emojis, query.query)
                .takeIf { it.isNotEmpty() }
                ?.let { EmojiSuggestions(field, query, it) }
        }
    }

/**
 * 絵文字の候補の帯 (横スクロール)。キーボードの候補欄のように、ツールバーの直上に置く想定。
 * 候補が無い間は畳む。
 */
@Composable
fun EmojiSuggestionStrip(
    suggestions: EmojiSuggestions?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = suggestions != null,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        // 畳む間も直前の候補を出しておく
        val current = remember { LastNonNull<EmojiSuggestions>() }
        val shown = current.update(suggestions) ?: return@AnimatedVisibility
        val listState = rememberLazyListState()
        // 問い合わせが変わったら先頭 (最も一致するもの) へ戻す
        LaunchedEffect(shown.query.query) { listState.scrollToItem(0) }
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown.emojis, key = { it.shortcode }) { emoji ->
                AssistChip(
                    onClick = { shown.select(emoji) },
                    label = { Text(":${emoji.shortcode}:", maxLines = 1) },
                    leadingIcon = {
                        RemoteImage(
                            url = emoji.url,
                            staticUrl = emoji.staticUrl,
                            kind = ImageKind.OtherEmoji,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier.animateItem(),
                )
            }
            item { Spacer(Modifier.width(4.dp)) }
        }
    }
}

/** 直前の `null` でない値を覚える (退場アニメーション中に中身を保つため)。 */
private class LastNonNull<T : Any> {
    private var last: T? = null
    fun update(value: T?): T? {
        if (value != null) last = value
        return last
    }
}

/**
 * 絵文字になる `:shortcode:` を、本文はそのままに少し太く + 色付きで表示する (Discord の入力欄のように)。
 * 判定は [findEmojiShortcodes] (サーバと同じ前後の条件 + 実在するショートコードのみ)。
 */
class EmojiShortcodeHighlight(
    emojis: List<CustomEmoji>,
    private val style: SpanStyle,
) : OutputTransformation {
    private val known = emojis.mapTo(HashSet()) { it.shortcode }

    override fun TextFieldBuffer.transformOutput() {
        findEmojiShortcodes(asCharSequence(), known).forEach { range ->
            addStyle(style, range.first, range.last + 1)
        }
    }
}

/** [EmojiShortcodeHighlight] を [emojis] が変わったときだけ作り直す。 */
@Composable
fun rememberEmojiShortcodeHighlight(emojis: List<CustomEmoji>): OutputTransformation? {
    val color = MaterialTheme.colorScheme.primary
    return remember(emojis, color) {
        if (emojis.isEmpty()) null
        else EmojiShortcodeHighlight(emojis, SpanStyle(color = color, fontWeight = FontWeight.SemiBold))
    }
}
