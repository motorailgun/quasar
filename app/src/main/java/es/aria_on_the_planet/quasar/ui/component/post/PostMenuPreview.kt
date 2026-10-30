package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.player.LocalInlinePlayback

/**
 * ポストのメニュー ([PostMenu]) の項目の上に出す、対象のポスト。[PostCardMode.Preview] の [PostCard] を、
 * 文字・アイコンはタイムラインと同じ大きさ、メディアだけ低くして出す (CW は開いたまま、長文は 6 行で切ってフェード)。
 * どのポストへの操作かを示すだけなので押せない (本文のリンク・メディア・インライン再生も効かない)。
 */
@Composable
internal fun PostMenuPreview(post: Post, modifier: Modifier = Modifier) {
    Box(modifier.semantics(mergeDescendants = true) {}) {
        // シートは画面と同じ CompositionLocal を受け継ぐので、そのままだとメディアをその場で再生できてしまう
        CompositionLocalProvider(LocalInlinePlayback provides null) {
            PostCard(
                post = post,
                mode = PostCardMode.Preview,
                mediaHeight = MediaHeight,
                // 横の余白は項目 (DropdownMenuItem) の中の余白と同じにして、アイコンの左端を項目と揃える
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        // 上に重ねてタップを中身に届かせない (シートのドラッグは親が受けるのでそのまま)
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

private val MediaHeight = 96.dp
