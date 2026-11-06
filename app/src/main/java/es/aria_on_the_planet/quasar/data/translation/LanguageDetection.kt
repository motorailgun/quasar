package es.aria_on_the_planet.quasar.data.translation

import es.aria_on_the_planet.quasar.domain.model.Post

/**
 * 文の言語を推定する (端末の機能。[TextClassifierLanguageDetector])。BCP 47 の言語タグ、分からなければ null。
 * 重いことがあるのでメインスレッドでは呼ばない。
 */
fun interface LanguageDetector {
    suspend fun detect(text: String): String?
}

/**
 * 言語の付いていないポスト (Misskey 等は送ってこない) の言語を本文から [detector] で推定する。モデルの言語コード
 * ([plamoLanguageOf]) で、分からない・対応していなければ null。
 */
suspend fun detectPostLanguage(post: Post, detector: LanguageDetector?): String? {
    val text = listOfNotNull(
        post.contentWarning,
        languageDetectionText(post.text, post.emojis.mapTo(HashSet()) { it.shortcode }),
    ).joinToString(" ").trim().take(MaxDetectionChars)
    if (text.none { it.isLetter() }) return null
    return plamoLanguageOf(detector?.detect(text))
}

/** 推定に渡す長さ (先頭から)。 */
private const val MaxDetectionChars = 1000
