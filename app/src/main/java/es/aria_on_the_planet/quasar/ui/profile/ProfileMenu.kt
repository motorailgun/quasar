package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.localWebUrl
import es.aria_on_the_planet.quasar.domain.model.remoteWebUrl
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionMenuItems
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.ui.component.account.LocalAccountActions
import es.aria_on_the_planet.quasar.ui.component.account.OpenInBrowserMenuItems
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.menu.ActionMenu
import es.aria_on_the_planet.quasar.ui.component.scaffold.OverlayBarIconButton

/**
 * プロフィールのメニュー (トップバーの右端の︙。戻るボタンと同じ見た目)。ブラウザで開く (リモートのユーザーなら所属サーバで開くも)、
 * 他人ならブーストの非表示 (フォロー中のみ)・ミュート・ブロック。ブロック・ミュートは確認してから [onAccountAction]。
 * [LocalAccountActions] が無い所 (ギャラリー等) では出さない。
 */
@Composable
fun ProfileMenu(
    profile: Profile,
    state: ProfileButtonState,
    onAccountAction: (AccountAction) -> Unit,
    barAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val serverUrl = LocalAccountActions.current?.serverUrl ?: return
    val user = profile.user
    var expanded by remember { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf<AccountActionType?>(null) }
    Box(modifier) {
        OverlayBarIconButton(
            Symbols.Outlined.MoreVert,
            contentDescription = stringResource(R.string.post_menu),
            barAlpha = barAlpha,
            onClick = { expanded = true },
        )
        ActionMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val dismiss = { expanded = false }
            OpenInBrowserMenuItems(
                localUrl = user.localWebUrl(serverUrl),
                remoteUrl = user.remoteWebUrl(),
                onDismiss = dismiss,
            )
            // 自分・関係の取得前 / 失敗はアカウントへの操作を出さない
            val follow = state as? ProfileButtonState.Follow ?: return@ActionMenu
            AccountActionMenuItems(
                user = user,
                relationship = follow.relationship.takeUnless { follow.isUpdating },
                named = false,
                onAction = { action ->
                    dismiss()
                    if (action.needsConfirmation) confirming = action.type else onAccountAction(action)
                },
            )
        }
    }
    confirming?.let { type ->
        val action = AccountAction(type, undo = false)
        AccountActionConfirmDialog(
            user = user,
            action = action,
            onConfirm = {
                confirming = null
                onAccountAction(action)
            },
            onDismiss = { confirming = null },
        )
    }
}
