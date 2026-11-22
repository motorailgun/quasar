package es.aria_on_the_planet.quasar.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.personal.PersonalPostList

/**
 * タイムラインのヘッダの ≡ で左から出るドロワーの中身 (X のアプリの形)。
 *
 * ```
 * (icon)                (他のアカウント)
 * 表示名                          [v]
 * @acct@domain
 * (開くと全アカウントの一覧 + アカウントを追加)
 * ──────────
 * ⚙ Settings
 * 🔍 Search
 * ☆ Favourites / 🔖 Bookmarks / 👤+ Follow requests / ✉ Private mentions
 * ```
 *
 * 上のアカウントの部分 ([DrawerAccountHeader]): 名前の部分のタップで自分のプロフィール、右上の他のアカウントの小さな
 * アイコンのタップで切り替え、[v] で全アカウントの一覧 ([DrawerAccountList]。+「アカウントを追加」) を開閉する。
 * [account] は投稿欄が取得した使っているアカウント (無ければ [accounts] の保存している情報)。
 * ドロワーは閉じていてもコンポジションに残るので、閉じ切ったら ([closed]) 一覧も閉じる (開き直すと閉じている)。
 *
 * 幅は項目に合わせる (M3 の既定は項目が幅いっぱいに広がるので最大の 360dp になる。下限の 240dp は M3 のまま)。
 * アカウントの部分は幅を決めるのに数えず、長い名前は省略する。
 */
@Composable
fun TimelineDrawerSheet(
    account: User?,
    accounts: DrawerAccounts?,
    onOpenProfile: (User) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonalPosts: (PersonalPostList) -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenSearch: () -> Unit,
    closed: Boolean,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier.width(IntrinsicSize.Max)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (account != null || accounts != null) {
                // アカウントの部分は幅を決めるのに数えない (長い名前・アカウントの数でドロワーが広がらない)
                var expanded by remember { mutableStateOf(false) }
                LaunchedEffect(closed) { if (closed) expanded = false }
                Column(Modifier.then(IgnoreIntrinsicWidth).fillMaxWidth()) {
                    DrawerAccountHeader(
                        me = account ?: accounts?.current?.user,
                        accounts = accounts,
                        expanded = expanded,
                        onToggleExpanded = { expanded = !expanded },
                        onOpenProfile = onOpenProfile,
                    )
                    if (accounts != null) DrawerAccountList(accounts, visible = expanded)
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }
            DrawerItem(R.string.settings, Symbols.Outlined.Settings, onOpenSettings)
            DrawerItem(R.string.search_hint, Symbols.Outlined.Search, onOpenSearch)
            DrawerItem(R.string.profile_tab_favourites, Symbols.Outlined.Star) { onOpenPersonalPosts(PersonalPostList.Favourites) }
            DrawerItem(R.string.profile_tab_bookmarks, Symbols.Outlined.Bookmark) { onOpenPersonalPosts(PersonalPostList.Bookmarks) }
            DrawerItem(R.string.settings_follow_requests, Symbols.Outlined.PersonAdd, onOpenFollowRequests)
            DrawerItem(R.string.private_mentions, Symbols.Outlined.Mail) { onOpenPersonalPosts(PersonalPostList.PrivateMentions) }
        }
    }
}

@Composable
private fun DrawerItem(@StringRes label: Int, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(label)) },
        icon = { Icon(icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** 親の intrinsic な幅の問い合わせに 0 を返す (測るときはそのまま)。 */
private object IgnoreIntrinsicWidth : LayoutModifier {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = 0
}
