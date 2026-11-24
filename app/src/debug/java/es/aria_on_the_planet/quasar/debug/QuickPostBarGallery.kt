package es.aria_on_the_planet.quasar.debug

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.chrisbanes.haze.rememberHazeState
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.ui.component.blur.LocalSurfaceBlurEnabled
import es.aria_on_the_planet.quasar.ui.component.blur.surfaceBlurSource
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.composer.ComposerScreen
import es.aria_on_the_planet.quasar.ui.composer.QuickComposerHost
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator

/**
 * クイック投稿バーを一覧の上に浮かべる (見た目・位置・当たり判定の確認用)。本物と同じ [QuickComposerHost] で組む。
 * 下書きは本物の ComposerSession (ギャラリーのアカウントの DraftStore) だが、サーバは `.invalid` なのでアカウント・
 * 上限・絵文字は取れず (アイコンは取得前のプレースホルダ)、送信も失敗する。ピルを引き上げるとフルの投稿画面
 * ([ComposerScreen]) を重ねる。
 */
@Composable
internal fun QuickPostBarGallery(posts: List<Post>, onBack: () -> Unit) {
    val navigator = LocalNavigator.current
    val account = rememberGalleryAccount()
    LaunchedEffect(account) { account.draftStore.open() }
    val hazeState = rememberHazeState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    QuickComposerHost(
        drafts = account.draftStore,
        hazeState = hazeState,
        snackbarHostState = snackbarHostState,
        onMediaClick = navigator::openMedia,
    ) { barSpace ->
        DetailScaffold(
            onBack = onBack,
            listState = listState,
            isRefreshing = false,
            onRefresh = {},
            title = { Text(stringResource(GalleryScenario.QuickPostBar.title)) },
        ) {
            PostList(
                items = posts.map(TimelineItem::PostItem),
                state = listState,
                contentPadding = PaddingValues(bottom = barSpace),
                callbacks = PostCallbacks(onMediaClick = navigator::openMedia),
                modifier = Modifier.fillMaxSize().surfaceBlurSource(hazeState, LocalSurfaceBlurEnabled.current),
            )
        }
    }
}
