package es.aria_on_the_planet.quasar.ui.component.account

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.webHostOf
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.menu.MenuItemText

/** アカウントへの操作の種類。 */
enum class AccountActionType { Block, Mute, HideBoosts }

/** アカウントへの操作。[undo] なら取り消し (ブロック解除・ミュート解除・ブーストを表示)。 */
data class AccountAction(val type: AccountActionType, val undo: Boolean) {
    /** 実行の前に確認するか (ブロック・ミュート)。 */
    val needsConfirmation: Boolean get() = !undo && type != AccountActionType.HideBoosts
}

/** [type] の操作が今オンか。 */
fun Relationship.isOn(type: AccountActionType): Boolean = when (type) {
    AccountActionType.Block -> blocking
    AccountActionType.Mute -> muting
    AccountActionType.HideBoosts -> !showingReblogs
}

/** メニューから見た相手。 */
sealed interface AccountMenuTarget {
    /** 自分 (ブロック等は出さない)。 */
    data object Me : AccountMenuTarget

    data class Other(val relationship: Relationship) : AccountMenuTarget
}

/**
 * ポストのメニューから使うアカウントへの操作と、Web のページを作るための接続中サーバ。
 * `QuasarNavHost` が提供し、無い所 (ギャラリー等) ではポストのメニューを出さない。
 */
@Stable
interface AccountActions {
    /** 接続中サーバの URL。 */
    val serverUrl: String

    /** ログイン中のアカウントの ID。まだ取得していなければ `null` (待たずに返す)。 */
    val myAccountId: String?

    /** [user] が自分か、自分との関係 (キャッシュが新しければそれ)。取れなければ例外。 */
    suspend fun target(user: User): AccountMenuTarget

    /** [action] を行い、更新後の関係を返す。一覧から消すのは各画面が関係の変化を受けて行う。 */
    suspend fun perform(user: User, action: AccountAction): Relationship
}

val LocalAccountActions = staticCompositionLocalOf<AccountActions?> { null }

/**
 * 関係を読み込む前に出す仮の関係。表示中のポストの投稿者をブロック・ミュートしていることはまず無いので、ミュート・ブロックを
 * 押せるようにしておく (読み込めたら置き換わる。ミュート・ブロックの API は済みでもそのまま成功するので、先に押されても困らない)。
 * フォローしているかは分からないので、ブーストの非表示は読み込むまで押せない ([AccountActionMenuItems] の `followKnown`)。
 */
val ProvisionalRelationship = Relationship(following = false, requested = false, followedBy = false)

/**
 * メニューの中のミュート / ブロック (error 色) / ブーストの非表示。読み込みの前後でメニューの高さが変わらないよう、項目は常に
 * 3 つ出し、使えないものはグレーアウトする。[relationship] が `null` (まだ分からない) なら全部、[followKnown] が `false`
 * (フォローしているか分からない) ならブーストの非表示を押せなくする。ブーストの非表示はフォロー中の相手にだけ押せる。
 *
 * @param named ポストのメニュー用に「@acct さんをブロック」の形にする。プロフィールでは「ブロック」だけ。
 * @param onAction 押されたとき。メニューを閉じる・確認するのは呼び出し側。
 */
@Composable
fun AccountActionMenuItems(
    user: User,
    relationship: Relationship?,
    named: Boolean,
    onAction: (AccountAction) -> Unit,
    followKnown: Boolean = true,
) {
    val known = relationship != null
    val current = relationship ?: ProvisionalRelationship
    val acct = user.screenName
    fun label(on: Int, onNamed: Int, off: Int, offNamed: Int, active: Boolean): Int =
        if (active) (if (named) offNamed else off) else (if (named) onNamed else on)

    val muting = current.muting
    DropdownMenuItem(
        text = {
            val res = label(
                R.string.account_mute, R.string.account_mute_named,
                R.string.account_unmute, R.string.account_unmute_named, muting,
            )
            MenuItemText(if (named) stringResource(res, acct) else stringResource(res))
        },
        leadingIcon = {
            Icon(if (muting) Symbols.Outlined.VolumeUp else Symbols.Outlined.VolumeOff, contentDescription = null)
        },
        onClick = { onAction(AccountAction(AccountActionType.Mute, undo = muting)) },
        enabled = known,
    )
    val blocking = current.blocking
    val error = MaterialTheme.colorScheme.error
    DropdownMenuItem(
        text = {
            val res = label(
                R.string.account_block, R.string.account_block_named,
                R.string.account_unblock, R.string.account_unblock_named, blocking,
            )
            MenuItemText(if (named) stringResource(res, acct) else stringResource(res))
        },
        leadingIcon = { Icon(Symbols.Outlined.Block, contentDescription = null) },
        onClick = { onAction(AccountAction(AccountActionType.Block, undo = blocking)) },
        enabled = known,
        // 解除は取り返しのつかない操作ではないので普通の色
        colors = if (blocking || !known) MenuDefaults.itemColors() else MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
    )
    // ブーストの非表示はフォロー中の相手にだけ意味がある (フォローし直す形で変えるので、フォロー外ではフォローになってしまう)
    val hidden = current.isOn(AccountActionType.HideBoosts)
    DropdownMenuItem(
        text = {
            val res = label(
                R.string.account_hide_boosts, R.string.account_hide_boosts_named,
                R.string.account_show_boosts, R.string.account_show_boosts_named, hidden,
            )
            MenuItemText(if (named) stringResource(res, acct) else stringResource(res))
        },
        leadingIcon = { Icon(Symbols.Outlined.Repeat, contentDescription = null) },
        onClick = { onAction(AccountAction(AccountActionType.HideBoosts, undo = hidden)) },
        enabled = known && followKnown && current.following,
    )
}

/** ブロック・ミュートの前の確認。[AccountAction.needsConfirmation] のものだけ出す。 */
@Composable
fun AccountActionConfirmDialog(
    user: User,
    action: AccountAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val acct = user.screenName
    when (action.type) {
        AccountActionType.Block -> ConfirmDialog(
            title = stringResource(R.string.account_block_confirm_title, acct),
            message = stringResource(R.string.account_block_confirm_message),
            confirmLabel = stringResource(R.string.account_block),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = true,
        )

        AccountActionType.Mute -> ConfirmDialog(
            title = stringResource(R.string.account_mute_confirm_title, acct),
            message = stringResource(R.string.account_mute_confirm_message),
            confirmLabel = stringResource(R.string.account_mute),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            destructive = true,
        )

        // 確認しない
        AccountActionType.HideBoosts -> Unit
    }
}

/**
 * メニューの「ブラウザで開く」(接続中サーバのページ [localUrl]) と、リモートなら「<ホスト> で開く」(元のサーバのページ
 * [remoteUrl])。[onDismiss] はメニューを閉じる。
 */
@Composable
fun OpenInBrowserMenuItems(localUrl: String, remoteUrl: String?, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    DropdownMenuItem(
        text = { MenuItemText(stringResource(R.string.menu_open_in_browser)) },
        leadingIcon = { Icon(Symbols.Outlined.OpenInBrowser, contentDescription = null) },
        onClick = {
            onDismiss()
            uriHandler.openUri(localUrl)
        },
    )
    val remote = remoteUrl ?: return
    val host = webHostOf(remote) ?: return
    DropdownMenuItem(
        text = { MenuItemText(stringResource(R.string.menu_open_on_server, host)) },
        leadingIcon = { Icon(Symbols.Outlined.Language, contentDescription = null) },
        onClick = {
            onDismiss()
            uriHandler.openUri(remote)
        },
    )
}
