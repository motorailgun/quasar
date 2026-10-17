package es.aria_on_the_planet.quasar.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
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
 */

/** Material の emphasized の曲線 (AOSP のページ遷移と同じ)。 */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val TransitionMillis = 450

/** 下になる画面がずれる量 (幅に対する割合)。 */
private const val UnderlapFraction = 4

private fun <T> slideSpec() = tween<T>(TransitionMillis, easing = Emphasized)

/** 進むとき。 */
fun pushTransition(): ContentTransform =
    slideInHorizontally(slideSpec()) { it } togetherWith
        (slideOutHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeOut(slideSpec()))

/** 戻るとき (予測型「戻る」も)。抜けていく今の画面を上に描く (戻り先の画面を下に置く)。 */
fun popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeIn(slideSpec()),
    initialContentExit = slideOutHorizontally(slideSpec()) { it },
    targetContentZIndex = -1f,
)
