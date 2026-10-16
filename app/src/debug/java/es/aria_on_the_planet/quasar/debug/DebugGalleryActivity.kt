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
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMediaViewer
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
                CompositionLocalProvider(LocalDisplaySettings provides settings) {
                    if (scenario == null) {
                        GalleryIndex(onBack = ::finish, onOpen = { startActivity(newIntent(this, it, overrides)) })
                    } else {
                        GalleryScreen(scenario, onBack = ::finish)
                    }
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
                    onMediaClick = context::openMedia,
                    header = { item(key = "header") { ProfileHeader(profile = fixture.profile, onOpenImage = context::openMediaViewer) } },
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
                                onMediaClick = context::openMedia,
                                modifier = Modifier.padding(12.dp),
                            )
                            ReplyContext(post = post, myAcct = null, onCancel = {}, onMediaClick = context::openMedia)
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
                    onMediaClick = context::openMedia,
                )
            }
        }
    }
}
