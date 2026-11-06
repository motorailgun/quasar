package es.aria_on_the_planet.quasar.domain.model

/**
 * ポストの訳 (端末内のモデルで訳したもの)。表示は原文と同じく `EmojiText(isHtml = true)` / CW。
 *
 * @property html 本文の訳 (HTML。リンク・メンション・ハッシュタグ・`:shortcode:` は原文のまま)。
 * @property contentWarning CW の訳。CW が無ければ `null`。
 * @property sourceLanguage 訳した元の言語 (翻訳のモデルの言語コード)。「〇〇語から翻訳」に出す。
 * @property plain HTML の構造を保てず、平文で訳し直したもの (リンクは文字になる。長い本文は分けて訳すので、一部だけのこともある)。
 * @property truncated 訳文が上限で途中で切れた (分けても 1 つが長すぎた)。「一部を翻訳」と出す。
 */
data class PostTranslation(
    val html: String,
    val contentWarning: String?,
    val sourceLanguage: String,
    val plain: Boolean = false,
    val truncated: Boolean = false,
)
