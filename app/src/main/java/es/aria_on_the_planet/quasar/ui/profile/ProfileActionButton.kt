package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.FollowState
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog

/**
 * プロフィールのヘッダのボタン。自分ならプロフィール編集 ([ProfileEditScreen] を開く)、他人ならフォローの状態 ([FollowState]) に応じて
 * Follow (鍵アカウントは Request to follow = リクエスト送信) / Requested (送信済み) / Following /
 * Unblock (ブロック中。error 色の塗り) / Blocked (ブロックされている。押せない error 色の枠線と文字)。
 * フォロー・リクエスト送信・解除・リクエストの取り下げは、どれも確認ダイアログを経てから [onSetFollowing] を呼ぶ。
 * 引っ越し済みのアカウント ([Profile.movedTo]) は新たにフォローできない (フォロー中・リクエスト中なら解除・取り下げはできる)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileActionButton(
    state: ProfileButtonState,
    profile: Profile,
    onSetFollowing: (follow: Boolean) -> Unit,
    onUnblock: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    edit: ProfileEdit? = null,
) {
    when (state) {
        ProfileButtonState.None -> Unit

        // 編集用の原文を読んでいる間は押せず、LoadingIndicator を出す
        ProfileButtonState.EditProfile ->
            OutlinedButton(onClick = onEditProfile, modifier = modifier, enabled = edit == null, shapes = ButtonDefaults.shapes()) {
                if (edit == ProfileEdit.Loading) {
                    LoadingIndicator(Modifier.size(ButtonDefaults.IconSize), color = LocalContentColor.current)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(R.string.profile_edit))
            }

        is ProfileButtonState.Follow -> FollowButton(state, profile, onSetFollowing, onUnblock, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FollowButton(
    state: ProfileButtonState.Follow,
    profile: Profile,
    onSetFollowing: (follow: Boolean) -> Unit,
    onUnblock: () -> Unit,
    modifier: Modifier,
) {
    // 確認中の操作 (true = フォロー / リクエスト送信、false = 解除 / リクエスト取り下げ)。null なら確認していない
    var confirming by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val relationship = state.relationship
    val followState = relationship.followState
    val enabled = !state.isUpdating
    val shapes = ButtonDefaults.shapes()

    when (followState) {
        // 引っ越し済みのアカウントはフォローさせない (引っ越し先をフォローしてもらう)
        FollowState.NotFollowing if profile.movedTo != null ->
            Button(onClick = {}, modifier = modifier, enabled = false, shapes = shapes) {
                Text(stringResource(R.string.profile_follow))
            }

        FollowState.NotFollowing ->
            Button(onClick = { confirming = true }, modifier = modifier, enabled = enabled, shapes = shapes) {
                Text(
                    stringResource(
                        when {
                            // 鍵アカウントへはフォローではなくフォローリクエストの送信になる
                            profile.user.locked -> R.string.profile_request_follow
                            relationship.followedBy -> R.string.profile_follow_back
                            else -> R.string.profile_follow
                        },
                    ),
                )
            }

        FollowState.Requested ->
            OutlinedButton(onClick = { confirming = false }, modifier = modifier, enabled = enabled, shapes = shapes) {
                Text(stringResource(R.string.profile_follow_requested))
            }

        FollowState.Following ->
            OutlinedButton(onClick = { confirming = false }, modifier = modifier, enabled = enabled, shapes = shapes) {
                Text(stringResource(R.string.profile_following_button))
            }

        // ブロック中はフォローできない。ブロックしていることに気づけるよう error 色の塗りで目立たせる。
        // 解除は確認しない (取り返しのつかない操作ではない)
        FollowState.Blocking ->
            Button(
                onClick = onUnblock,
                modifier = modifier,
                enabled = enabled,
                shapes = shapes,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.account_unblock))
            }

        // ブロックされている。押せないが、無効のグレーではなく error 色の枠線と文字で「Blocked」と出して理由を示す
        FollowState.BlockedBy ->
            OutlinedButton(
                onClick = {},
                modifier = modifier,
                enabled = false,
                shapes = shapes,
                colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(ButtonDefaults.outlinedButtonBorder(enabled = true).width, MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.profile_blocked_by))
            }
    }

    val follow = confirming ?: return
    val acct = "@${profile.user.screenName}"
    val dismiss = { confirming = null }
    val confirm = {
        confirming = null
        onSetFollowing(follow)
    }
    when {
        follow && profile.user.locked -> ConfirmDialog(
            title = stringResource(R.string.profile_request_follow_confirm_title, acct),
            message = stringResource(R.string.profile_request_follow_confirm_message),
            confirmLabel = stringResource(R.string.profile_request_follow_send),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = confirm,
            onDismiss = dismiss,
        )

        follow -> ConfirmDialog(
            title = stringResource(R.string.profile_follow_confirm_title, acct),
            confirmLabel = stringResource(R.string.profile_follow),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = confirm,
            onDismiss = dismiss,
        )

        followState == FollowState.Requested -> ConfirmDialog(
            title = stringResource(R.string.profile_withdraw_request_title, acct),
            confirmLabel = stringResource(R.string.profile_withdraw_request),
            dismissLabel = stringResource(R.string.profile_keep_request),
            onConfirm = confirm,
            onDismiss = dismiss,
            destructive = true,
        )

        else -> ConfirmDialog(
            title = stringResource(R.string.profile_unfollow_confirm_title, acct),
            message = stringResource(R.string.profile_unfollow_confirm_message),
            confirmLabel = stringResource(R.string.profile_unfollow),
            dismissLabel = stringResource(R.string.cancel),
            onConfirm = confirm,
            onDismiss = dismiss,
            destructive = true,
        )
    }
}
