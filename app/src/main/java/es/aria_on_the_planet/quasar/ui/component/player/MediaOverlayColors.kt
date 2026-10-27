package es.aria_on_the_planet.quasar.ui.component.player

import androidx.compose.ui.graphics.Color

/**
 * メディア (映像・画像) の上や、メディアビューアの黒背景の上に重ねる操作部分の色。
 * 下地が常にメディアか黒なので、テーマ (ライト / ダーク、dynamic color) に関係なく白と半透明の黒に固定する。
 * 色をハードコードしてよいのはこの用途だけで、ここに集める。
 */
object MediaOverlayColors {
    /** アイコン・文字。 */
    val Content = Color.White

    /** 操作部分の下に敷く半透明の黒 (アイコンの丸い下地・ラベル等)。 */
    val Scrim = Color.Black.copy(alpha = 0.5f)

    /** シークバー等の、まだ進んでいない側。 */
    val ContentInactive = Color.White.copy(alpha = 0.35f)

    /** 下端・上端の操作部分の下に敷くグラデーションの濃い側。 */
    val GradientScrim = Color.Black.copy(alpha = 0.55f)

    /** 画像の上端に重ねたトップバーの下に敷く、薄いグラデーションの濃い側 (プロフィールのヘッダ画像)。 */
    val TopBarScrim = Color.Black.copy(alpha = 0.3f)

    /** メディアビューアの背景。 */
    val Background = Color.Black
}
