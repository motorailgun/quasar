package es.aria_on_the_planet.quasar.ui.component.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 操作の前の確認ダイアログ (投稿の破棄、フォロー / フォロー解除など)。
 * 表示するかどうかは呼び出し側が持ち、[onConfirm] / [onDismiss] のどちらでも閉じる (状態を戻す) のは呼び出し側。
 *
 * @param message 補足の説明。`null` ならタイトルのみ。
 * @param destructive 取り消せない・失うものがある操作 (破棄・解除) なら、確定ボタンを error 色にする。
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
