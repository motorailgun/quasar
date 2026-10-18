package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/*
 * 画面の切り替え (AOSP の設定画面と同じスライド)。進むときは新しい画面が右端から全体で入り、元の画面は
 * 左へ少しずれながら薄くなる。戻るときはその逆 (今の画面が右へ抜け、下の画面が左から戻る)。
 * `NavDisplay` の transitionSpec / popTransitionSpec / predictivePopTransitionSpec に渡す。
 * 予測型「戻る」は同じ向きの直線の遷移で、引いている間の動きは `PredictiveBackDamping` が抑える。
 */

/** Material の emphasized の曲線 (AOSP のページ遷移と同じ)。 */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val TransitionMillis = 450

/** 下になる画面がずれる量 (幅に対する割合)。 */
internal const val UnderlapFraction = 4

private fun <T> slideSpec() = tween<T>(TransitionMillis, easing = Emphasized)

/** 進むとき。 */
fun pushTransition(): ContentTransform =
    slideInHorizontally(slideSpec()) { it } togetherWith
        (slideOutHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeOut(slideSpec()))

/** 戻るとき (ボタン・予測型でない「戻る」)。抜けていく今の画面を上に描く (戻り先の画面を下に置く)。 */
fun popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeIn(slideSpec()),
    initialContentExit = slideOutHorizontally(slideSpec()) { it },
    targetContentZIndex = -1f,
)

/**
 * 予測型「戻る」。[popTransition] と同じ向きで、直線 (引いた量 = ずれ) にする。引いている間の見た目は
 * `PredictiveBackDamping` の補正で抑え、離した後は残りをそのまま抜ける。戻り先のフェードは補正できないので付けない。
 */
fun predictivePopTransition(millis: Int): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(linearSpec(millis)) { -it / UnderlapFraction },
    initialContentExit = slideOutHorizontally(linearSpec(millis)) { it },
    targetContentZIndex = -1f,
)

private fun <T> linearSpec(millis: Int) = tween<T>(millis, easing = LinearEasing)
