package es.aria_on_the_planet.quasar.ui.composer

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import es.aria_on_the_planet.quasar.ui.component.dialog.ConfirmDialog
import es.aria_on_the_planet.quasar.domain.model.isEmpty
import es.aria_on_the_planet.quasar.domain.model.problems
import es.aria_on_the_planet.quasar.ui.component.composer.Composer

/**
 * フルの投稿画面。トップバーの Post で送信する。
 *
 * @param session 書きかけの投稿 ([DraftStore] から受け取る。クイック投稿バーと共有)。
 * @param onClose 下書きを残したまま閉じる。
 * @param onFinish 投稿した・破棄した (下書きを終えて閉じる)。呼び出し側は [DraftStore.finish] する。
 * @param keepDraftOnClose `true` なら × / 戻るはそのまま [onClose] (クイック投稿バーに下書きが残るので確認は要らない)。
 *   `false` なら入力があるときは破棄の確認を出す (単独の `ComposerActivity`)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposerScreen(
    session: ComposerSession,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    keepDraftOnClose: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by session.state.collectAsStateWithLifecycle()
    val composer = session.composer
    val limits = state.limits
    val canSubmit by remember(composer, limits) {
        derivedStateOf { composer.toDraft().problems(limits).isEmpty() }
    }
    val isEmpty by remember(composer) { derivedStateOf { composer.toDraft().isEmpty } }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.posted) {
        if (state.posted) onFinish()
    }
    val errorText = state.error?.let { stringResource(R.string.composer_post_failed, it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            session.errorShown()
        }
    }

    val openMediaPicker = rememberMediaPicker(session)

    val requestClose = {
        if (keepDraftOnClose || isEmpty) onClose() else confirmingDiscard = true
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = (keepDraftOnClose || !isEmpty) && !state.sending,
        onBackCompleted = requestClose,
    )

    Scaffold(
        modifier = modifier,
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
                        onClick = session::submit,
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
            onRemoveAttachment = { session.removeAttachment(it.localId) },
            onRetryAttachment = { session.retryAttachment(it.localId) },
            onAttachmentDescriptionChange = { attachment, description ->
                session.setAttachmentDescription(attachment.localId, description)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }

    if (confirmingDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.composer_discard_title),
            message = stringResource(R.string.composer_discard_message),
            confirmLabel = stringResource(R.string.composer_discard),
            dismissLabel = stringResource(R.string.composer_keep_editing),
            onConfirm = onFinish,
            onDismiss = { confirmingDiscard = false },
            destructive = true,
        )
    }
}
