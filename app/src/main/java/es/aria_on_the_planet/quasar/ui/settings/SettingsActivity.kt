package es.aria_on_the_planet.quasar.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.navigation.popTransition
import es.aria_on_the_planet.quasar.ui.navigation.pushTransition
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
                    transitionSpec = { pushTransition() },
                    popTransitionSpec = { popTransition() },
                    // 予測型「戻る」はジェスチャの進み具合でこのアニメーションを動かす
                    predictivePopTransitionSpec = { popTransition() },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }
}

/** 設定画面を開く。 */
fun Context.openSettings() {
    startActivity(SettingsActivity.newIntent(this))
}
