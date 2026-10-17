package es.aria_on_the_planet.quasar.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import kotlinx.serialization.Serializable

/** 設定のページ (Navigation 3 のキー)。トップから各ページへ進む。 */
@Serializable
sealed interface SettingsRoute : NavKey {
    @Serializable data object Top : SettingsRoute
    @Serializable data object Display : SettingsRoute
    @Serializable data object Animation : SettingsRoute
    @Serializable data object Timelines : SettingsRoute
    @Serializable data object Posting : SettingsRoute
    @Serializable data object Confirmations : SettingsRoute
}

/** 設定画面。[openSettings] で起動する。中のページの行き来は Navigation 3 (予測型「戻る」のアニメーション込み)。 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val store = container.settingsStore
        setContent {
            QuasarAppContent(container, inlinePlayback = false) {
                // QuasarAppContent は読み込みを待ってから中身を描くので、ここでは null にならない
                val stored by store.settings.collectAsStateWithLifecycle()
                val settings = stored ?: AppSettings()
                val backStack = rememberNavBackStack(SettingsRoute.Top)
                val navigate: (SettingsRoute) -> Unit = { backStack.add(it) }
                val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() else finish() }
                val update = store::launchUpdate
                NavDisplay(
                    backStack = backStack,
                    onBack = back,
                    entryProvider = entryProvider {
                        entry<SettingsRoute.Top> { SettingsTopPage(onNavigate = navigate, onBack = back) }
                        entry<SettingsRoute.Display> {
                            DisplaySettingsPage(settings.display, update, onNavigate = navigate, onBack = back)
                        }
                        entry<SettingsRoute.Animation> { AnimationSettingsPage(settings.display, update, onBack = back) }
                        entry<SettingsRoute.Timelines> { TimelineSettingsPage(settings, update, onBack = back) }
                        entry<SettingsRoute.Posting> { PostingSettingsPage(settings, update, onBack = back) }
                        entry<SettingsRoute.Confirmations> {
                            ConfirmationSettingsPage(settings.confirmations, update, onBack = back)
                        }
                    },
                    transitionSpec = { settingsPushTransition() },
                    popTransitionSpec = { settingsPopTransition() },
                    // 予測型「戻る」はジェスチャの進み具合でこのアニメーションを動かす
                    predictivePopTransitionSpec = { settingsPopTransition() },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

/*
 * ページの切り替え (AOSP の設定画面と同じスライド)。進むときは新しいページが右端から全体で入り、元のページは
 * 左へ少しずれながら薄くなる。戻るときはその逆 (今のページが右へ抜け、下のページが左から戻る)。
 */

/** Material の emphasized の曲線 (AOSP のページ遷移と同じ)。 */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val TransitionMillis = 450

/** 下になるページがずれる量 (幅に対する割合)。 */
private const val UnderlapFraction = 4

private fun <T> slideSpec() = tween<T>(TransitionMillis, easing = Emphasized)

private fun settingsPushTransition(): ContentTransform =
    slideInHorizontally(slideSpec()) { it } togetherWith
        (slideOutHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeOut(slideSpec()))

/** 戻るときは抜けていく今のページを上に描く (戻り先のページを下に置く)。 */
private fun settingsPopTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(slideSpec()) { -it / UnderlapFraction } + fadeIn(slideSpec()),
    initialContentExit = slideOutHorizontally(slideSpec()) { it },
    targetContentZIndex = -1f,
)

/** 設定画面を開く。 */
fun Context.openSettings() {
    startActivity(SettingsActivity.newIntent(this))
}
