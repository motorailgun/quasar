package es.aria_on_the_planet.quasar.domain.model.settings

/**
 * 予測型「戻る」の動き (`PredictiveBackDamping`)。指で引いている間、今の画面は引いた量 p に対して
 * `maxDragFraction × ln(1 + dragLogSteepness × p) / ln(1 + dragLogSteepness)` だけずれ、離すと残りを抜ける。
 * 設定画面にはまだ出していない (上級者向けの設定を作るときに出す想定)。範囲は companion の Range。
 */
data class PredictiveBackSettings(
    /** 幅いっぱい引いたときに今の画面がずれる量 (幅に対する割合)。 */
    val maxDragFraction: Float = DefaultMaxDragFraction,
    /** log の曲がり具合。大きいほど引き始めでよく動き、早めに頭打ちになる。0 に近いほど直線。 */
    val dragLogSteepness: Float = DefaultDragLogSteepness,
    /** 遷移の長さ (ms)。離した後は残りの割合 × これで抜ける / 戻る。 */
    val transitionMillis: Int = DefaultTransitionMillis,
) {
    companion object {
        const val DefaultMaxDragFraction = 0.2f
        const val DefaultDragLogSteepness = 30f
        const val DefaultTransitionMillis = 300
        val MaxDragFractionRange = 0.05f..1f
        val DragLogSteepnessRange = 0.1f..100f
        val TransitionMillisRange = 50..1000
    }
}
