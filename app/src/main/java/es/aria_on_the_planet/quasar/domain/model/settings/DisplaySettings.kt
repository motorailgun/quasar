package es.aria_on_the_planet.quasar.domain.model.settings

/**
 * 表示に関するユーザー設定。設定画面で変え、`SettingsStore` が保存する。画面からは `LocalDisplaySettings` で読む。
 */
data class DisplaySettings(
    /** タイムライン等でポスト本文を折りたたむ行数。これを超える分はフェードして「Show more」。`null` なら折りたたまない。 */
    val longPostMaxLines: Int? = DefaultLongPostMaxLines,
    /**
     * 一覧の上に浮かぶ UI (タイムラインのヘッダ・クイック投稿バー・プロフィールのタブ) の背景をぼかすか。オフなら不透明な面。
     * バッテリーセーバー中は設定に関わらずぼかさない (`rememberSurfaceBlurEnabled`)。ぼかしは描画の負荷が大きい。
     */
    val surfaceBlur: Boolean = true,
    /**
     * 同じぼかしの半径 (dp)。
     * `rememberSurfaceBlurStyle` が使う。閲覧注意メディアのぼかしは別 (隠すためのものなので対象外)。
     */
    val surfaceBlurRadiusDp: Float = DefaultSurfaceBlurRadiusDp,
    /** 同じぼかしに重ねる色味の不透明度 (0〜1)。大きいほど後ろが見えにくい。 */
    val surfaceBlurTintAlpha: Float = DefaultSurfaceBlurTintAlpha,
    /**
     * 画像のアニメーション (GIF / APNG / Motion WebP) を動かすか。オフなら他の項目に関わらずすべて止める。
     * 判定は `DisplaySettings.animates(ImageKind)` (`ui/component/image/ImageKind.kt`) にまとめている。
     */
    val animateImages: Boolean = true,
    /** ポストの中身 (本文・CW・投票・通知の抜粋) のカスタム絵文字。 */
    val animatePostEmojis: Boolean = true,
    /** それ以外 (表示名・bio・フィールド等) のカスタム絵文字。 */
    val animateOtherEmojis: Boolean = true,
    /** ユーザーのアイコン。 */
    val animateAvatars: Boolean = true,
    /**
     * 絵文字・アイコンのアニメーションの描き直しの上限 (fps)。`null` なら上限なし (画像の本来のコマ送り)。
     * 今は APNG のみに効く (GIF / Motion WebP は Android の再生が自分でコマを進めるため)。
     */
    val emojiAvatarAnimationMaxFps: Int? = DefaultEmojiAvatarAnimationMaxFps,
    /**
     * アプリ全体の UI の倍率 (dp と sp の両方に掛かる)。`QuasarAppContent` が `LocalDensity` を差し替えて効かせる。
     * Compose の外で描くもの (埋め込みの Photo Picker 等) には効かない。
     */
    val uiScale: Float = 1f,
    /** 文字だけの倍率 ([uiScale] と端末の文字サイズの設定に重ねて掛かる)。 */
    val fontScale: Float = 1f,
) {
    companion object {
        const val DefaultLongPostMaxLines = 8
        const val DefaultSurfaceBlurRadiusDp = 6f
        /** ぼかしの半径の上限 (dp)。設定画面のスライダーの範囲。 */
        const val MaxSurfaceBlurRadiusDp = 16f
        const val DefaultSurfaceBlurTintAlpha = 0.8f
        const val DefaultEmojiAvatarAnimationMaxFps = 30
        /** [uiScale] / [fontScale] の範囲。 */
        val ScaleRange = 0.75f..1.25f
        /** 設定画面のスライダーの [uiScale] / [fontScale] の刻み。 */
        const val ScaleStep = 0.025f
    }
}
