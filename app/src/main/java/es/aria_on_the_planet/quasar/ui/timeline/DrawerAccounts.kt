package es.aria_on_the_planet.quasar.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.account.StoredAccount
import es.aria_on_the_planet.quasar.data.account.StoredAccounts
import es.aria_on_the_planet.quasar.data.account.customEmoji
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.user.UserIcon
import es.aria_on_the_planet.quasar.ui.component.user.UserName
import es.aria_on_the_planet.quasar.ui.component.user.avatarShape

/**
 * ドロワーのアカウントの切り替え: [current] は使っているアカウント、[others] はそれ以外 (保存している順)。[onSwitch] はキー
 * (`AccountKey` の値) で切り替える、[onAdd] はアカウントの追加 (ログインの画面を開く)。どれも保存している表示用の情報
 * (`StoredProfile`) で描く (通信しない)。
 */
@Immutable
data class DrawerAccounts(
    val current: DrawerAccount,
    val others: List<DrawerAccount>,
    val onSwitch: (String) -> Unit,
    val onAdd: () -> Unit,
)

/**
 * 切り替えの 1 アカウント。[user] は保存している表示用の情報から組み立てたもの (まだ無ければ `null`。プレースホルダの
 * アイコンとサーバ名で出す)、[label] は `@user@host` (無ければサーバのホスト)。
 */
@Immutable
data class DrawerAccount(val key: String, val user: User?, val label: String)

/** 保存しているアカウントの一覧から、[currentKey] を使っているアカウントとしたドロワーの切り替え。一覧に無ければ `null`。 */
fun StoredAccounts.toDrawerAccounts(currentKey: String, onSwitch: (String) -> Unit, onAdd: () -> Unit): DrawerAccounts? {
    val current = accounts.firstOrNull { it.key.value == currentKey } ?: return null
    return DrawerAccounts(
        current = current.toDrawerAccount(),
        others = accounts.filter { it.key.value != currentKey }.map { it.toDrawerAccount() },
        onSwitch = onSwitch,
        onAdd = onAdd,
    )
}

/** 表示用の情報 (と ID) が揃っていなければ [DrawerAccount.user] は `null` (以前の保存から移した直後のアカウント)。 */
fun StoredAccount.toDrawerAccount(): DrawerAccount = DrawerAccount(
    key = key.value,
    user = profile?.let { profile ->
        val id = accountId ?: return@let null
        User(
            inServerId = id,
            screenName = profile.acct,
            displayName = profile.displayName,
            serverUrl = serverUrl,
            iconUrl = profile.avatarUrl,
            note = "",
            emojis = profile.emojis.map { it.customEmoji },
            iconStaticUrl = profile.avatarStaticUrl,
        )
    },
    label = label,
)

/** 上の行に小さく出す他のアカウントの数 (それより多ければ広げた一覧で選ぶ)。 */
private const val MaxSideIcons = 1

/**
 * ドロワーの上のアカウントの部分 (X のアプリの形)。
 *
 * ```
 * (icon 48)                                   (他 32)
 * 表示名                                         [v]
 * @acct@host
 * ```
 *
 * 名前の部分のタップで自分のプロフィール、右上の小さなアイコン (他のアカウントの先頭 1 つ。押せる範囲は 48dp) のタップで
 * そのアカウントに切り替え、[v] で全アカウントの一覧 ([DrawerAccountList]) を開閉する。[me] は使っているアカウント (投稿欄が取得した新しいもの。無ければ保存している情報)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DrawerAccountHeader(
    me: User?,
    accounts: DrawerAccounts?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenProfile: (User) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = me?.let { "@${it.fullScreenName}" } ?: accounts?.current?.label
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        // 上の余白は段階 5 までと同じ (外側 8dp + 12dp)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            AccountIcon(me, size = 48.dp, modifier = Modifier.clip(avatarShape()).clickable(enabled = me != null, role = Role.Button) {
                me?.let(onOpenProfile)
            })
            Spacer(Modifier.weight(1f))
            accounts?.others?.take(MaxSideIcons)?.forEach { other ->
                val description = stringResource(R.string.drawer_switch_to, other.label)
                // 押せる範囲は 48dp (最小のタッチ領域)。アイコンは大きなアイコンと下端を揃え、横は [v] と同じ位置
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { accounts.onSwitch(other.key) }
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AccountIcon(other.user, size = 32.dp)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(enabled = me != null, role = Role.Button) { me?.let(onOpenProfile) }
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (me != null) {
                    UserName(me, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                if (label != null) {
                    Text(
                        label,
                        style = if (me != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (accounts != null) {
                val rotation by animateFloatAsState(
                    if (expanded) 270f else 90f,
                    MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "accountsChevron",
                )
                IconButton(onClick = onToggleExpanded) {
                    Icon(
                        Symbols.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(
                            if (expanded) R.string.drawer_accounts_collapse else R.string.drawer_accounts_expand,
                        ),
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }
        }
    }
}

/**
 * 全アカウントの一覧 (使っているアカウントは印付きで押せない) +「アカウントを追加」。[visible] の間だけ出す (縦に開閉する)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DrawerAccountList(accounts: DrawerAccounts, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    ) {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AccountRow(accounts.current, current = true, onClick = null)
            accounts.others.forEach { other -> AccountRow(other, current = false, onClick = { accounts.onSwitch(other.key) }) }
            AddAccountRow(onClick = accounts.onAdd)
        }
    }
}

@Composable
private fun AccountRow(account: DrawerAccount, current: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIcon(account.user, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (account.user != null) {
                UserName(account.user, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                account.label,
                style = if (account.user != null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Icon(
                Symbols.Filled.CheckCircle,
                contentDescription = stringResource(R.string.drawer_account_current),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun AddAccountRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Symbols.Filled.Add, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.add_account), style = MaterialTheme.typography.titleSmall)
    }
}

/** アカウントのアイコン。表示用の情報がまだ無ければ同じ形のプレースホルダ (人のアイコン)。 */
@Composable
private fun AccountIcon(user: User?, size: Dp, modifier: Modifier = Modifier) {
    if (user != null) {
        UserIcon(user, modifier, size = size)
    } else {
        Box(
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, avatarShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Symbols.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
