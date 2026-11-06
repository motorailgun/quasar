package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostTranslation
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationMode
import es.aria_on_the_planet.quasar.domain.model.settings.TranslationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ポストを訳すか・何語から何語へか。訳せない (言語が無い・モデルが対応していない・訳す先と同じ・本文が空)、または
 * 設定でその言語に出さないなら null。
 *
 * @param deviceLanguages 端末の言語 (BCP 47、先頭がアプリの言語)。先頭は [TranslationSettings.targetLanguage] が null の
 *   ときの訳す先、全部が [TranslationSettings.languageModes] を変えていないときに出さない言語。
 * @param sourceLanguage 元の言語。ポストに無ければ本文から推定したもの ([detectPostLanguage])。
 */
fun translationOfferFor(
    post: Post,
    settings: TranslationSettings,
    deviceLanguages: List<String>,
    sourceLanguage: String? = post.language,
): TranslationOffer? {
    if (post.text.isBlank() && post.contentWarning.isNullOrBlank()) return null
    val source = plamoLanguageOf(sourceLanguage) ?: return null
    val target = settings.targetLanguage ?: plamoLanguageOf(deviceLanguages.firstOrNull()) ?: "en"
    if (source == target) return null
    return when (settings.modeOf(source, plamoLanguagesOf(deviceLanguages))) {
        TranslationMode.Automatic -> TranslationOffer(source, target, automatic = true)
        TranslationMode.Offer -> TranslationOffer(source, target, automatic = false)
        TranslationMode.Hidden -> null
    }
}

/** 端末の言語 (BCP 47) を翻訳のモデルの言語コードにする (対応していないものは除く。順を保ち、重複を除く)。 */
fun plamoLanguagesOf(languageTags: List<String>): List<String> = languageTags.mapNotNull(::plamoLanguageOf).distinct()

/** [source] から [target] へ訳せる。[automatic] なら表示したときに訳す (でなければ「翻訳を表示」で)。 */
data class TranslationOffer(val source: String, val target: String, val automatic: Boolean)

/**
 * ポストを [TranslationEngine] で訳す。本文は HTML のまま訳させ ([toTranslatableHtml])、崩れたら平文で訳し直す。
 * 長い本文は段落等の切れ目で分けて 1 つずつ訳す ([split])。HTML か平文かは分けた 1 つごとに決める。
 */
class PostTranslator(private val engine: TranslationEngine) {
    /** 読み込んだモデルを解放する ([TranslationEngine.release])。 */
    suspend fun release() = engine.release()

    suspend fun translate(post: Post, offer: TranslationOffer): PostTranslation = withContext(Dispatchers.Default) {
        val source = toTranslatableHtml(post.text, post.emojis.mapTo(HashSet()) { it.shortcode })
        var plain = false
        var truncated = false
        val html = buildString {
            for (part in source.split(MaxPartLength)) when (part) {
                is TranslatablePart.Markup -> append(part.html)
                is TranslatablePart.Text -> {
                    val translated = translateText(part.source.html, offer)
                    // 切れた HTML は閉じていないので戻せない (戻せても途中まで)。平文なら短く収まることがある
                    val restored = if (translated.truncated) null else restoreTranslatedHtml(part.source, translated.text)
                    if (restored != null) {
                        append(restored)
                    } else {
                        val retried = translateText(part.source.plainText(), offer)
                        plain = true
                        truncated = truncated || retried.truncated
                        append(if (part.inline) plainTextToInlineHtml(retried.text) else plainTextToHtml(retried.text))
                    }
                }
            }
        }
        val contentWarning = post.contentWarning?.let { translateText(it, offer) }
        PostTranslation(
            html = html,
            contentWarning = contentWarning?.text?.trim(),
            sourceLanguage = offer.source,
            plain = plain,
            truncated = truncated || contentWarning?.truncated == true,
        )
    }

    private suspend fun translateText(text: String, offer: TranslationOffer): GeneratedText = engine.generate(
        plamoTranslationPrompt(PlamoTranslationRequest(text, targetLanguage = offer.target, sourceLanguage = offer.source)),
        MaxTokens,
    )

    private companion object {
        /** 訳文の上限 (トークン)。Mastodon の 500 文字の投稿の HTML なら収まる。 */
        const val MaxTokens = 1024

        /**
         * 1 回で訳す本文の長さの目安 (HTML の文字数)。訳文が [MaxTokens] に収まり、プロンプトがコンテキスト (4096) を超えない
         * よう、これを超える本文は段落等で分ける。日本語は 1 文字 1 トークン前後なので、英語等へ訳しても収まる大きさ。
         */
        const val MaxPartLength = 800
    }
}
