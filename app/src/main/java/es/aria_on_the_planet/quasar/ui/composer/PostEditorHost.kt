package es.aria_on_the_planet.quasar.ui.composer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R

/**
 * 自分のポストの編集 ([PostEditStore.editor]) のフルの投稿画面を下から重ねて出し、編集・削除の失敗を Toast で知らせる。
 * `QuasarNavHost` が全画面の上に置く。
 */
@Composable
fun PostEditorHost(store: PostEditStore) {
    val context = LocalContext.current
    LaunchedEffect(store) {
        store.failures.collect { failure ->
            val text = when (failure) {
                is PostEditFailure.EditLoad -> R.string.post_edit_load_failed
                is PostEditFailure.Delete -> R.string.post_delete_failed
            }
            Toast.makeText(context, context.getString(text, failure.message), Toast.LENGTH_SHORT).show()
        }
    }

    val editor by store.editor.collectAsStateWithLifecycle()
    // 閉じるアニメーションの間も最後の投稿欄を出しておく
    var lastEditor by remember { mutableStateOf(editor) }
    if (editor != null) lastEditor = editor
    AnimatedVisibility(
        visible = editor != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        lastEditor?.let { session ->
            ComposerScreen(
                session = session,
                onClose = store::close,
                onFinish = store::close,
                keepDraftOnClose = false,
            )
        }
    }
}
