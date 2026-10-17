package es.aria_on_the_planet.quasar.ui.component.image

import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings

/** 画像の種類。アニメーションさせるかを [DisplaySettings] のどの項目で決めるかに使う。 */
enum class ImageKind {
    /** ポストの中身 (本文・CW・投票・通知の抜粋) のカスタム絵文字。 */
    PostEmoji,

    /** それ以外 (表示名・bio・フィールド等) のカスタム絵文字。 */
    OtherEmoji,

    /** ユーザーのアイコン。 */
    Avatar,

    /** 上記以外 (添付メディア・リンクプレビュー・ヘッダ画像・投稿欄の添付)。 */
    Other,
}

/**
 * [kind] の画像のアニメーションの描き直しの上限 (fps)。`null` なら上限なし。絵文字・アイコンだけに掛ける
 * (添付メディア等は本来のコマ送りで見せる)。
 */
fun DisplaySettings.animationMaxFps(kind: ImageKind): Int? = when (kind) {
    ImageKind.PostEmoji, ImageKind.OtherEmoji, ImageKind.Avatar -> emojiAvatarAnimationMaxFps?.takeIf { it > 0 }
    ImageKind.Other -> null
}

/** [kind] の画像をアニメーションさせるか。[DisplaySettings.animateImages] がオフならすべて止める。 */
fun DisplaySettings.animates(kind: ImageKind): Boolean = animateImages && when (kind) {
    ImageKind.PostEmoji -> animatePostEmojis
    ImageKind.OtherEmoji -> animateOtherEmojis
    ImageKind.Avatar -> animateAvatars
    ImageKind.Other -> true
}
