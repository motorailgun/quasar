package es.aria_on_the_planet.quasar.ui.component.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.DraftAttachment

/**
 * 添付メディアの代替テキストを編集するダイアログ。上にメディア全体 (クロップせず縦横比のまま)、下に入力欄 (残り文字数付き)。
 * [maxLength] を超えている間は保存できない。
 */
@Composable
fun AttachmentDescriptionDialog(
    attachment: DraftAttachment,
    maxLength: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val text = rememberTextFieldState(attachment.description)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    val length = text.text.length
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.composer_media_description_title)) },
        text = {
            Column {
                // 説明を書くための表示なので、切らずに全体を見せる。大きさは画像の縦横比に合わせ、角丸も画像に沿わせる
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = attachment.previewUri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(max = PreviewMaxHeight)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    state = text,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.composer_media_description_hint)) },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                    isError = length > maxLength,
                    supportingText = { Text("${maxLength - length}") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.text.toString().trim()) }, enabled = length <= maxLength) {
                Text(stringResource(R.string.composer_media_description_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_media_description_cancel)) }
        },
    )
}

private val PreviewMaxHeight: Dp = 280.dp
