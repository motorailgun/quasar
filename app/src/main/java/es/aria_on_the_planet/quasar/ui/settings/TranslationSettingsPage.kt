package es.aria_on_the_planet.quasar.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.translation.PlamoLanguages
import es.aria_on_the_planet.quasar.data.translation.TranslationModelFailure
import es.aria_on_the_planet.quasar.data.translation.TranslationModelSource
import es.aria_on_the_planet.quasar.data.translation.TranslationModelState
import es.aria_on_the_planet.quasar.data.translation.plamoLanguageOf
import es.aria_on_the_planet.quasar.data.translation.plamoLanguagesOf
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsActionItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsChoiceItem
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsGroup
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsScaffold
import es.aria_on_the_planet.quasar.ui.component.settings.SettingsSwitchItem
import es.aria_on_the_planet.quasar.ui.translation.PostTranslationStore
import es.aria_on_the_planet.quasar.ui.translation.deviceLanguageTags
import es.aria_on_the_planet.quasar.ui.translation.languageDisplayName
import java.util.Locale

/** 翻訳の設定の確認ダイアログ。 */
private enum class TranslationDialog { Download, CancelDownload, Disable }

/**
 * 翻訳: オン / オフ (オンにするとモデルをダウンロードし、オフにするときはモデルを消すかを選ぶ)・モデルの状態・
 * 訳す先 (既定は端末の言語)・言語ごとの扱い ([TranslationLanguageSettings])。
 * 言語は翻訳のモデルが対応するもの ([PlamoLanguages]) を今の言語の表示名の順に並べる。
 */
@Composable
fun TranslationSettingsPage(
    settings: TranslationSettings,
    translations: PostTranslationStore,
    update: UpdateSettings,
    onBack: () -> Unit,
) {
    // 開くたびにモデルのファイル・ダウンロードを見直す (adb で置いた直後・裏で落とし終えた等)
    LaunchedEffect(Unit) { translations.refreshModel() }
    fun updateTranslation(transform: TranslationSettings.() -> TranslationSettings) =
        update { it.copy(translation = it.translation.transform()) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val languages = remember(locale) { PlamoLanguages.sortedBy { languageDisplayName(it, locale) } }
    val deviceTarget = plamoLanguageOf(locale.toLanguageTag()) ?: "en"
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    val modelSize = size(TranslationModelSource.Size)
    val model = translations.modelState
    var dialog by rememberSaveable { mutableStateOf<TranslationDialog?>(null) }

    SettingsScaffold(title = stringResource(R.string.settings_translation), onBack = onBack) {
        SettingsGroup(title = null) {
            SettingsSwitchItem(
                title = stringResource(R.string.settings_translation_enabled),
                summary = stringResource(R.string.settings_translation_enabled_summary),
                checked = settings.enabled,
                onCheckedChange = { checked ->
                    dialog = when {
                        checked && model != TranslationModelState.Ready -> TranslationDialog.Download
                        checked -> null.also { updateTranslation { copy(enabled = true) } }
                        model == TranslationModelState.Missing -> null.also { updateTranslation { copy(enabled = false) } }
                        else -> TranslationDialog.Disable
                    }
                },
            )
            if (settings.enabled) {
                SettingsActionItem(
                    title = stringResource(R.string.settings_translation_model),
                    summary = when (model) {
                        TranslationModelState.Missing -> stringResource(R.string.settings_translation_model_missing, modelSize)
                        is TranslationModelState.Downloading -> if (model.waiting && model.downloaded == 0L) {
                            stringResource(R.string.settings_translation_model_waiting)
                        } else {
                            stringResource(R.string.settings_translation_model_downloading, size(model.downloaded), size(model.total))
                        }
                        TranslationModelState.Verifying -> stringResource(R.string.settings_translation_model_verifying)
                        TranslationModelState.Ready -> stringResource(R.string.settings_translation_model_ready, modelSize)
                        is TranslationModelState.Failed -> stringResource(
                            when (model.reason) {
                                TranslationModelFailure.Storage -> R.string.settings_translation_model_failed_storage
                                TranslationModelFailure.Network -> R.string.settings_translation_model_failed_network
                                TranslationModelFailure.Corrupted -> R.string.settings_translation_model_failed_corrupted
                            },
                        )
                    },
                    icon = Symbols.Outlined.Translate,
                    onClick = {
                        when (model) {
                            TranslationModelState.Missing, is TranslationModelState.Failed -> dialog = TranslationDialog.Download
                            is TranslationModelState.Downloading -> dialog = TranslationDialog.CancelDownload
                            TranslationModelState.Verifying, TranslationModelState.Ready -> translations.refreshModel()
                        }
                    },
                )
            }
        }
        if (settings.enabled) {
            SettingsGroup(title = null) {
                SettingsChoiceItem<String?>(
                    title = stringResource(R.string.settings_translation_target),
                    options = listOf(null) + languages,
                    selected = settings.targetLanguage,
                    label = { code ->
                        if (code == null) {
                            stringResource(R.string.settings_translation_target_device, languageDisplayName(deviceTarget, locale))
                        } else {
                            languageDisplayName(code, locale)
                        }
                    },
                    onSelect = { code -> updateTranslation { copy(targetLanguage = code) } },
                )
            }
            TranslationLanguageSettings(
                settings = settings,
                deviceLanguages = remember(locale) { plamoLanguagesOf(deviceLanguageTags()) },
                target = settings.targetLanguage ?: deviceTarget,
                update = { updateTranslation(it) },
            )
        }
    }

    when (dialog) {
        TranslationDialog.Download -> ConfirmDialog(
            title = stringResource(R.string.settings_translation_download_title),
            message = stringResource(R.string.settings_translation_download_message, modelSize),
            confirmLabel = stringResource(R.string.settings_translation_download),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = {
                dialog = null
                updateTranslation { copy(enabled = true) }
                translations.downloadModel()
            },
            onDismiss = { dialog = null },
        )
        TranslationDialog.CancelDownload -> ConfirmDialog(
            title = stringResource(R.string.settings_translation_cancel_download_title),
            confirmLabel = stringResource(R.string.settings_translation_cancel_download),
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
            onConfirm = {
                dialog = null
                translations.deleteModel()
            },
            onDismiss = { dialog = null },
        )
        TranslationDialog.Disable -> DisableTranslationDialog(
            modelSize = modelSize,
            onConfirm = { deleteModel ->
                dialog = null
                updateTranslation { copy(enabled = false) }
                // ダウンロード中のものは残しても使えないので必ず止める
                if (deleteModel || model != TranslationModelState.Ready) translations.deleteModel()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** 翻訳をオフにする確認。ダウンロードしたモデルも消すか (既定で消す) を選ばせる。 */
@Composable
private fun DisableTranslationDialog(modelSize: String, onConfirm: (deleteModel: Boolean) -> Unit, onDismiss: () -> Unit) {
    var deleteModel by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_translation_disable_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.toggleable(value = deleteModel, onValueChange = { deleteModel = it }, role = Role.Checkbox),
            ) {
                Checkbox(checked = deleteModel, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.settings_translation_disable_delete, modelSize))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(deleteModel) }) { Text(stringResource(R.string.settings_translation_disable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
