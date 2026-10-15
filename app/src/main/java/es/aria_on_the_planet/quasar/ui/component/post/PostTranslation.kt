package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.TranslatedContent
import es.aria_on_the_planet.quasar.domain.model.TranslationProviderType
import es.aria_on_the_planet.quasar.ui.feed.PostTranslationUi
import java.util.Locale

/**
 * 訳文の上に置くラベル (x_translation.png を参考)。「[文A] Translated from Korean · On device  Show original」。
 * オンデバイスとサーバー (DeepL 等) では訳の質が違うので、どちらで訳したかも出す。
 */
@Composable
internal fun TranslatedLabel(content: TranslatedContent, onShowOriginal: () -> Unit, modifier: Modifier = Modifier) {
    val language = languageDisplayName(content.sourceLanguage)
    val via = when (content.provider) {
        TranslationProviderType.OnDevice -> stringResource(R.string.translation_on_device)
        TranslationProviderType.Server -> content.providerName
    }
    TranslationRow(
        status = stringResource(R.string.translation_translated_from, language) + (via?.let { " · $it" } ?: ""),
        action = stringResource(R.string.translation_show_original),
        onAction = onShowOriginal,
        modifier = modifier,
    )
}

/** 本文の下に置く、訳文表示中以外の操作・状態 (「Translate post」/ 翻訳中 / 「Show translation」/ 失敗)。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslationFooter(translation: PostTranslationUi, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    when (translation) {
        PostTranslationUi.Available ->
            TranslationRow(null, stringResource(R.string.translation_translate), onToggle, modifier)

        is PostTranslationUi.Original ->
            TranslationRow(null, stringResource(R.string.translation_show_translation), onToggle, modifier)

        PostTranslationUi.Failed ->
            TranslationRow(stringResource(R.string.translation_failed), stringResource(R.string.translation_retry), onToggle, modifier)

        is PostTranslationUi.Loading -> Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(LabelIconSize))
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (translation.downloadingModel) {
                    stringResource(R.string.translation_downloading_model, languageDisplayName(translation.sourceLanguage))
                } else {
                    stringResource(R.string.translation_translating)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 訳文の表示中は本文の上の TranslatedLabel が担当する
        is PostTranslationUi.Shown -> Unit
    }
}

/** 翻訳アイコン + 状態の文言 (onSurfaceVariant) + 押せる文言 (primary)。 */
@Composable
private fun TranslationRow(status: String?, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Translate,
            contentDescription = null,
            modifier = Modifier.size(LabelIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        if (status != null) {
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .clickable(onClick = onAction)
                .padding(vertical = 4.dp),
        )
    }
}

private val LabelIconSize = 16.dp

/** 言語コードを表示中の言語での名前にする (`ko` → 「Korean」)。分からなければコードのまま。 */
@Composable
private fun languageDisplayName(code: String): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return Locale.forLanguageTag(code).getDisplayLanguage(locale).ifBlank { code }
}
