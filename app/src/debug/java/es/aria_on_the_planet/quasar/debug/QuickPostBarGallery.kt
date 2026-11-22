package es.aria_on_the_planet.quasar.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.rememberHazeState
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.blur.LocalSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlurSource
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.composer.QuickPostBar
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * クイック投稿バーを一覧の上に浮かべる (見た目・位置の確認用)。下書きは本物の ComposerSession だが、サーバは
 * `.invalid` なのでアカウント・上限・絵文字は取れず (アイコンは取得前のプレースホルダ)、送信も失敗する。
 */
@Composable
internal fun QuickPostBarGallery(posts: List<Post>, onBack: () -> Unit) {
    val navigator = LocalNavigator.current
    val account = rememberGalleryAccount()
    val session = remember(account) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope to account.composerSession(scope = scope, editing = null, onPublished = {})
    }
    DisposableEffect(session) {
        onDispose { session.first.cancel() }
    }
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = false,
        onRefresh = {},
        title = { Text(stringResource(GalleryScenario.QuickPostBar.title)) },
    ) {
        Box(Modifier.fillMaxSize()) {
            PostList(
                items = posts.map(TimelineItem::PostItem),
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
                callbacks = PostCallbacks(onMediaClick = navigator::openMedia),
                modifier = Modifier.fillMaxSize().surfaceBlurSource(hazeState, LocalSurfaceBlurEnabled.current),
            )
            QuickPostBar(
                session = session.second,
                hazeState = hazeState,
                onExpand = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(12.dp),
            )
        }
    }
}
