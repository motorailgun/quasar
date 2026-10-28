package es.aria_on_the_planet.quasar.debug

import android.content.Context
import es.aria_on_the_planet.quasar.ui.component.composer.isEmbeddedPhotoPickerAvailable
import es.aria_on_the_planet.quasar.ui.component.composer.MediaPickerSheet
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiPickerSheet
import es.aria_on_the_planet.quasar.domain.model.CustomEmoji
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.settings.ConfirmTarget
import es.aria_on_the_planet.quasar.ui.component.dialog.LocalActionConfirmer
import es.aria_on_the_planet.quasar.ui.profile.FollowRequestCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import es.aria_on_the_planet.quasar.ui.component.account.LocalAccountActions
import es.aria_on_the_planet.quasar.ui.component.composer.EmojiPicker
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.feed.TimelineItem
import es.aria_on_the_planet.quasar.ui.component.composer.ReplyContext
import es.aria_on_the_planet.quasar.ui.component.composer.ReplyTargetPreview
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsTab
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsContent
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsState
import es.aria_on_the_planet.quasar.ui.component.post.PostCallbacks
import es.aria_on_the_planet.quasar.ui.component.post.PostDetail
import es.aria_on_the_planet.quasar.ui.feed.FeedState
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.toggleOn
import es.aria_on_the_planet.quasar.domain.model.withAction
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import es.aria_on_the_planet.quasar.ui.mediaviewer.mediaViewerEntry
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.navigation.LocalNavigator
import es.aria_on_the_planet.quasar.ui.navigation.Navigator
import es.aria_on_the_planet.quasar.ui.navigation.OverlaySceneStrategy
import es.aria_on_the_planet.quasar.ui.navigation.Route
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.domain.model.NotificationType
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
import es.aria_on_the_planet.quasar.ui.feed.PostFeedState
import es.aria_on_the_planet.quasar.ui.profile.ProfileButtonState
import es.aria_on_the_planet.quasar.ui.profile.ProfileContent
import es.aria_on_the_planet.quasar.ui.profile.ProfileHeader
import es.aria_on_the_planet.quasar.ui.profile.ProfileScreenState
import es.aria_on_the_planet.quasar.ui.profile.ProfileState
import es.aria_on_the_planet.quasar.ui.profile.ProfileTab
import es.aria_on_the_planet.quasar.ui.profile.ProfileEdit
import es.aria_on_the_planet.quasar.ui.profile.ProfileEditor
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.ProfileField
import es.aria_on_the_planet.quasar.domain.model.ProfileFieldSource
import es.aria_on_the_planet.quasar.domain.model.ProfileLimits
import es.aria_on_the_planet.quasar.domain.model.ProfileSource
import es.aria_on_the_planet.quasar.domain.model.normalized
import androidx.core.text.HtmlCompat
import android.text.TextUtils
import es.aria_on_the_planet.quasar.ui.reactions.AccountReactionsContent
import es.aria_on_the_planet.quasar.ui.reactions.AccountReactionsState

/**
 * debug ビルドだけにあるギャラリー。固定データ (`assets/gallery/`) で本物の部品を組み立てて表示する。
 * 条件に合う投稿をサーバで探さずに、見た目・動作・負荷を確かめるためのもの。
 *
 * adb から開く例 (表示設定は extra で上書きできる。[GalleryExtras]):
 * ```
 * adb shell am start --activity-clear-task -n es.aria_on_the_planet.quasar/.debug.DebugGalleryActivity \
 *     --es scenario animated-emoji --ez animate_images false --ei max_fps 60 --ef ui_scale 1.2
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
            // 表示設定の上書き (倍率を含む) は QuasarAppContent に渡す
            QuasarAppContent(container, display = { it.overriddenBy(overrides) }) {
                // メディアビューアはアプリ本体と同じく、ギャラリーの画面に重ねて出す
                val backStack = rememberNavBackStack(GalleryHome)
                val context = LocalContext.current
                val navigator = remember(backStack) {
                    GalleryNavigator(context, backStack, onExit = ::finish)
                }
                val accountActions = remember { GalleryAccountActions() }
                CompositionLocalProvider(LocalNavigator provides navigator, LocalAccountActions provides accountActions) {
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
    override fun openAccountReactions(account: User, type: NotificationType, posts: List<Post>) = Unit
    override fun openPostReactions(post: Post, tab: PostReactionsTab) = Unit
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
                    supportingContent = { Text(scenario.id) },
                    modifier = Modifier.clickable { onOpen(scenario) },
                ) {
                    Text(stringResource(scenario.title))
                }
            }
        }
    }
}

/** ギャラリーのプロフィールの編集用の原文 (本物はサーバの `source`。ここでは表示用の HTML から文字だけ取る)。 */
private fun galleryProfileSource(profile: Profile) = ProfileSource(
    displayName = profile.user.displayName,
    note = HtmlCompat.fromHtml(profile.user.note, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim(),
    fields = profile.fields.map {
        ProfileFieldSource(it.name, HtmlCompat.fromHtml(it.value, HtmlCompat.FROM_HTML_MODE_COMPACT).toString())
    },
)

/** 保存の代わりに、編集した内容を表示に当てる (本文は改行だけ HTML にする)。 */
private fun Profile.applyingGalleryEdit(source: ProfileSource): Profile {
    val saved = source.normalized()
    return copy(
        user = user.copy(
            displayName = saved.displayName,
            note = saved.note.takeIf { it.isNotBlank() }
                ?.let { "<p>" + TextUtils.htmlEncode(it).replace("\n", "<br>") + "</p>" }
                .orEmpty(),
        ),
        fields = saved.fields.map { ProfileField(it.name, TextUtils.htmlEncode(it.value), verifiedAt = null) },
    )
}

@Composable
private fun GalleryScreen(scenario: GalleryScenario, onBack: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val text = remember(scenario) {
        context.assets.open("gallery/${scenario.fixture}").bufferedReader().use { it.readText() }
    }
    if (scenario == GalleryScenario.MyProfile || scenario == GalleryScenario.OtherProfile) {
        // タブ付きの画面を骨組みごと本物で出す (一覧は固定データ。追加読み込み・更新は無い)
        val fixture = remember(text) { GalleryFixtures.profile(text) }
        // お気に入り・ブックマークは post-variety のポスト (ブックマークは逆順)
        val favourites = remember {
            val variety = context.assets.open("gallery/post-variety.json").bufferedReader().use { it.readText() }
            GalleryFixtures.posts(variety).map { it.copy(viewer = it.viewer.copy(favourited = true)) }
        }
        var tab by remember { mutableStateOf(ProfileTab.Posts) }
        var relationship by remember { mutableStateOf(Relationship(following = true, requested = false, followedBy = true)) }
        // プロフィールの編集は手元だけ: 原文の読み込み・保存はそれぞれ待ってから、保存は表示に当てるだけ
        var profile by remember(fixture) { mutableStateOf(fixture.profile) }
        var edit by remember { mutableStateOf<ProfileEdit?>(null) }
        val scope = rememberCoroutineScope()
        fun feed(posts: List<Post>) = PostFeedState(items = posts.map(TimelineItem::PostItem), loaded = true, endReached = true)
        val state = ProfileScreenState(
            profile = ProfileState(profile = profile),
            posts = feed(fixture.posts),
            media = feed(fixture.posts.filter { it.mediaAttachments.isNotEmpty() }),
            favourites = feed(favourites),
            bookmarks = feed(favourites.reversed().map { it.copy(viewer = it.viewer.copy(bookmarked = true)) }),
            tab = tab,
            button = if (scenario == GalleryScenario.MyProfile) {
                ProfileButtonState.EditProfile
            } else {
                ProfileButtonState.Follow(relationship)
            },
            edit = edit,
        )
        ProfileContent(
            state = state,
            onBack = onBack,
            onRefresh = {},
            onSelectTab = { tab = it },
            onLoadMore = {},
            onSetFollowing = {},
            onAnswerFollowRequest = {},
            onAccountAction = { relationship = relationship.applying(it) },
            callbacks = PostCallbacks(onMediaClick = navigator::openMedia),
            onFollowingClick = {},
            onFollowersClick = {},
            onOpenImage = navigator::openMediaViewer,
            onStartEdit = {
                edit = ProfileEdit.Loading
                scope.launch {
                    delay(600)
                    edit = ProfileEdit.Editing(
                        editor = ProfileEditor(galleryProfileSource(profile)),
                        // 4.7 以降の既定と同じ (表示名 40 文字)
                        limits = ProfileLimits(maxDisplayNameCharacters = 40),
                        emojis = profile.user.emojis,
                    )
                }
            },
            onCancelEdit = { edit = null },
            onSaveEdit = {
                val editing = edit as? ProfileEdit.Editing
                if (editing != null) {
                    edit = editing.copy(isSaving = true)
                    scope.launch {
                        delay(2000)
                        profile = profile.applyingGalleryEdit(editing.editor.toSource())
                        edit = null
                    }
                }
            },
        )
        return
    }
    if (scenario == GalleryScenario.PostDetail) {
        PostDetailGallery(text, onBack)
        return
    }
    if (scenario == GalleryScenario.AccountReactions) {
        // 画面の骨組みごと本物を使う (トップバーの文言も確かめたいので、ギャラリーの DetailScaffold には載せない)
        val fixture = remember(text) { GalleryFixtures.profile(text) }
        AccountReactionsContent(
            state = AccountReactionsState(account = fixture.profile.user, posts = fixture.posts.take(3), loaded = true),
            type = NotificationType.Favourite,
            postCount = 3,
            onBack = onBack,
            onRefresh = {},
            callbacks = PostCallbacks(onMediaClick = navigator::openMedia, onHashtagClick = {}),
        )
        return
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

            GalleryScenario.PickerSheets -> {
                val emojis = remember(text) { GalleryFixtures.emojis(text) }
                PickerSheets(emojis)
            }

            GalleryScenario.Profile -> {
                val fixture = remember(text) { GalleryFixtures.profile(text) }
                PostList(
                    items = fixture.posts.map(TimelineItem::PostItem),
                    state = listState,
                    callbacks = PostCallbacks(onMediaClick = navigator::openMedia),
                    header = { item(key = "header") { ProfileHeader(profile = fixture.profile, onOpenImage = navigator::openMediaViewer) } },
                )
            }

            GalleryScenario.FollowRequest -> {
                // 押すと送信中の表示になり、もう一度押す代わりに 2 秒で消えて「返事済み」になる (データ層は通らない)
                val fixture = remember(text) { GalleryFixtures.profile(text) }
                var answering by remember { mutableStateOf<Boolean?>(null) }
                var answered by remember { mutableStateOf(false) }
                val confirmer = LocalActionConfirmer.current
                val scope = rememberCoroutineScope()
                LaunchedEffect(answering) {
                    if (answering != null) {
                        delay(2000)
                        answering = null
                        answered = true
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item(key = "header") {
                        ProfileHeader(
                            profile = fixture.profile,
                            relationship = Relationship(following = false, requested = false, followedBy = answered, requestedBy = !answered),
                            followRequest = {
                                AnimatedVisibility(visible = !answered, exit = shrinkVertically() + fadeOut()) {
                                    FollowRequestCard(
                                        user = fixture.profile.user,
                                        answering = answering,
                                        onAnswer = { accept ->
                                            scope.launch {
                                                if (accept || confirmer.confirm(ConfirmTarget.RejectFollowRequest)) answering = accept
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
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

            GalleryScenario.AccountReactions,
            GalleryScenario.PostDetail,
            GalleryScenario.MyProfile,
            GalleryScenario.OtherProfile -> Unit

            GalleryScenario.AnimatedEmoji,
            GalleryScenario.AnimatedAvatars,
            GalleryScenario.EmojiStress,
            GalleryScenario.PostVariety -> {
                val posts = remember(text) { GalleryFixtures.posts(text) }
                PostList(
                    items = posts.map(TimelineItem::PostItem),
                    state = listState,
                    callbacks = PostCallbacks(onMediaClick = navigator::openMedia),
                )
            }
        }
    }
}

/**
 * ポスト詳細の本体を `reactionDetail` で並べる。ブースト等は手元で反映するだけ (件数の動きも確かめられる)。
 * 件数のタップでリアクションしたユーザーの一覧 ([PostReactionsContent]) に切り替え、「戻る」で戻る。
 */
@Composable
private fun PostDetailGallery(text: String, onBack: () -> Unit) {
    val navigator = LocalNavigator.current
    var posts by remember(text) { mutableStateOf(GalleryFixtures.posts(text)) }
    var reactions by remember { mutableStateOf<PostReactionsTab?>(null) }
    reactions?.let { tab ->
        val users = remember(posts) { posts.map { it.author }.distinctBy { it.inServerId } }
        fun feed(users: List<User>) = FeedState(items = users, loaded = true, endReached = true)
        NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = { reactions = null })
        PostReactionsContent(
            // ブーストは空 (空の表示の確認)、お気に入りは投稿者たち
            state = PostReactionsState(boosts = feed(emptyList()), favourites = feed(users)),
            initialTab = tab,
            onTabShown = {},
            onRefresh = {},
            onLoadMore = {},
            onBack = { reactions = null },
            onUserClick = {},
        )
        return
    }
    val listState = rememberLazyListState()
    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = false,
        onRefresh = {},
        title = { Text(stringResource(GalleryScenario.PostDetail.title)) },
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            items(posts, key = { it.id }) { post ->
                Column {
                    PostDetail(
                        post = post,
                        callbacks = PostCallbacks(
                            onMediaClick = navigator::openMedia,
                            onAction = { target, type ->
                                val action = type.toggleOn(target)
                                posts = posts.map { it.withAction(target.statusId, action) }
                            },
                            onReply = {},
                        ),
                        reactionDetail = true,
                        onReactionCountClick = { _, type ->
                            reactions = if (type == PostActionType.Boost) PostReactionsTab.Boosts else PostReactionsTab.Favourites
                        },
                    )
                    HorizontalDivider(thickness = 4.dp)
                }
            }
        }
    }
}

/** 投稿欄と同じシートを開く。選んだ絵文字・メディアは数えて出すだけ (アップロードはしない)。 */
@Composable
private fun PickerSheets(emojis: List<CustomEmoji>) {
    var emojiOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(listOf<String>()) }
    var media by remember { mutableStateOf(setOf<Uri>()) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { emojiOpen = true }) { Text("Emoji sheet") }
        Button(onClick = { mediaOpen = true }, enabled = isEmbeddedPhotoPickerAvailable()) { Text("Media sheet") }
        Text("Emoji: ${picked.joinToString(" ") { ":$it:" }}")
        Text("Media: ${media.size}")
    }
    if (emojiOpen) {
        EmojiPickerSheet(emojis = emojis, onSelect = { picked = picked + it.shortcode }, onDismiss = { emojiOpen = false })
    }
    if (mediaOpen) {
        MediaPickerSheet(
            selected = media,
            maxItems = 4,
            onSelect = { media = media + it },
            onDeselect = { media = media - it.toSet() },
            onDismiss = { mediaOpen = false },
            onError = { mediaOpen = false },
        )
    }
}
