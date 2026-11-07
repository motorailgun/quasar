package es.aria_on_the_planet.quasar.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.UserName
import es.aria_on_the_planet.quasar.ui.personal.PersonalPostList

/**
 * タイムラインのヘッダの ≡ で左から出るドロワーの中身 (X のアプリの形)。
 *
 * ```
 * (icon)
 * 表示名
 * @acct@domain
 * ──────────
 * ⚙ Settings
 * 🔍 Search
 * ☆ Favourites / 🔖 Bookmarks / 👤+ Follow requests / ✉ Private mentions
 * ```
 *
 * 上のアカウントの部分 (アイコン・名前・@acct) のタップで自分のプロフィール。[account] が未取得ならその部分は出さない。
 *
 * 幅は項目に合わせる (M3 の既定は項目が幅いっぱいに広がるので最大の 360dp になる。下限の 240dp は M3 のまま)。
 * アカウントの部分は幅を決めるのに数えず、長い名前は省略する。
 */
@Composable
fun TimelineDrawerSheet(
    account: User?,
    onOpenProfile: (User) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPersonalPosts: (PersonalPostList) -> Unit,
    onOpenFollowRequests: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier.width(IntrinsicSize.Max)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (account != null) {
                Column(
                    Modifier
                        .then(IgnoreIntrinsicWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(role = Role.Button) { onOpenProfile(account) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    UserIcon(account, size = 48.dp)
                    Spacer(Modifier.height(12.dp))
                    UserName(
                        account,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "@${account.fullScreenName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
