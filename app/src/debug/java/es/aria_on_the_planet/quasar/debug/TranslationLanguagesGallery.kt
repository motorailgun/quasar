package es.aria_on_the_planet.quasar.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.data.translation.plamoLanguageOf
import es.aria_on_the_planet.quasar.data.translation.plamoLanguagesOf
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsScaffold
import es.aria_on_the_planet.quasar.ui.settings.TranslationLanguageSettings
import es.aria_on_the_planet.quasar.ui.translation.deviceLanguageTags
import es.aria_on_the_planet.quasar.ui.translation.languageDisplayName

/** 確かめる言語 (結果の欄)。 */
private val SampleLanguages = listOf("en", "ja", "es", "ko", "fr", "de", "zh-hans")

/**
 * 翻訳の設定の言語ごとの扱い (モデルが無くても触れる)。設定は手元だけ (保存しない) で、下の「結果」に言語ごとの扱い
 * (訳す先は常に出さない) を出す。
 */
@Composable
internal fun TranslationLanguagesGallery(onBack: () -> Unit) {
    var settings by remember { mutableStateOf(TranslationSettings(enabled = true)) }
    val deviceLanguages = remember { plamoLanguagesOf(deviceLanguageTags()) }
    val target = plamoLanguageOf(deviceLanguageTags().firstOrNull()) ?: "en"

    SettingsScaffold(title = stringResource(GalleryScenario.TranslationLanguages.title), onBack = onBack) {
        SettingsGroup(title = "Gallery") {
            SettingsActionItem(
                title = "Reset",
                summary = "Forget the list (device languages ${deviceLanguages.joinToString()} are hidden). Target: $target",
                onClick = { settings = TranslationSettings(enabled = true) },
            )
        }
        TranslationLanguageSettings(
            settings = settings,
            deviceLanguages = deviceLanguages,
            target = target,
            update = { settings = settings.it() },
        )
        SettingsGroup(title = "Result") {
            for (code in SampleLanguages) {
                SettingsActionItem(
                    title = languageDisplayName(code),
                    summary = if (code == target) "Target (never shown)" else settings.modeOf(code, deviceLanguages).name,
                    onClick = {},
                )
            }
        }
    }
}
