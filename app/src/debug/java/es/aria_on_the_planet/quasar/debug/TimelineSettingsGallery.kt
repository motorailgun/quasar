package es.aria_on_the_planet.quasar.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.ui.navigation.Route
import es.aria_on_the_planet.quasar.ui.settings.PostMenuSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.SettingsTopPage
import es.aria_on_the_planet.quasar.ui.settings.TimelineSettingsPage

/**
 * 設定のトップと、そこから開く Timelines (アカウントごと) / Post menu (端末共通) のページ。Post menu は本物の端末共通の設定を
 * 読み書きする (ポストのメニューの項目を変えて post-variety の︙で確かめられる)。Timelines はギャラリーのアカウント
 * ([rememberGalleryAccount]) の設定で、本物のアカウントの設定には触れない。他のページ・ログアウトは開かない。
 */
@Composable
internal fun TimelineSettingsGallery(onBack: () -> Unit) {
    val context = LocalContext.current
    val device = (context.applicationContext as QuasarApplication).container.deviceSettings
    val deviceSettings by device.settings.collectAsStateWithLifecycle()
    val accountStore = rememberGalleryAccount().account.settings
    val accountSettings by accountStore.settings.collectAsStateWithLifecycle()
    // ページはこの中で切り替える (ギャラリーの画面遷移には積まない)
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    when (page) {
        PostMenuPage -> deviceSettings?.let {
            PostMenuSettingsPage(it.hiddenPostMenuItems, device::launchUpdate, onBack = { page = null })
        }
        TimelinesPage -> accountSettings?.let {
            TimelineSettingsPage(it, accountStore::launchUpdate, onBack = { page = null })
        }
        else -> SettingsTopPage(
            account = "@gallery@gallery.invalid",
            translationAvailable = false,
            onNavigate = { route ->
                when (route) {
                    Route.SettingsPostMenu -> page = PostMenuPage
                    Route.SettingsTimelines -> page = TimelinesPage
                    else -> Unit
                }
            },
            onLogout = {},
            onBack = onBack,
        )
    }
}

private const val PostMenuPage = "post-menu"
private const val TimelinesPage = "timelines"
