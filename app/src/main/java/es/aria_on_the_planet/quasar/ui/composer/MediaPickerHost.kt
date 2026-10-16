package es.aria_on_the_planet.quasar.ui.composer

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.ui.component.composer.MediaPickerSheet
import es.aria_on_the_planet.quasar.ui.component.composer.isEmbeddedPhotoPickerAvailable

/**
 * [session] にメディアを添付するピッカーを開く関数を返す (投稿画面・クイック投稿バー共通)。
 *
 * 使える端末では埋め込みのピッカーをシートで開き (最初は半分の高さ)、それ以外や埋め込みが失敗した後は
 * OS 標準の Photo Picker を別画面で開く。いずれも権限は要らない。
 *
 * 埋め込みのシートは別ウィンドウ (ダイアログ) として出るので、この関数を呼ぶ場所のレイアウトには影響しない。
 */
@Composable
fun rememberMediaPicker(session: ComposerSession): () -> Unit {
    val state by session.state.collectAsStateWithLifecycle()
    val composer = session.composer
    val limits = state.limits
    // 複数選択は 2 枚以上でしか使えないので、残り 1 枚なら単一選択
    val remaining = limits.maxMediaAttachments - composer.attachments.size
    val pickMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = remaining.coerceAtLeast(2)),
    ) { uris -> session.addMedia(uris) }
    val pickSingle = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) session.addMedia(listOf(uri))
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var embeddedFailed by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val openSystemPicker = {
        // 動画は単独でしか付けられないので、既に画像があれば画像だけ選ばせる
        val type = if (composer.attachments.isEmpty()) {
            ActivityResultContracts.PickVisualMedia.ImageAndVideo
        } else {
            ActivityResultContracts.PickVisualMedia.ImageOnly
        }
        val request = PickVisualMediaRequest(type)
        if (remaining >= 2) pickMultiple.launch(request) else if (remaining == 1) pickSingle.launch(request)
    }

    if (sheetOpen && isEmbeddedPhotoPickerAvailable()) {
        MediaPickerSheet(
            // 埋め込みのピッカーで選んだもの (content:// の URI) を選択済みとして渡す
            selected = remember { composer.attachments.mapNotNull { it.previewUri?.toUri() }.toSet() },
            maxItems = limits.maxMediaAttachments,
            onSelect = session::addMedia,
            onDeselect = session::removeMedia,
            onDismiss = { sheetOpen = false },
            onError = { error ->
                Log.w("MediaPicker", "Embedded photo picker failed; falling back to the system picker", error)
                sheetOpen = false
                embeddedFailed = true
                openSystemPicker()
            },
        )
    }

    return {
        if (isEmbeddedPhotoPickerAvailable() && !embeddedFailed) {
            keyboard?.hide()
            sheetOpen = true
        } else {
            openSystemPicker()
        }
    }
}
