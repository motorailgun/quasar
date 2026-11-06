package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationEntry
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationState
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationStore
import es.aria_on_the_planet.quasar.ui.translation.languageDisplayName

/** ポストの訳 (アプリ全体で 1 つ)。無い所 (翻訳の無いビルド・ギャラリー) では翻訳の表示を出さない。 */
val LocalPostTranslations = staticCompositionLocalOf<PostTranslationStore?> { null }

/**
 * [post] の訳。訳せない・翻訳が無いなら null。組み立てられている間を「表示されている」として、自動で訳す言語なら訳させる
 * (LazyColumn の項目はスクロールで画面に入る前後に組み立てられる)。
 */
@Composable
internal fun rememberPostTranslation(post: Post, enabled: Boolean): PostTranslationEntry? {
    val store = LocalPostTranslations.current ?: return null
    if (!enabled) return null
    // version は設定が変わると増える (entry を取り直す)
    store.version
    val entry = store.entryFor(post) ?: return null
    // 自動で訳す言語に設定されたら、表示中のものも訳し始める
    DisposableEffect(store, entry, entry.offer.automatic) {
        store.onShown(entry)
        onDispose { store.onHidden(entry) }
    }
    return entry
}

/** 本文の上の 1 行 (X の形): 「[文A] スペイン語から翻訳 (切れたら「一部を翻訳」)  原文を表示」/「[文A] 翻訳を表示」/ 訳している間 / 失敗。 */
@Composable
internal fun PostTranslationBar(
    entry: PostTranslationEntry,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.bodyMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val action = MaterialTheme.colorScheme.primary
    @Composable
    fun Action(text: String) = Text(
        text,
        style = style,
        color = action,
        maxLines = 1,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onToggle).padding(horizontal = 2.dp, vertical = 2.dp),
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbols.Outlined.Translate, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        when (val state = entry.state) {
            is PostTranslationState.Translated -> if (entry.showingOriginal) {
                Action(stringResource(R.string.translation_show))
            } else {
                Text(
                    stringResource(
                        // 長すぎて途中で切れた訳は「一部を翻訳」
                        if (state.translation.truncated) R.string.translation_translated_from_partial else R.string.translation_translated_from,
                        languageDisplayName(state.translation.sourceLanguage),
                    ),
                    style = style,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Action(stringResource(R.string.translation_show_original))
            }
            PostTranslationState.Idle -> Action(stringResource(R.string.translation_show))
            PostTranslationState.Queued, PostTranslationState.Translating ->
                Text(stringResource(R.string.translation_translating), style = style, color = muted, maxLines = 1)
            PostTranslationState.Failed -> {
                Text(stringResource(R.string.translation_failed), style = style, color = muted, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Action(stringResource(R.string.translation_retry))
            }
        }
    }
}
