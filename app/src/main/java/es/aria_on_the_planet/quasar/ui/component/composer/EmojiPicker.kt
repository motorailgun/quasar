package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import es.aria_on_the_planet.quasar.ui.component.image.ImageKind
import es.aria_on_the_planet.quasar.ui.component.image.RemoteImage
import kotlinx.coroutines.launch

/**
 * カスタム絵文字のピッカー (検索欄 + カテゴリごとのグリッド)。シートやポップアップなど入れ物には依存しない。
 * 検索中はカテゴリを外し、[rankEmojis] の順 (前方一致 → 部分一致) で並べる。
 *
 * @param onSearchFocused 検索欄にフォーカスが来た (キーボードが出る) とき。シートなら全体に広げる等。
 */
@Composable
fun EmojiPicker(
    emojis: List<CustomEmoji>,
    onSelect: (CustomEmoji) -> Unit,
    modifier: Modifier = Modifier,
    onSearchFocused: () -> Unit = {},
) {
    val query = rememberTextFieldState()
    val categories = remember(emojis) { groupEmojisByCategory(emojis) }
    val results by remember(emojis) {
        derivedStateOf {
            query.text.trim().takeIf { it.isNotEmpty() }?.let { rankEmojis(emojis, it.toString(), Int.MAX_VALUE) }
        }
    }
    val gridState = rememberLazyGridState()
    LaunchedEffect(results == null) { gridState.scrollToItem(0) }

    Column(modifier) {
        OutlinedTextField(
            state = query,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onFocusChanged { if (it.isFocused) onSearchFocused() },
            placeholder = { Text(stringResource(R.string.emoji_picker_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            lineLimits = TextFieldLineLimits.SingleLine,
            // ショートコードは ASCII のみなので英字入力を求める (IME_FLAG_FORCE_ASCII)。Gboard 等は従うが、
            // Mozc 系 (Arte 等) は無視してかなのままになる (URI 種別にしても同じだった)
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            shape = RoundedCornerShape(28.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CellSize),
            state = gridState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val found = results
            if (found == null) {
                categories.forEach { category ->
                    header("header:${category.name}") { CategoryHeader(category.name) }
                    emojiItems(category.emojis, keyPrefix = "${category.name}:", onSelect)
                }
            } else if (found.isEmpty()) {
                header("empty") {
                    Text(
                        stringResource(R.string.emoji_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                emojiItems(found, keyPrefix = "search:", onSelect)
            }
        }
    }
}

private fun LazyGridScope.header(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") { content() }
}

private fun LazyGridScope.emojiItems(emojis: List<CustomEmoji>, keyPrefix: String, onSelect: (CustomEmoji) -> Unit) {
    items(emojis, key = { keyPrefix + it.shortcode }, contentType = { "emoji" }) { emoji ->
        EmojiCell(emoji, onClick = { onSelect(emoji) })
    }
}

@Composable
private fun CategoryHeader(name: String?) {
    Text(
        name ?: stringResource(R.string.emoji_picker_uncategorized),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmojiCell(emoji: CustomEmoji, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // アニメーションさせるかは「それ以外の絵文字」の設定に従う (止めるときは static_url)
        RemoteImage(
            url = emoji.url,
            staticUrl = emoji.staticUrl,
            kind = ImageKind.OtherEmoji,
            contentDescription = ":${emoji.shortcode}:",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(EmojiSize),
        )
    }
}

/**
 * カーソル位置 (範囲選択中はその範囲) に `:shortcode:` を入れ、後ろにカーソルを置く (ピッカーで選んだとき)。
 * 前後の空白は [insertEmojiAtCursor] が補う。
 */
fun TextFieldState.insertEmoji(emoji: CustomEmoji) {
    val (text, cursor) = insertEmojiAtCursor(this.text, selection, emoji.shortcode)
    edit {
        replace(0, length, text)
        selection = TextRange(cursor)
    }
}

/**
 * [EmojiPicker] を下からのシートで出す。選んでも閉じず、続けて選べる (閉じるのはユーザーの操作で [onDismiss])。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    emojis: List<CustomEmoji>,
    onSelect: (CustomEmoji) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        EmojiPicker(
            emojis = emojis,
            onSelect = onSelect,
            // 半分まで開いた状態から引き上げると全体を使える。検索中はキーボードの上に収める
            modifier = Modifier.fillMaxHeight().imePadding(),
            // 半分の高さではキーボードに隠れるので、検索を始めたら全体に広げる
            onSearchFocused = { scope.launch { sheetState.expand() } },
        )
    }
}

private val CellSize = 48.dp
private val EmojiSize = 32.dp
