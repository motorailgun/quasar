package es.aria_on_the_planet.quasar.ui.component.post

import android.content.ClipData
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.localWebUrl
import es.aria_on_the_planet.quasar.domain.model.remoteWebUrl
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionConfirmDialog
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionMenuItems
import es.aria_on_the_planet.quasar.ui.component.account.AccountMenuTarget
import es.aria_on_the_planet.quasar.ui.component.account.LocalAccountActions
import es.aria_on_the_planet.quasar.ui.component.account.OpenInBrowserMenuItems
import es.aria_on_the_planet.quasar.ui.component.account.ProvisionalRelationship
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.text.postPlainText
import es.aria_on_the_planet.quasar.ui.component.menu.ActionMenu
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import es.aria_on_the_planet.quasar.ui.component.menu.MenuItemText

/** メニューを開いたときに読む相手の状態。 */
private sealed interface TargetState {
    data object Loading : TargetState
    data object Failed : TargetState
    data class Loaded(val target: AccountMenuTarget) : TargetState
}

/**
 * ポストのメニュー (︙)。ブラウザで開く (リモートのポストなら元のサーバで開くも)、本文をコピー、他人のポストなら投稿者の
 * ミュート・ブロック・ブーストの非表示 (フォロー中のみ)、自分のポストなら編集・削除 (削除は error 色、設定に従って確認する)。
 * 編集・削除は [LocalOwnPostActions] で、どの画面 (タイムライン・ポスト詳細等) でも同じ。
 * 投稿者との関係は開くたびに読み (キャッシュが新しければそれ)、読み込むまでは仮の関係 ([ProvisionalRelationship]) で出す (自分の ID がまだ分からず、関係の読み込みにも失敗したら項目ごと出さない)。ブロック・ミュートは確認してから行う。
 * [LocalAccountActions] が無い所 (ギャラリー等) では何も出さない。
 *
 * @param compact 投稿者の行 (公開範囲の右) に置く小さいボタン。`false` なら [IconButton] (ポスト詳細の投稿者の行)。
 *   編集の準備・削除の最中は、どちらもボタンの代わりに読み込み中を出し、押せない。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostMenu(
    post: Post,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val actions = LocalAccountActions.current ?: return
    val ownActions = LocalOwnPostActions.current
    val busy = ownActions?.busyStatusId?.collectAsStateWithLifecycle()?.value == post.statusId
    val confirmer = LocalActionConfirmer.current
    val author = post.author
    var expanded by remember { mutableStateOf(false) }
    var target by remember(author) { mutableStateOf<TargetState>(TargetState.Loading) }
    var confirming by remember { mutableStateOf<AccountAction?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current

    LaunchedEffect(expanded, author) {
        if (!expanded) return@LaunchedEffect
        target = try {
            TargetState.Loaded(actions.target(author))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load relationship", e)
            TargetState.Failed
        }
    }

    fun perform(action: AccountAction) {
        scope.launch {
            try {
                target = TargetState.Loaded(AccountMenuTarget.Other(actions.perform(author, action)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to ${action.type} (undo = ${action.undo})", e)
                Toast.makeText(
                    context,
                    context.getString(R.string.account_action_failed, e.message ?: e.toString()),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Box(modifier) {
        val label = stringResource(R.string.post_menu)
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (compact) {
            Box(
                Modifier
                    // 押せる範囲の余白の分だけ外にはみ出させ、行の高さ・アイコンの位置はアイコンだけのときと揃える
                    .layout { measurable, constraints ->
                        val padX = ActionPaddingHorizontal.roundToPx()
                        val padY = CompactMenuPaddingVertical.roundToPx()
                        val placeable = measurable.measure(Constraints())
                        layout(placeable.width - padX * 2, placeable.height - padY * 2) {
                            placeable.place(-padX, -padY)
                        }
                    }
                    .clip(CircleShape)
                    .clickable(enabled = !busy, role = Role.Button, onClickLabel = label) { expanded = true }
                    .padding(horizontal = ActionPaddingHorizontal, vertical = CompactMenuPaddingVertical),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(ActionIconSize))
                } else {
                    Icon(Symbols.Outlined.MoreVert, contentDescription = label, modifier = Modifier.size(ActionIconSize), tint = tint)
                }
            }
        } else {
            IconButton(onClick = { expanded = true }, enabled = !busy) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp))
                } else {
                    Icon(Symbols.Outlined.MoreVert, contentDescription = label, tint = tint)
                }
            }
        }
        ActionMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val dismiss = { expanded = false }
            // 自分のポストか: 関係を読み込んだ結果、まだなら覚えている自分の ID で決める
            val myId = actions.myAccountId
            val state = target
            val mine = if (state is TargetState.Loaded) state.target == AccountMenuTarget.Me else myId == author.inServerId
            if (mine && ownActions != null) {
                DropdownMenuItem(
                    text = { MenuItemText(stringResource(R.string.post_edit)) },
                    leadingIcon = { Icon(Symbols.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        dismiss()
                        ownActions.edit(post)
                    },
                )
                val error = MaterialTheme.colorScheme.error
                DropdownMenuItem(
                    text = { MenuItemText(stringResource(R.string.post_delete)) },
                    leadingIcon = { Icon(Symbols.Outlined.Delete, contentDescription = null) },
                    onClick = {
                        dismiss()
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            if (confirmer.confirm(ConfirmTarget.DeletePost)) ownActions.delete(post)
                        }
                    },
                    colors = MenuDefaults.itemColors(textColor = error, leadingIconColor = error),
                )
            }
            OpenInBrowserMenuItems(
                localUrl = post.localWebUrl(actions.serverUrl),
                remoteUrl = post.remoteWebUrl(),
                onDismiss = dismiss,
            )
            // 本文の無いポスト (メディアだけ等) には出さない
            val plainText = remember(post.text) { postPlainText(post.text) }
            if (plainText.isNotEmpty()) {
                DropdownMenuItem(
                    text = { MenuItemText(stringResource(R.string.post_copy_text)) },
                    leadingIcon = { Icon(Symbols.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        dismiss()
                        // Android 13 以降は OS がコピーしたことを知らせるので、アプリからは出さない
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, plainText))) }
                    },
                )
            }
            // 自分のポストにはアカウントへの操作を出さない。関係を読み込むまで (読めなかったときも) は仮の関係で出しておき、
            // フォローしているか次第のブーストの非表示はグレーアウトしておく。自分の ID がまだ分からなければ全部グレーアウト
            // (自分のポストにブロック等を押せる状態で出さないように)
            if (mine) return@ActionMenu
            val relationship = when (state) {
                is TargetState.Loaded -> (state.target as AccountMenuTarget.Other).relationship
                TargetState.Loading -> if (myId == null) null else ProvisionalRelationship
                TargetState.Failed -> if (myId == null) return@ActionMenu else ProvisionalRelationship
            }
            AccountActionMenuItems(
                user = author,
                relationship = relationship,
                followKnown = state is TargetState.Loaded,
                named = true,
                onAction = { action ->
                    dismiss()
                    if (action.needsConfirmation) confirming = action else perform(action)
                },
            )
        }
    }

    confirming?.let { action ->
        AccountActionConfirmDialog(
            user = author,
            action = action,
            onConfirm = {
                confirming = null
                perform(action)
            },
            onDismiss = { confirming = null },
        )
    }
}

/** [PostMenu] の compact のボタンの上下の余白 (押せる範囲)。 */
private val CompactMenuPaddingVertical = 6.dp

private const val TAG = "PostMenu"
