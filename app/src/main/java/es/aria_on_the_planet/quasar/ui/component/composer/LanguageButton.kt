package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.postLanguageCodes
import es.aria_on_the_planet.quasar.domain.model.preferredPostLanguages
import es.aria_on_the_planet.quasar.ui.component.dialog.ChoiceDialog
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.translation.deviceLanguageTags
import es.aria_on_the_planet.quasar.ui.translation.languageDisplayName
import java.text.Collator

/**
 * ポストの言語のボタン (言語のアイコン + `JA` 等のコード、選んでいなければ「Auto」)。押すと言語を選ぶダイアログ
 * ([LanguageDialog]) を出す。色はツールバーのアイコンと同じく、選んでいれば primary、Auto ならオフの色 ([ComposerIconColors])。
 *
 * @param language 選んでいる言語。`null` なら Auto (送らずサーバに任せる)。
 * @param accountDefault アカウントの投稿の言語の設定 (Auto のときにサーバが使う)。分からなければ `null`。
 */
@Composable
fun LanguageButton(
    language: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    accountDefault: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = language?.uppercase() ?: stringResource(R.string.composer_language_auto)
    val stateLabel = language?.let { languageDisplayName(it) } ?: label
    TextButton(
        onClick = { open = true },
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (language != null) ComposerIconColors.on() else ComposerIconColors.off(),
            disabledContentColor = ComposerIconColors.disabled(),
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.semantics { stateDescription = stateLabel },
    ) {
        // ボタンの中のアイコンの標準の大きさ (18dp。既定の 24dp だと文字に対して大きい)
        Icon(
            Symbols.Outlined.Language,
            contentDescription = stringResource(R.string.composer_language),
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    if (open) {
        LanguageDialog(
            selected = language,
            accountDefault = accountDefault,
            onSelect = {
                open = false
                onChange(it)
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * 言語を選ぶダイアログ (翻訳の設定の「翻訳先」と同じ形)。上から Auto / よく使いそうな言語
 * ([preferredPostLanguages]。選んでいる言語もここに入る) / 区切り / 残りの言語 (表示名の順)。
 */
@Composable
private fun LanguageDialog(
    selected: String?,
    accountDefault: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val preferred = remember(selected, accountDefault) {
        preferredPostLanguages(selected, accountDefault, deviceLanguageTags())
    }
    val names = remember(locale) { postLanguageCodes().associateWith { languageDisplayName(it, locale) } }
    val others = remember(names, preferred) {
        val collator = Collator.getInstance(locale)
        names.keys.filter { it !in preferred }.sortedWith(compareBy(collator) { names.getValue(it) })
    }
    ChoiceDialog(
        title = stringResource(R.string.composer_language),
        groups = listOf(listOf(null) + preferred, others),
        selected = selected,
        label = { code ->
            if (code == null) stringResource(R.string.composer_language_auto) else names[code] ?: languageDisplayName(code, locale)
        },
        description = { code ->
            if (code != null) {
                null
            } else if (accountDefault != null) {
                stringResource(R.string.composer_language_auto_account, names[accountDefault] ?: languageDisplayName(accountDefault, locale))
            } else {
                stringResource(R.string.composer_language_auto_server)
            }
        },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
