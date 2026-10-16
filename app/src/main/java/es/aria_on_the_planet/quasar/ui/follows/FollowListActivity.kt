package es.aria_on_the_planet.quasar.ui.follows

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.profile.openProfile

/** アカウントのフォロー / フォロワー一覧。[openFollowList] で起動する。 */
class FollowListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
        val initialTab = intent.getStringExtra(EXTRA_TAB)
            ?.let { name -> FollowListTab.entries.firstOrNull { it.name == name } }
            ?: FollowListTab.Following
        val session = container.sessionStore.session.value
        if (accountId == null || session == null) {
            finish()
            return
        }
        setContent {
            QuasarAppContent(container) {
                val viewModel: FollowListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { FollowListViewModel(session, accountId, container.accountRepository) }
                    },
                )
                FollowListScreen(
                    viewModel = viewModel,
                    initialTab = initialTab,
                    onBack = ::finish,
                    onUserClick = ::openProfile,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "account_id"
        private const val EXTRA_TAB = "tab"

        fun newIntent(context: Context, accountId: String, tab: FollowListTab): Intent =
            Intent(context, FollowListActivity::class.java)
                .putExtra(EXTRA_ACCOUNT_ID, accountId)
                .putExtra(EXTRA_TAB, tab.name)
    }
}

/** [accountId] (接続中サーバ内の ID) のフォロー / フォロワー一覧を [tab] を選んだ状態で開く。 */
fun Context.openFollowList(accountId: String, tab: FollowListTab) {
    startActivity(FollowListActivity.newIntent(this, accountId, tab))
}
