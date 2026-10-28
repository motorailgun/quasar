package es.aria_on_the_planet.quasar.ui.profile

import android.text.TextUtils
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.icon.Symbols
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/**
 * 相手から自分へのフォローリクエスト (Discord のプロフィールの「〇〇 sent you a friend request」と同じ形)。
 * 「**名前** has requested to follow you」と、承認 (塗り) / 拒否 (枠線) のボタン。ボタンは Expressive の [ButtonGroup] で、
 * 押した方が膨らみ隣が縮む。
 *
 * @param answering 返事の送信中 (`true` = 承認、`false` = 拒否、`null` = 送信していない)。送信中はもう片方を無効にし、
 *   押した方は色を保ったまま (押しても何もしない) アイコンを [LoadingIndicator] にする。
 * @param onAnswer 承認 (`true`) / 拒否 (`false`)。拒否の確認は呼び出し側で行う。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FollowRequestCard(
    user: User,
    answering: Boolean?,
    onAnswer: (accept: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val name = "<b>${TextUtils.htmlEncode(user.nameOrAcct)}</b>"
            EmojiText(
                text = stringResource(R.string.profile_follow_request_message, name),
                emojis = user.emojis,
                isHtml = true,
                style = MaterialTheme.typography.bodyMedium,
            )
            val idle = answering == null
            val acceptInteraction = remember { MutableInteractionSource() }
            val rejectInteraction = remember { MutableInteractionSource() }
            val acceptLabel = stringResource(R.string.profile_follow_request_accept)
            val rejectLabel = stringResource(R.string.profile_follow_request_reject)
            ButtonGroup(
                // 2 つだけなので溢れない (必須の引数なので標準のものを渡しておく)
                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
            ) {
                customItem(
                    buttonGroupContent = {
                        Button(
                            onClick = { if (idle) onAnswer(true) },
                            enabled = answering != false,
                            shapes = ButtonDefaults.shapes(),
                            interactionSource = acceptInteraction,
                            modifier = Modifier.animateWidth(acceptInteraction),
                        ) {
                            AnswerContent(Symbols.Filled.Check, acceptLabel, loading = answering == true)
                        }
                    },
                    menuContent = { menu ->
                        DropdownMenuItem(
                            text = { Text(acceptLabel) },
                            onClick = {
                                menu.dismiss()
                                onAnswer(true)
                            },
                            enabled = idle,
                        )
                    },
                )
                customItem(
                    buttonGroupContent = {
                        OutlinedButton(
                            onClick = { if (idle) onAnswer(false) },
                            enabled = answering != true,
                            shapes = ButtonDefaults.shapes(),
                            interactionSource = rejectInteraction,
                            modifier = Modifier.animateWidth(rejectInteraction),
                        ) {
                            AnswerContent(Symbols.Outlined.Close, rejectLabel, loading = answering == false)
                        }
                    },
                    menuContent = { menu ->
                        DropdownMenuItem(
                            text = { Text(rejectLabel) },
                            onClick = {
                                menu.dismiss()
                                onAnswer(false)
                            },
                            enabled = idle,
                        )
                    },
                )
            }
        }
    }
}

/** ボタンの中身。送信中は前のアイコンを [LoadingIndicator] に替える (幅が変わらないよう文字は残す)。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnswerContent(icon: ImageVector, label: String, loading: Boolean) {
    if (loading) {
        LoadingIndicator(Modifier.size(ButtonDefaults.IconSize), color = LocalContentColor.current)
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(label)
}
