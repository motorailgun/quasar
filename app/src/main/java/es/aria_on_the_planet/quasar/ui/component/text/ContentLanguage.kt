package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import java.util.Locale

/**
 * 文字のロケールを中身の言語 ([language]、ポストの `language` 等の ISO 639 / BCP 47) にしたスタイル。無い・読めなければそのまま。
 *
 * 漢字 (CJK 統合漢字) の字形はテキストのロケールで選ばれ、無ければ端末の言語の一覧で決まる。端末の言語に日本語が無いと
 * 日本語のポストが中国語 (簡体字) の字形で出るため、ポストの中身にはポストの言語を渡す。行の折り返しも言語に合う。
 */
fun TextStyle.withContentLanguage(language: String?): TextStyle {
    val tag = contentLanguageTag(language) ?: return this
    return copy(localeList = LocaleList(tag))
}

/** [language] を正規化した BCP 47 のタグ。空・言語の読めないもの (サーバから来る値なので何でもありうる) は `null`。 */
internal fun contentLanguageTag(language: String?): String? {
    if (language.isNullOrBlank()) return null
    val locale = Locale.forLanguageTag(language.trim())
    if (locale.language.isEmpty()) return null
    return locale.toLanguageTag()
}
