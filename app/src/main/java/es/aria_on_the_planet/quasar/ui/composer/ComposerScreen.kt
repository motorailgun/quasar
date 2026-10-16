package es.aria_on_the_planet.quasar.ui.composer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.isEmpty
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.composer.Composer

/**
 * 投稿画面。トップバーの Post で送信し、成功したら [onClose] する。
 * 入力があるときの「戻る」/ × は破棄の確認を挟む。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposerScreen(
    viewModel: ComposerViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val composer = viewModel.composer
    val limits = state.limits
    val canSubmit by remember(composer, limits) {
        derivedStateOf { composer.toDraft().problems(limits).isEmpty() }
    }
    val isEmpty by remember(composer) { derivedStateOf { composer.toDraft().isEmpty } }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.posted) {
        if (state.posted) onClose()
    }
    val errorText = state.error?.let { stringResource(R.string.composer_post_failed, it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.errorShown()
        }
    }

    // 端末のメディアは Photo Picker で選ぶ (権限は要らない)。複数選択は 2 枚以上でしか使えないので、残り 1 枚なら単一選択
    val remaining = limits.maxMediaAttachments - composer.attachments.size
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = remaining.coerceAtLeast(2)),
    ) { uris -> viewModel.addMedia(uris) }
    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addMedia(listOf(uri))
    }
    val openMediaPicker = {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        // 動画は単独でしか付けられないので、既に画像があれば画像だけ選ばせる
        val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val input = if (composer.attachments.isEmpty()) request else imagesOnly
        if (remaining >= 2) pickMultiple.launch(input) else if (remaining == 1) pickSingle.launch(input)
    }

    val requestClose = { if (isEmpty) onClose() else confirmingDiscard = true }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = !isEmpty && !state.sending,
        onBackCompleted = { confirmingDiscard = true },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = requestClose, enabled = !state.sending) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.composer_close))
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::submit,
                        enabled = canSubmit && !state.sending,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (state.sending) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(stringResource(R.string.composer_post))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // キーボードを含めて避け、ツールバーをキーボードの直上に置く
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Composer(
            state = composer,
            account = state.account,
            limits = limits,
            emojis = state.emojis,
            enabled = !state.sending,
            autoFocus = true,
            onAttachMedia = openMediaPicker,
            onRemoveAttachment = { viewModel.removeAttachment(it.localId) },
            onRetryAttachment = { viewModel.retryAttachment(it.localId) },
            onAttachmentDescriptionChange = { attachment, description ->
                viewModel.setAttachmentDescription(attachment.localId, description)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.composer_discard_title)) },
            text = { Text(stringResource(R.string.composer_discard_message)) },
            confirmButton = {
                TextButton(onClick = onClose) { Text(stringResource(R.string.composer_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDiscard = false }) {
                    Text(stringResource(R.string.composer_keep_editing))
                }
            },
        )
    }
}
