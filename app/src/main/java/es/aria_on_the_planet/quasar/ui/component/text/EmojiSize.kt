package es.aria_on_the_planet.quasar.ui.component.text

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified

/**
 * カスタム絵文字の縦横比 (幅 / 高さ) のキャッシュ。API は絵文字のサイズを返さないため、
 * 画像の読み込み完了時に記録し、以降の表示 (同じ URL の絵文字すべて) で枠の幅に使う。
 * Snapshot state なので、記録されると参照している EmojiText が再レイアウトされる。
 */
internal object EmojiAspectRatios {
    private val ratios = mutableStateMapOf<String, Float>()

    operator fun get(url: String): Float? = ratios[url]

    fun record(url: String, width: Float, height: Float) {
        val ratio = emojiAspectRatio(width, height) ?: return
        if (ratios[url] != ratio) ratios[url] = ratio
    }
}

private const val MinEmojiAspectRatio = 0.25f
private const val MaxEmojiAspectRatio = 8f

/** 画像サイズから表示用の縦横比を求める。極端な比率は丸める。サイズ不明なら `null`。 */
internal fun emojiAspectRatio(width: Float, height: Float): Float? {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    return (width / height).coerceIn(MinEmojiAspectRatio, MaxEmojiAspectRatio)
}

/** 絵文字の高さ。行の高さに合わせ、指定が無ければ 1.2em。 */
internal fun emojiHeight(style: TextStyle): TextUnit =
    if (style.lineHeight.isSpecified) style.lineHeight else 1.2.em
