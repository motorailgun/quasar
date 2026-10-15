package es.aria_on_the_planet.quasar.domain.model

import java.util.Locale

/**
 * 翻訳の設定。今は既定値のみで、将来は設定画面から変更して永続化する (`AppContainer.translationSettings`)。
 * 言語はいずれも ISO 639-1 の主言語 (`ko`, `en` 等)。
 */
data class TranslationSettings(
    /** ML Kit によるオンデバイス翻訳を使う (opt-in)。Google Play 開発者サービスが無い端末では有効でも使われない。 */
    val onDeviceEnabled: Boolean = false,
    /** 翻訳する言語 (翻訳元)。[Post.language] の主言語部分と比べる。 */
    val sourceLanguages: Set<String> = setOf("ko"),
    /** 翻訳先の言語。 */
    val targetLanguage: String = "en",
    /** 翻訳できるポストは最初から訳文を表示する。 */
    val autoTranslate: Boolean = false,
)

enum class TranslationProviderType { Server, OnDevice }

/** ポストの本文・CW の訳。 */
data class TranslatedContent(
    /** 本文の訳 (HTML)。リンク・カスタム絵文字の `:shortcode:` は原文のまま残る。 */
    val html: String,
    /** CW の訳。CW が無ければ `null`。 */
    val contentWarning: String?,
    /** 翻訳元の言語 (ISO 639)。 */
    val sourceLanguage: String,
    val provider: TranslationProviderType,
    /** サーバー側翻訳のサービス名 (`DeepL.com` 等)。分からなければ `null`。 */
    val providerName: String? = null,
    /** 訳した時点の [Post.translationSourceHash]。ポストが編集されたら古い訳を出さないために比べる。 */
    val sourceHash: Int,
)

/** 翻訳の対象 (本文 + CW) のハッシュ。 */
fun Post.translationSourceHash(): Int = 31 * text.hashCode() + contentWarning.hashCode()

/** 言語タグ (`ko-KR`, `zh-TW` 等) の主言語部分。不明 (`null`・空・`und`) なら `null`。 */
fun primaryLanguageOf(tag: String?): String? {
    if (tag.isNullOrBlank()) return null
    val language = Locale.forLanguageTag(tag.trim().replace('_', '-')).language.lowercase()
    return language.takeUnless { it.isEmpty() || it == "und" }
}
