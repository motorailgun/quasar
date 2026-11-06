package es.aria_on_the_planet.quasar.ui.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.licenses.LicenseRepository
import es.aria_on_the_planet.quasar.domain.model.LicenseCatalog
import es.aria_on_the_planet.quasar.domain.model.LicensedComponent
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsNavigationItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsScaffold
import kotlin.coroutines.cancellation.CancellationException

/** 読み込み中は null、失敗は [Result.failure]。 */
@Composable
private fun rememberLicenseCatalog(repository: LicenseRepository): Result<LicenseCatalog>? {
    val language = LocalConfiguration.current.locales[0].language
    val catalog by produceState<Result<LicenseCatalog>?>(null, repository, language) {
        value = try {
            Result.success(repository.catalog(language))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("LicensesPage", "failed to load licenses", e)
            Result.failure(e)
        }
    }
    return catalog
}

/**
 * ライセンスの一覧: 翻訳のモデル (PLaMo。「Built with PLaMo」を出す) / 同梱のソフトウェア / 依存のライブラリ。
 * 押すと [onOpen] で本文のページ。
 */
@Composable
fun LicensesPage(repository: LicenseRepository, onOpen: (id: String) -> Unit, onBack: () -> Unit) {
    val catalog = rememberLicenseCatalog(repository)
    SettingsScaffold(title = stringResource(R.string.settings_licenses), onBack = onBack) {
        val loaded = catalog?.getOrNull()
        when {
            catalog == null -> Unit
            loaded == null -> LoadFailed()
            else -> {
                loaded.translationModel?.let { model ->
                    SettingsGroup(title = stringResource(R.string.licenses_translation_model)) {
                        SettingsNavigationItem(
                            title = model.name,
                            summary = stringResource(R.string.licenses_built_with_plamo) + " · " + model.licenseNames(),
                            onClick = { onOpen(model.id) },
                        )
                    }
                }
                ComponentGroup(stringResource(R.string.licenses_bundled), loaded.bundled, onOpen)
                ComponentGroup(stringResource(R.string.licenses_libraries), loaded.libraries, onOpen)
            }
        }
    }
}

@Composable
private fun ComponentGroup(title: String, components: List<LicensedComponent>, onOpen: (String) -> Unit) {
    if (components.isEmpty()) return
    SettingsGroup(title = title) {
        for (component in components) {
            SettingsNavigationItem(
                title = component.name,
                summary = listOfNotNull(component.version, component.licenseNames()).joinToString(" · "),
                onClick = { onOpen(component.id) },
            )
        }
    }
}

private fun LicensedComponent.licenseNames(): String = licenses.joinToString(", ") { it.name }

/** 1 件の本文 (と版・ウェブサイト)。 */
@Composable
fun LicenseDetailPage(repository: LicenseRepository, id: String, onBack: () -> Unit) {
    val catalog = rememberLicenseCatalog(repository)
    val component = catalog?.getOrNull()?.find(id)
    val uriHandler = LocalUriHandler.current
    SettingsScaffold(title = component?.name ?: stringResource(R.string.settings_licenses), onBack = onBack) {
        when {
            catalog == null -> Unit
            component == null -> LoadFailed()
            else -> {
                val isTranslationModel = component.id == catalog.getOrNull()?.translationModel?.id
                SettingsGroup(title = null) {
                    if (isTranslationModel) {
                        LicenseText(stringResource(R.string.licenses_built_with_plamo))
                    }
                    component.version?.let { LicenseText(stringResource(R.string.licenses_version, it)) }
                    component.website?.let { website ->
                        SettingsActionItem(
                            title = stringResource(R.string.licenses_website),
                            summary = website,
                            icon = Symbols.Outlined.OpenInBrowser,
                            onClick = { uriHandler.openUri(website) },
                        )
                    }
                }
                for (license in component.licenses) {
                    SettingsGroup(title = license.name) {
                        license.text?.let { LicenseText(it.trim(), selectable = true) }
                        license.url?.let { url ->
                            SettingsActionItem(
                                title = stringResource(R.string.licenses_open_license),
                                summary = url,
                                icon = Symbols.Outlined.OpenInBrowser,
                                onClick = { uriHandler.openUri(url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 項目と同じ面に置く文。ライセンスの本文は長いので選んでコピーできるようにする。 */
@Composable
private fun LicenseText(text: String, selectable: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(16.dp),
    ) {
        val content = @Composable {
            Text(
                text,
                style = if (selectable) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selectable) SelectionContainer { content() } else content()
    }
}

@Composable
private fun LoadFailed() {
    SettingsGroup(title = null) {
        LicenseText(stringResource(R.string.licenses_load_failed))
    }
}
