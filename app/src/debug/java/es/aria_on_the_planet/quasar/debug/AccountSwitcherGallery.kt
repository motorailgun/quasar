package es.aria_on_the_planet.quasar.debug

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.data.account.StoredAccount
import es.aria_on_the_planet.quasar.data.account.StoredAccounts
import es.aria_on_the_planet.quasar.ui.timeline.TimelineDrawerSheet
import es.aria_on_the_planet.quasar.ui.timeline.toDrawerAccounts

/** 出すアカウントの数 (固定データの先頭から)。全部は「長い名前・絵文字・表示用の情報の無いアカウント」を含む。 */
private val AccountCounts = listOf(1, 2, Int.MAX_VALUE)

/**
 * ドロワーのアカウントの切り替え。本物の [TimelineDrawerSheet] を、固定データの保存しているアカウント ([accounts]) で出す。
 * 上のチップでアカウントの数を変える。切り替えは手元だけ (使っているアカウントが変わる)、追加・ほかの項目は Toast。
 */
@Composable
internal fun AccountSwitcherGallery(accounts: List<StoredAccount>, onBack: () -> Unit) {
    val context = LocalContext.current
    var count by remember { mutableIntStateOf(AccountCounts.last()) }
    val shown = accounts.take(count)
    var currentKey by remember { mutableStateOf(accounts.first().key.value) }
    val current = currentKey.takeIf { key -> shown.any { it.key.value == key } } ?: shown.first().key.value
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val drawerAccounts = StoredAccounts(shown).toDrawerAccounts(
        currentKey = current,
        onSwitch = { currentKey = it },
        onAdd = { toast("Add account") },
    )
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountCounts.forEach { n ->
                FilterChip(
                    selected = count == n,
                    onClick = { count = n },
                    label = { Text(if (n == Int.MAX_VALUE) "All (${accounts.size})" else "$n") },
                )
            }
            FilterChip(selected = false, onClick = onBack, label = { Text("Close") })
        }
        Box(Modifier.fillMaxSize()) {
            // 数を変えたら開閉の状態も作り直す
            key(count) {
                TimelineDrawerSheet(
                    account = null,
                    accounts = drawerAccounts,
                    onOpenProfile = { toast("Open profile: @${it.screenName}") },
                    onOpenSettings = { toast("Settings") },
                    onOpenPersonalPosts = { toast(it.name) },
                    onOpenFollowRequests = { toast("Follow requests") },
                    onOpenSearch = { toast("Search") },
                    closed = false,
                )
            }
        }
    }
}
