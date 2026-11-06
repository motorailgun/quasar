package es.aria_on_the_planet.quasar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.translation.PlamoLanguages
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationMode
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsDropdownItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsMenuAction
import es.aria_on_the_planet.quasar.ui.translation.languageDisplayName
import java.util.Locale

/**
 * 翻訳を出す言語: 「その他の言語」の扱いと、それと違う扱いにする言語の一覧 (+ 言語を追加)。
 *
 * @param deviceLanguages 端末の言語 (翻訳のモデルの言語コード)。一覧をまだ変えていないときは、これを出さない一覧になる。
 * @param target 訳す先。その言語のポストには出さないので、一覧には足させない。
 */
@Composable
internal fun TranslationLanguageSettings(
    settings: TranslationSettings,
    deviceLanguages: List<String>,
    target: String,
    update: (TranslationSettings.() -> TranslationSettings) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val modes = settings.languageModesOr(deviceLanguages)
    val listed = remember(modes, locale) { modes.keys.sortedBy { languageDisplayName(it, locale) } }
    var adding by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(title = stringResource(R.string.settings_translation_languages)) {
        TranslationModeItem(
            title = stringResource(R.string.settings_translation_other_languages),
            mode = settings.defaultMode,
            defaultMode = null,
            onSelect = { mode -> update { copy(defaultMode = mode) } },
            onRemove = null,
        )
    }
    SettingsGroup(title = stringResource(R.string.settings_translation_per_language)) {
        for (code in listed) {
            key(code) {
                TranslationModeItem(
                    title = languageDisplayName(code, locale),
                    mode = modes.getValue(code),
                    defaultMode = settings.defaultMode,
                            onSelect = { mode -> update { withLanguageMode(code, mode, deviceLanguages) } },
                    onRemove = { update { withoutLanguage(code, deviceLanguages) } },
                )
            }
        }
        SettingsActionItem(
            title = stringResource(R.string.settings_translation_add_language),
            summary = null,
            icon = Symbols.Filled.Add,
            onClick = { adding = true },
        )
    }
    if (adding) {
        AddLanguageSheet(
            candidates = PlamoLanguages.filter { it !in modes && it != target },
            locale = locale,
            onAdd = { code ->
                adding = false
                update { withLanguageMode(code, defaultMode.addedLanguageMode, deviceLanguages) }
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun translationModeLabel(mode: TranslationMode): String = stringResource(
    when (mode) {
        TranslationMode.Automatic -> R.string.translation_mode_automatic
        TranslationMode.Offer -> R.string.translation_mode_offer
        TranslationMode.Hidden -> R.string.translation_mode_hidden
    },
)

/**
 * 言語 1 つ (または「その他の言語」) の扱いを選ぶ項目 (右端に今の値、押すとメニュー)。メニューの選択肢のうち「その他の言語」と
 * 同じものには「(既定)」を付ける ([defaultMode]。「その他の言語」自身は null)。[onRemove] があれば選択肢の下に「一覧から削除」。
 */
@Composable
private fun TranslationModeItem(
    title: String,
    mode: TranslationMode,
    defaultMode: TranslationMode?,
    onSelect: (TranslationMode) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val removeLabel = stringResource(R.string.settings_translation_remove_language)
    SettingsDropdownItem(
        title = title,
        options = TranslationMode.entries,
        selected = mode,
        label = { translationModeLabel(it) },
        menuLabel = { option ->
            val label = translationModeLabel(option)
            if (option == defaultMode) stringResource(R.string.translation_mode_default, label) else label
        },
        onSelect = onSelect,
        action = onRemove?.let { SettingsMenuAction(removeLabel, Symbols.Outlined.Delete, it, destructive = true) },
    )
}

/** 一覧に足す言語を選ぶシート。今の言語の表示名・英語名・言語コードで絞り込める。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLanguageSheet(candidates: List<String>, locale: Locale, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val languages = remember(candidates, locale) {
        candidates
            .map { code -> LanguageName(code, languageDisplayName(code, locale), languageDisplayName(code, Locale.ENGLISH)) }
            .sortedBy { it.name }
    }
    val needle = query.trim()
    val shown = if (needle.isEmpty()) languages else languages.filter { it.matches(needle) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.settings_translation_add_language),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Symbols.Outlined.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.settings_translation_search_language)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        // 絞り込んでもシートの高さを変えない
        LazyColumn(
            Modifier
                .fillMaxHeight()
                .imePadding(),
        ) {
            items(shown, key = { it.code }) { language ->
                ListItem(
                    headlineContent = { Text(language.name) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onAdd(language.code) },
                )
            }
        }
    }
}

private class LanguageName(val code: String, val name: String, val englishName: String) {
    fun matches(query: String) =
        name.contains(query, ignoreCase = true) || englishName.contains(query, ignoreCase = true) || code.startsWith(query, ignoreCase = true)
}
