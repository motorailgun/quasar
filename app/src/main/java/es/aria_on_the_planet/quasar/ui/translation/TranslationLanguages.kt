package es.aria_on_the_planet.quasar.ui.translation

import android.content.res.Resources
import android.icu.util.ULocale
import java.util.Locale

/**
 * 翻訳のモデルの言語コード (`es`・`zh-hans` 等) の表示名 (今の言語で。「スペイン語」「簡体中国語」)。
 * 中国語の `zh-hans` / `zh-hant` は BCP 47 の文字体系 (`Hans` / `Hant`) として読め、ICU の方言名で
 * 「簡体中国語」/「Simplified Chinese」になる (`Locale.getDisplayName` だと「中国語 (漢字(簡体字))」)。
 */
fun languageDisplayName(code: String, locale: Locale = Locale.getDefault()): String =
    ULocale.forLanguageTag(code).getDisplayNameWithDialect(ULocale.forLocale(locale))
        .replaceFirstChar { it.titlecase(locale) }.ifBlank { code }

/**
 * 端末の言語 (BCP 47)。先頭はアプリの言語 (アプリの言語を選んでいればそれ、無ければ端末の第 1 言語)、続いて端末の設定の
 * 言語の順。
 */
fun deviceLanguageTags(): List<String> {
    val system = Resources.getSystem().configuration.locales
    return (listOf(Locale.getDefault()) + (0 until system.size()).map(system::get)).map { it.toLanguageTag() }.distinct()
}
