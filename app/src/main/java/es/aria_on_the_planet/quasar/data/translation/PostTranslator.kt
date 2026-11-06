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

/** ポストを [TranslationEngine] で訳す。本文は HTML のまま訳させ ([toTranslatableHtml])、崩れたら平文で訳し直す。 */
class PostTranslator(private val engine: TranslationEngine) {
    /** 読み込んだモデルを解放する ([TranslationEngine.release])。 */
    suspend fun release() = engine.release()

    suspend fun translate(post: Post, offer: TranslationOffer): PostTranslation = withContext(Dispatchers.Default) {
        val source = toTranslatableHtml(post.text, post.emojis.mapTo(HashSet()) { it.shortcode })
        val restored = if (source.html.isBlank()) "" else restoreTranslatedHtml(source, translateText(source.html, offer))
        PostTranslation(
            html = restored ?: plainTextToHtml(translateText(source.plainText(), offer)),
            contentWarning = post.contentWarning?.let { translateText(it, offer).trim() },
            sourceLanguage = offer.source,
            plain = restored == null,
        )
    }

    private suspend fun translateText(text: String, offer: TranslationOffer): String = engine.generate(
        plamoTranslationPrompt(PlamoTranslationRequest(text, targetLanguage = offer.target, sourceLanguage = offer.source)),
        MaxTokens,
    )

    private companion object {
        /** 訳文の上限 (トークン)。Mastodon の 500 文字の投稿の HTML なら収まる。 */
        const val MaxTokens = 1024
    }
}
