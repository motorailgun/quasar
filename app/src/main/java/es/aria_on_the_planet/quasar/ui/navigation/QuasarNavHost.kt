package es.aria_on_the_planet.quasar.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.AppSettings
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.composer.ComposerScreen
import es.aria_on_the_planet.quasar.ui.follows.FollowListScreen
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.follows.FollowListViewModel
import es.aria_on_the_planet.quasar.ui.hashtag.HashtagScreen
import es.aria_on_the_planet.quasar.ui.hashtag.HashtagViewModel
import es.aria_on_the_planet.quasar.ui.login.LoginScreen
import es.aria_on_the_planet.quasar.ui.mediaviewer.mediaViewerEntry
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.ui.post.PostScreen
import es.aria_on_the_planet.quasar.ui.post.PostViewModel
import es.aria_on_the_planet.quasar.ui.profile.ProfileScreen
import es.aria_on_the_planet.quasar.ui.profile.ProfileViewModel
import es.aria_on_the_planet.quasar.ui.reactions.AccountReactionsScreen
import es.aria_on_the_planet.quasar.ui.reactions.AccountReactionsViewModel
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsScreen
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsTab
import es.aria_on_the_planet.quasar.ui.reactions.PostReactionsViewModel
import es.aria_on_the_planet.quasar.ui.settings.AnimationSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.ConfirmationSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.DisplaySettingsPage
import es.aria_on_the_planet.quasar.ui.settings.PostingSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.SettingsTopPage
import es.aria_on_the_planet.quasar.ui.settings.TimelineSettingsPage
import es.aria_on_the_planet.quasar.ui.timeline.TimelineScreen
import es.aria_on_the_planet.quasar.ui.timeline.TimelineViewModel

/**
 * アプリの全画面。画面の行き来は Navigation 3 のバックスタック ([Route]) で、Activity は MainActivity 1 つ。
 * 画面 (`ProfileScreen` 等) へのコールバックは [Navigator] から組み立て、画面の中では [LocalNavigator] でも引ける。
 *
 * * ログイン状態でバックスタックを作り直す (ログイン → `[Timeline]`、ログアウト → `[Login]`)。
 * * ViewModel・`rememberSaveable` は画面 (エントリ) ごと。下に積まれた画面はコンポジションから外れる
 *   (ストリーミング・インライン再生は今までの「別の Activity を開くと止まる」と同じく止まる)。
 * * 書きかけの投稿 (`DraftStore`) はセッションごとにここで開く (どの画面から復元されても使えるように)。
 *
 * @param onExit 一番下の画面で「戻る」(Activity を閉じる)。
 */
@Composable
fun QuasarNavHost(container: AppContainer, onExit: () -> Unit) {
    val session by container.sessionStore.session.collectAsStateWithLifecycle()
    val current = session
    val backStack = rememberNavBackStack(if (current == null) Route.Login else Route.Timeline)

    // ログイン・ログアウトでバックスタックを作り直す (状態から決めるので、復元時に余計な作り直しはしない)
    val loggedIn = current != null
    LaunchedEffect(loggedIn) {
        val wrongRoot = if (loggedIn) Route.Login in backStack else backStack.toList() != listOf(Route.Login)
        if (wrongRoot) backStack.resetTo(if (loggedIn) Route.Timeline else Route.Login)
    }
    LaunchedEffect(current) {
        if (current?.accessToken != null) container.draftStore.open(current)
    }

    val context = LocalContext.current
    val navigator = remember(backStack, context) { BackStackNavigator(backStack, context, onExit) }
    val backDamping = remember { PredictiveBackDamping() }
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    SideEffect { settings?.let { backDamping.settings = it.predictiveBack } }
    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                backDamping.onBack()
                navigator.back()
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                rememberPredictiveBackDampingDecorator(backDamping),
            ),
            // メディアビューアは下の画面に重ねる (それ以外は 1 画面ずつ)
            sceneStrategies = remember { listOf(OverlaySceneStrategy()) },
            transitionSpec = { pushTransition() },
            popTransitionSpec = { popTransition() },
            predictivePopTransitionSpec = { predictivePopTransition(backDamping.settings.transitionMillis) },
            entryProvider = entryProvider {
                entry<Route.Login> { LoginScreen(container.oauthLogin) }
                entry<Route.Timeline> { WithSession(current) { TimelineEntry(container, it) } }
                entry<Route.Profile> { key -> WithSession(current) { ProfileEntry(container, it, key, navigator) } }
                entry<Route.FollowList> { key -> WithSession(current) { FollowListEntry(container, it, key, navigator) } }
                entry<Route.Hashtag> { key -> WithSession(current) { HashtagEntry(container, it, key, navigator) } }
                entry<Route.AccountReactions> { key ->
                    WithSession(current) { AccountReactionsEntry(container, it, key, navigator) }
                }
                entry<Route.PostReactions> { key ->
                    WithSession(current) { PostReactionsEntry(container, it, key, navigator) }
                }
                entry<Route.Post> { key -> WithSession(current) { PostEntry(container, it, key, navigator) } }
                entry<Route.Composer> { ComposerEntry(container, navigator) }
                mediaViewerEntry(container, onBack = navigator::back)
                settingsEntries(container, navigator) { backStack.add(it) }
            },
        )
    }
}

/** ログイン中だけ中身を出す (ログアウト直後、バックスタックを作り直すまでの間は何も出さない)。 */
@Composable
private fun WithSession(session: Session?, content: @Composable (Session) -> Unit) {
    if (session != null) content(session)
}

private fun NavBackStack<NavKey>.resetTo(root: Route) {
    clear()
    add(root)
}

/** [Navigator] の本体: バックスタックに積む。 */
private class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val context: Context,
    private val onExit: () -> Unit,
) : Navigator {
    override fun openProfile(user: User) {
        backStack.add(Route.Profile(user.inServerId))
    }

    override fun openPost(post: Post) {
        backStack.add(Route.Post(post.statusId))
    }

    override fun openHashtag(name: String) {
        backStack.add(Route.Hashtag(name))
    }

    override fun openFollowList(accountId: String, tab: FollowListTab) {
        backStack.add(Route.FollowList(accountId, tab))
    }

    override fun openAccountReactions(account: User, type: NotificationType, posts: List<Post>) {
        backStack.add(Route.AccountReactions(account.inServerId, type, posts.map { it.statusId }.distinct()))
    }

    override fun openPostReactions(post: Post, tab: PostReactionsTab) {
        backStack.add(Route.PostReactions(post.statusId, tab))
    }

    override fun openComposer() {
        backStack.add(Route.Composer)
    }

    override fun openMedia(click: MediaClick) {
        openMedia(context, click, ::openMediaViewer)
    }

    override fun openMediaViewer(args: MediaViewerArgs) {
        backStack.add(Route.MediaViewer(args))
    }

    override fun openSettings() {
        backStack.add(Route.Settings)
    }

    override fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull() else onExit()
    }
}

@Composable
private fun TimelineEntry(container: AppContainer, session: Session) {
    val viewModel: TimelineViewModel = viewModel(
        key = "${session.serverUrl}|${session.accessToken.hashCode()}",
        factory = viewModelFactory {
            initializer {
                TimelineViewModel(
                    session,
                    container.timelineRepository,
                    container.statusRepository,
                    container.notificationRepository,
                    container.streamingClient,
                    container.settingsStore.loaded,
                )
            }
        },
    )
    TimelineScreen(
        viewModel = viewModel,
        drafts = container.draftStore,
        canPost = session.accessToken != null,
    )
}

/** クイック投稿バーの無い画面からの返信: 下書き (共有) の返信先にして投稿画面で書く。 */
private fun replyInComposer(container: AppContainer, session: Session, navigator: Navigator): (Post) -> Unit = { post ->
    container.draftStore.open(session).startReply(post)
    navigator.openComposer()
}

@Composable
private fun ProfileEntry(container: AppContainer, session: Session, key: Route.Profile, navigator: Navigator) {
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ProfileViewModel(session, key.accountId, container.accountRepository, container.statusRepository) }
        },
    )
    ProfileScreen(
        viewModel = viewModel,
        onBack = navigator::back,
        // 同じアカウントのポスト (自分の投稿) からは遷移しない
        onUserClick = { user -> if (user.inServerId != key.accountId) navigator.openProfile(user) },
        onPostClick = navigator::openPost,
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onFollowingClick = { navigator.openFollowList(key.accountId, FollowListTab.Following) },
        onFollowersClick = { navigator.openFollowList(key.accountId, FollowListTab.Followers) },
        onOpenImage = navigator::openMediaViewer,
        onReply = replyInComposer(container, session, navigator).takeIf { viewModel.canInteract },
    )
}

@Composable
private fun FollowListEntry(container: AppContainer, session: Session, key: Route.FollowList, navigator: Navigator) {
    val viewModel: FollowListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FollowListViewModel(session, key.accountId, container.accountRepository) }
        },
    )
    FollowListScreen(
        viewModel = viewModel,
        initialTab = key.tab,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
    )
}

@Composable
private fun HashtagEntry(container: AppContainer, session: Session, key: Route.Hashtag, navigator: Navigator) {
    val viewModel: HashtagViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HashtagViewModel(session, key.name, container.timelineRepository, container.statusRepository) }
        },
    )
    HashtagScreen(
        viewModel = viewModel,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = navigator::openPost,
        onMediaClick = navigator::openMedia,
        // 同じハッシュタグからは遷移しない
        onHashtagClick = { tag -> if (!tag.equals(key.name, ignoreCase = true)) navigator.openHashtag(tag) },
        onReply = replyInComposer(container, session, navigator).takeIf { viewModel.canInteract },
    )
}

@Composable
private fun AccountReactionsEntry(
    container: AppContainer,
    session: Session,
    key: Route.AccountReactions,
    navigator: Navigator,
) {
    val viewModel: AccountReactionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AccountReactionsViewModel(
                    session = session,
                    accountId = key.accountId,
                    type = key.type,
                    statusIds = key.statusIds,
                    accountRepository = container.accountRepository,
                    statusRepository = container.statusRepository,
                )
            }
        },
    )
    AccountReactionsScreen(
        viewModel = viewModel,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = navigator::openPost,
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onReply = replyInComposer(container, session, navigator).takeIf { viewModel.canInteract },
    )
}

@Composable
private fun PostEntry(container: AppContainer, session: Session, key: Route.Post, navigator: Navigator) {
    val viewModel: PostViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PostViewModel(
                    session = session,
                    statusId = key.statusId,
                    repository = container.statusRepository,
                    accountRepository = container.accountRepository,
                    createEditor = { scope, target, onSaved -> container.composerSession(session, scope, target, onSaved) },
                )
            }
        },
    )
    PostScreen(
        viewModel = viewModel,
        drafts = container.draftStore,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = { post -> if (post.statusId != key.statusId) navigator.openPost(post) },
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onReactionCountClick = { post, type ->
            val tab = if (type == PostActionType.Boost) PostReactionsTab.Boosts else PostReactionsTab.Favourites
            navigator.openPostReactions(post, tab)
        },
    )
}

@Composable
private fun PostReactionsEntry(container: AppContainer, session: Session, key: Route.PostReactions, navigator: Navigator) {
    val viewModel: PostReactionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PostReactionsViewModel(session, key.statusId, container.statusRepository) }
        },
    )
    PostReactionsScreen(
        viewModel = viewModel,
        initialTab = key.tab,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
    )
}

/** 投稿画面 (単独)。投稿できたら閉じる。閉じても下書きは残す。 */
@Composable
private fun ComposerEntry(container: AppContainer, navigator: Navigator) {
    val draft by container.draftStore.current.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        container.draftStore.published.collect { navigator.back() }
    }
    draft?.let {
        ComposerScreen(
            session = it,
            onClose = navigator::back,
            onFinish = {
                container.draftStore.finish()
                navigator.back()
            },
            keepDraftOnClose = true,
        )
    }
}

/** 設定のページ。 */
private fun EntryProviderScope<NavKey>.settingsEntries(
    container: AppContainer,
    navigator: Navigator,
    navigate: (Route) -> Unit,
) {
    val update = container.settingsStore::launchUpdate
    entry<Route.Settings> {
        val session by container.sessionStore.session.collectAsStateWithLifecycle()
        SettingsTopPage(
            serverUrl = session?.serverUrl,
            onNavigate = navigate,
            onLogout = container.oauthLogin::logout,
            onBack = navigator::back,
        )
    }
    entry<Route.SettingsDisplay> {
        WithSettings(container) { DisplaySettingsPage(it.display, update, onNavigate = navigate, onBack = navigator::back) }
    }
    entry<Route.SettingsAnimation> {
        WithSettings(container) { AnimationSettingsPage(it.display, update, onBack = navigator::back) }
    }
    entry<Route.SettingsTimelines> { WithSettings(container) { TimelineSettingsPage(it, update, onBack = navigator::back) } }
    entry<Route.SettingsPosting> { WithSettings(container) { PostingSettingsPage(it, update, onBack = navigator::back) } }
    entry<Route.SettingsConfirmations> {
        WithSettings(container) { ConfirmationSettingsPage(it.confirmations, update, onBack = navigator::back) }
    }
}

/**
 * 今の設定で [content] を描く。設定はエントリの中で読む: `NavDisplay` は積まれたエントリ (中身のラムダごと) を
 * 使い回すので、`entryProvider` の外で読んだ値を渡すと、開いたときの値のまま変わらない。
 */
@Composable
private fun WithSettings(container: AppContainer, content: @Composable (AppSettings) -> Unit) {
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
    content(settings ?: AppSettings())
}
