package es.aria_on_the_planet.quasar.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.QuasarApplication
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.ui.QuasarAppContent
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiPicker
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.component.composer.ReplyContext
import es.aria_on_the_planet.quasar.ui.component.composer.ReplyTargetPreview
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.ui.mediaviewer.mediaViewerEntry
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator
import es.aria_on_the_planet.quasar.ui.navigation.Navigator
import es.aria_on_the_planet.quasar.ui.navigation.OverlaySceneStrategy
import es.aria_on_the_planet.quasar.ui.navigation.Route
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import es.aria_on_the_planet.quasar.ui.profile.ProfileHeader
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings

/**
 * debug ビルドだけにあるギャラリー。固定データ (`assets/gallery/`) で本物の部品を組み立てて表示する。
 * 条件に合う投稿をサーバで探さずに、見た目・動作・負荷を確かめるためのもの。
 *
 * adb から開く例 (表示設定は extra で上書きできる。[GalleryExtras]):
 * ```
 * adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar/.debug.DebugGalleryActivity \
 *     --es scenario animated-emoji --ez animate_images false --ei max_fps 60
 * ```
 * `scenario` が無ければシナリオの一覧を出す。`--activity-clear-task` を付けないと、開いている間に別のシナリオを
 * 指定しても切り替わらない (同じ Activity への intent は「届けた」扱いになるが、Android 16 の実機では
 * `onNewIntent` が呼ばれなかった)。
 */
class DebugGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as QuasarApplication).container
        val scenario = GalleryScenario.of(intent.getStringExtra(GalleryExtras.Scenario))
        val overrides = intent.extras
        setContent {
            QuasarAppContent(container) {
                val settings = LocalDisplaySettings.current.overriddenBy(overrides)
                // メディアビューアはアプリ本体と同じく、ギャラリーの画面に重ねて出す
                val backStack = rememberNavBackStack(GalleryHome)
                val context = LocalContext.current
                val navigator = remember(backStack) {
                    GalleryNavigator(context, backStack, onExit = ::finish)
                }
                CompositionLocalProvider(LocalDisplaySettings provides settings, LocalNavigator provides navigator) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = navigator::back,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        sceneStrategies = remember { listOf(OverlaySceneStrategy()) },
                        entryProvider = entryProvider {
                            entry<GalleryHome> {
                                if (scenario == null) {
                                    GalleryIndex(onBack = ::finish, onOpen = { startActivity(newIntent(this@DebugGalleryActivity, it, overrides)) })
                                } else {
                                    GalleryScreen(scenario, onBack = ::finish)
                                }
                            }
                            mediaViewerEntry(container, onBack = navigator::back)
                        },
                    )
                }
            }
        }
    }

    companion object {
        /** [scenario] を開く。[overrides] の表示設定の上書きは引き継ぐ。 */
        internal fun newIntent(context: Context, scenario: GalleryScenario, overrides: Bundle? = null): Intent =
            Intent(context, DebugGalleryActivity::class.java).apply {
                overrides?.let(::putExtras)
                putExtra(GalleryExtras.Scenario, scenario.id)
            }
    }
}

/** ギャラリーの画面 (バックスタックの一番下)。シナリオは Activity の extra で決まる。 */
@Serializable
private data object GalleryHome : NavKey

/** ギャラリーの遷移: メディアビューアだけ開く (プロフィール等への遷移はデータ層を通らないので無効)。 */
private class GalleryNavigator(
    private val context: Context,
    private val backStack: NavBackStack<NavKey>,
    private val onExit: () -> Unit,
) : Navigator {
    override fun openProfile(user: User) = Unit
    override fun openPost(post: Post) = Unit
    override fun openHashtag(name: String) = Unit
    override fun openFollowList(accountId: String, tab: FollowListTab) = Unit
    override fun openComposer() = Unit
    override fun openSettings() = Unit
    override fun openMedia(click: MediaClick) = openMedia(context, click, ::openMediaViewer)
    override fun openMediaViewer(args: MediaViewerArgs) {
        backStack.add(Route.MediaViewer(args))
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull() else onExit()
    }
}

@Composable
private fun GalleryIndex(onBack: () -> Unit, onOpen: (GalleryScenario) -> Unit) {
    val listState = rememberLazyListState()
    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = false,
        onRefresh = {},
        title = { Text(stringResource(R.string.gallery_name)) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(GalleryScenario.entries) { scenario ->
                ListItem(
                    headlineContent = { Text(stringResource(scenario.title)) },
                    supportingContent = { Text(scenario.id) },
                    modifier = Modifier.clickable { onOpen(scenario) },
                )
            }
        }
    }
}

@Composable
private fun GalleryScreen(scenario: GalleryScenario, onBack: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val text = remember(scenario) {
        context.assets.open("gallery/${scenario.fixture}").bufferedReader().use { it.readText() }
    }
    val listState = rememberLazyListState()
    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = false,
        onRefresh = {},
        title = { Text(stringResource(scenario.title)) },
    ) {
        when (scenario) {
            GalleryScenario.EmojiPicker -> {
                val emojis = remember(text) { GalleryFixtures.emojis(text) }
                Box(Modifier.fillMaxSize()) { EmojiPicker(emojis = emojis, onSelect = {}) }
            }

            GalleryScenario.Profile -> {
                val fixture = remember(text) { GalleryFixtures.profile(text) }
                PostList(
                    items = fixture.posts.map(TimelineItem::PostItem),
                    state = listState,
                    onMediaClick = navigator::openMedia,
                    header = { item(key = "header") { ProfileHeader(profile = fixture.profile, onOpenImage = navigator::openMediaViewer) } },
                )
            }

            GalleryScenario.Reply -> {
                // ポストごとに、クイック投稿バーの返信先 → フル画面の返信先 (+ 本文欄の代わりの区切り)
                val posts = remember(text) { GalleryFixtures.posts(text) }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(posts, key = { it.id }) { post ->
                        Column {
                            ReplyTargetPreview(
                                post = post,
                                onCancel = {},
                                onMediaClick = navigator::openMedia,
                                modifier = Modifier.padding(12.dp),
                            )
                            ReplyContext(post = post, myAcct = null, onCancel = {}, onMediaClick = navigator::openMedia)
                            HorizontalDivider(thickness = 4.dp)
                        }
                    }
                }
            }

            GalleryScenario.AnimatedEmoji,
            GalleryScenario.AnimatedAvatars,
            GalleryScenario.EmojiStress,
            GalleryScenario.PostVariety -> {
                val posts = remember(text) { GalleryFixtures.posts(text) }
                PostList(
                    items = posts.map(TimelineItem::PostItem),
                    state = listState,
                    onMediaClick = navigator::openMedia,
                )
            }
        }
    }
}
