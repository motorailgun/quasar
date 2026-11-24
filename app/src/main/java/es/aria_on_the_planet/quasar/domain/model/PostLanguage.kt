package es.aria_on_the_planet.quasar.domain.model

import java.util.Locale

/**
 * ポストの言語として選べる言語 (ISO 639-1 の 2 文字のコード)。Mastodon はどれも受け付ける
 * (`LanguagesHelper::ISO_639_1`)。古いコード (`iw` 等) は今のもの (`he`) にする。
 */
fun postLanguageCodes(): List<String> =
    Locale.getISOLanguages()
        .map { Locale.forLanguageTag(it).toLanguageTag() }
        .filter { it.length == 2 }
        .distinct()

/**
 * 言語のメニューで一覧の上にまとめて出す言語。アカウントの投稿の言語の設定 → 端末の言語 (地域を外した言語の部分) →
 * 選んでいる言語の順で、重なりは除く。
 *
 * @param deviceLanguages 端末の言語 (BCP 47。`ja-JP` 等)。
 */
fun preferredPostLanguages(selected: String?, accountDefault: String?, deviceLanguages: List<String>): List<String> =
    buildList {
        accountDefault?.let(::add)
        deviceLanguages.mapNotNullTo(this) { tag -> tag.substringBefore('-').lowercase().takeIf { it.isNotEmpty() && it != "und" } }
        selected?.let(::add)
    }.distinct()
