package es.aria_on_the_planet.quasar.ui.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.data.media.ImagePrepareOptions
import es.aria_on_the_planet.quasar.ui.account.AccountUiContext
import es.aria_on_the_planet.quasar.ui.component.account.LocalAccountActions
import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.data.account.AccountKey
import es.aria_on_the_planet.quasar.di.AppContainer
import es.aria_on_the_planet.quasar.domain.model.NotificationType
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.PostActionType
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.domain.model.settings.AccountSettings
import es.aria_on_the_planet.quasar.domain.model.settings.DeviceSettings
import es.aria_on_the_planet.quasar.domain.model.settings.ColumnLayout
import es.aria_on_the_planet.quasar.ui.component.layout.LocalFloatingPanelInset
import es.aria_on_the_planet.quasar.ui.timeline.visibleColumnCount
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.ui.component.post.LocalOwnPostActions
import es.aria_on_the_planet.quasar.ui.composer.ComposerScreen
import es.aria_on_the_planet.quasar.ui.composer.DraftStore
import es.aria_on_the_planet.quasar.ui.composer.PostEditorHost
import es.aria_on_the_planet.quasar.ui.follows.FollowListScreen
import es.aria_on_the_planet.quasar.ui.follows.FollowListTab
import es.aria_on_the_planet.quasar.ui.follows.FollowListViewModel
import es.aria_on_the_planet.quasar.ui.hashtag.HashtagScreen
import es.aria_on_the_planet.quasar.ui.hashtag.HashtagViewModel
import es.aria_on_the_planet.quasar.ui.personal.FollowRequestsScreen
import es.aria_on_the_planet.quasar.ui.personal.FollowRequestsViewModel
import es.aria_on_the_planet.quasar.ui.personal.PersonalPostList
import es.aria_on_the_planet.quasar.ui.personal.PersonalPostsScreen
import es.aria_on_the_planet.quasar.ui.personal.PersonalPostsViewModel
import es.aria_on_the_planet.quasar.ui.search.SearchScreen
import es.aria_on_the_planet.quasar.ui.search.SearchViewModel
import es.aria_on_the_planet.quasar.ui.login.LoginScreen
import es.aria_on_the_planet.quasar.ui.login.ReloginTarget
import es.aria_on_the_planet.quasar.ui.mediaviewer.mediaViewerEntry
import es.aria_on_the_planet.quasar.ui.mediaviewer.openMedia
import es.aria_on_the_planet.quasar.ui.mediaviewer.MediaViewerArgs
import es.aria_on_the_planet.quasar.ui.openlink.OpenLinkScreen
import es.aria_on_the_planet.quasar.ui.openlink.OpenLinkViewModel
import es.aria_on_the_planet.quasar.ui.openlink.hashtagOfUrl
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
import es.aria_on_the_planet.quasar.ui.settings.ExperimentalSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.LicenseDetailPage
import es.aria_on_the_planet.quasar.ui.settings.LicensesPage
import es.aria_on_the_planet.quasar.ui.settings.PostingSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.SettingsTopPage
import es.aria_on_the_planet.quasar.ui.settings.TranslationSettingsPage
import es.aria_on_the_planet.quasar.ui.component.post.LocalPostTranslations
import es.aria_on_the_planet.quasar.ui.settings.TimelineSettingsPage
import es.aria_on_the_planet.quasar.ui.settings.UpdateAccountSettings
import es.aria_on_the_planet.quasar.ui.settings.PostMenuSettingsPage
import es.aria_on_the_planet.quasar.ui.timeline.toDrawerAccounts
import es.aria_on_the_planet.quasar.ui.timeline.TimelineScreen
import es.aria_on_the_planet.quasar.ui.timeline.TimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * アプリの全画面。画面の行き来は Navigation 3 のバックスタック ([Route]) で、Activity は MainActivity 1 つ。
 * 画面 (`ProfileScreen` 等) へのコールバックは [Navigator] から組み立て、画面の中では [LocalNavigator] でも引ける。
 *
 * * アカウントごとに作り直される (MainActivity の `AccountScope`)。ログイン中は `[Timeline]`、ログアウト中は `[Login]` から始まる。
 *   アカウントの追加はログイン中に `Login` を積み、ログインできたら使うアカウントが変わって作り直される。
 * * ViewModel・`rememberSaveable` は画面 (エントリ) ごと。下に積まれた画面はコンポジションから外れる
 *   (ストリーミング・インライン再生は今までの「別の Activity を開くと止まる」と同じく止まる)。
 * * ログイン中のアカウントの依存 ([AccountUiContext]) を各画面に渡す。ViewModel にはそのデータ層の分 ([AccountContext]) を渡し、
 *   ViewModel は寿命の間ずっと同じものを使う。
 * * 書きかけの投稿 (`DraftStore`) はアカウントごとにここで開く (どの画面から復元されても使えるように)。
 *
 * @param account 使っているアカウントの依存 (ログアウト中は `null`)。作り直されるまで変わらない。
 * @param externalLinks 他のアプリから「Quasar で開く」に共有された URL・`@user@domain` (`linkQueryOf`)。
 *   今の画面の上に開く (ログアウト中なら Toast)。
 * @param onExit 一番下の画面で「戻る」(Activity を閉じる)。
 */
@Composable
fun QuasarNavHost(
    container: AppContainer,
    account: AccountUiContext?,
    externalLinks: Flow<String> = emptyFlow(),
    onExit: () -> Unit,
) {
    val current = account
    val backStack = rememberNavBackStack(if (current == null) Route.Login else Route.Timeline)

    // ログアウト中はログインの画面だけ (復元した状態が別の形でも直す)
    LaunchedEffect(current) {
        if (current == null && backStack.toList() != listOf(Route.Login)) backStack.resetTo(Route.Login)
    }
    LaunchedEffect(current) {
        if (current != null) {
            current.draftStore.open()
            current.panelDraftStore.open()
        }
    }

    val context = LocalContext.current
    LaunchedEffect(externalLinks) {
        externalLinks.collect { query ->
            if (current == null) {
                Toast.makeText(context, R.string.open_link_not_logged_in, Toast.LENGTH_SHORT).show()
            } else {
                // タグのページは検索では引けないので URL から直接開く。同じものを続けて共有されたら積まない
                val route = hashtagOfUrl(query)?.let(Route::Hashtag) ?: Route.OpenLink(query)
                if (backStack.lastOrNull() != route) backStack.add(route)
            }
        }
    }
    // 幅の広い画面では、ポスト・プロフィール等をタイムラインの上に浮かぶパネルで開く (docs/tablet-layout.md)
    val panel = remember(backStack) { DetailPanelState(pop = { backStack.removeLastOrNull() }) }
    val navigator = remember(backStack, context) { BackStackNavigator(backStack, context, panel, onExit) }
    val backDamping = remember { PredictiveBackDamping() }
    val settings by container.deviceSettings.settings.collectAsStateWithLifecycle()
    SideEffect {
        settings?.let {
            backDamping.settings = it.predictiveBack
            panel.backSettings = it.predictiveBack
        }
    }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val panelEnabled = visibleColumnCount(settings?.display?.columnLayout ?: ColumnLayout.Auto, windowWidth) >= 2
    // メディアビューアは下の画面に重ねる。詳細のパネルも下の画面 (タイムライン) に重ねる
    val sceneStrategies = remember(panel, panelEnabled) {
        listOf(OverlaySceneStrategy<NavKey>(), DetailPanelSceneStrategy(panel, panelEnabled))
    }
    CompositionLocalProvider(
        LocalNavigator provides navigator,
        // ポストのメニューのブロック等・ブラウザで開く。ログアウト中は無い
        LocalAccountActions provides current?.accountActions,
        // 自分のポストの編集・削除。編集の投稿欄はどの画面から開いても全画面の上に重ねる (PostEditorHost)
        LocalOwnPostActions provides current?.ownPostActions,
        LocalPostTranslations provides container.postTranslations,
        LocalFloatingPanelInset provides panel.coveredWidth,
    ) {
        Box(Modifier.fillMaxSize()) {
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
                sceneStrategies = sceneStrategies,
                transitionSpec = { pushTransition() },
                popTransitionSpec = { popTransition() },
                predictivePopTransitionSpec = { predictivePopTransition(backDamping.settings.transitionMillis) },
                entryProvider = entryProvider {
                    entry<Route.Login> {
                        // ログイン中なら追加のログイン (戻るボタンで今のアカウントに戻る)
                        LoginScreen(container.oauthLogin, onBack = if (current != null) navigator::back else null)
                    }
                    entry<Route.Relogin> {
                        WithSession(current) { account ->
                            val stored by container.accountStore.state.collectAsStateWithLifecycle()
                            val target = stored.find(account.account.key)
                            LoginScreen(
                                container.oauthLogin,
                                onBack = navigator::back,
                                relogin = target?.let { ReloginTarget(it.key, it.serverUrl, it.label) },
                            )
                        }
                    }
                    entry<Route.Timeline> {
                        WithSession(current) { TimelineEntry(container, it, onAddAccount = { backStack.add(Route.Login) }) }
                    }
                    entry<Route.Profile>(metadata = DetailPanelSceneStrategy.panel()) { key -> WithSession(current) { ProfileEntry(container, it, key, navigator) } }
                    entry<Route.FollowList>(metadata = DetailPanelSceneStrategy.panel()) { key -> WithSession(current) { FollowListEntry(it, key, navigator) } }
                    entry<Route.Hashtag>(metadata = DetailPanelSceneStrategy.panel()) { key -> WithSession(current) { HashtagEntry(it, key, navigator) } }
                    entry<Route.PersonalPosts>(metadata = DetailPanelSceneStrategy.panel()) { key ->
                        WithSession(current) { PersonalPostsEntry(it, key, navigator) }
                    }
                    entry<Route.Search>(metadata = DetailPanelSceneStrategy.panel()) {
                        WithSession(current) { SearchEntry(it, navigator) }
                    }
                    entry<Route.FollowRequests>(metadata = DetailPanelSceneStrategy.panel()) {
                        WithSession(current) { FollowRequestsEntry(it, navigator) }
                    }
                    entry<Route.AccountReactions>(metadata = DetailPanelSceneStrategy.panel()) { key ->
                        WithSession(current) { AccountReactionsEntry(it, key, navigator) }
                    }
                    entry<Route.PostReactions>(metadata = DetailPanelSceneStrategy.panel()) { key ->
                        WithSession(current) { PostReactionsEntry(it, key, navigator) }
                    }
                    entry<Route.Post>(metadata = DetailPanelSceneStrategy.panel()) { key -> WithSession(current) { PostEntry(it, key, navigator) } }
                    entry<Route.OpenLink>(metadata = DetailPanelSceneStrategy.panel()) { key ->
                        WithSession(current) { account ->
                            OpenLinkEntry(account, key, navigator) { backStack.replace(key, it) }
                        }
                    }
                    entry<Route.Composer> { key -> WithSession(current) { ComposerEntry(it, key, navigator) } }
                    mediaViewerEntry(container, onBack = navigator::back)
                    settingsEntries(container, navigator) { backStack.add(it) }
                },
            )
            current?.let { PostEditorHost(it.postEditStore) }
        }
    }
}

/** ログイン中だけ中身を出す (ログアウト中に復元されたエントリは、バックスタックを作り直すまで何も出さない)。 */
@Composable
private fun WithSession(account: AccountUiContext?, content: @Composable (AccountUiContext) -> Unit) {
    if (account != null) content(account)
}

private fun NavBackStack<NavKey>.resetTo(root: Route) {
    clear()
    add(root)
}

/**
 * [from] を [to] に置き換える ([from] が既に無ければ何もしない)。すぐ下が [to] なら [from] を外すだけ
 * (同じキーを隣り合わせに積まない。開いていたポストを共有から開き直したとき)。
 */
private fun NavBackStack<NavKey>.replace(from: Route, to: Route) {
    val index = lastIndexOf(from)
    when {
        index < 0 -> Unit
        getOrNull(index - 1) == to -> removeAt(index)
        else -> set(index, to)
    }
}

/** [Navigator] の本体: バックスタックに積む。 */
private class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
    private val context: Context,
    private val panel: DetailPanelState,
    private val onExit: () -> Unit,
) : Navigator {
    override fun openProfile(user: User) {
        backStack.add(Route.Profile(user.inServerId))
    }

    override fun openPost(statusId: String) {
        backStack.add(Route.Post(statusId))
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

    override fun openComposer(panelDraft: Boolean) {
        backStack.add(Route.Composer(panelDraft))
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

    override fun openPersonalPosts(list: PersonalPostList) {
        backStack.add(Route.PersonalPosts(list))
    }

    override fun openFollowRequests() {
        backStack.add(Route.FollowRequests)
    }

    override fun openSearch() {
        backStack.add(Route.Search)
    }

    override fun openRelogin() {
        backStack.add(Route.Relogin)
    }

    override fun back() {
        // 詳細のパネルの画面はパネルが閉じるアニメーションをしてから外す
        if (backStack.lastOrNull()?.opensInPanel() == true && panel.requestBack()) return
        if (backStack.size > 1) backStack.removeLastOrNull() else onExit()
    }
}

@Composable
private fun TimelineEntry(container: AppContainer, account: AccountUiContext, onAddAccount: () -> Unit) {
    val viewModel: TimelineViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                TimelineViewModel(
                    account.account,
                    account.account.settings.loaded,
                )
            }
        },
    )
    // ドロワーのアカウントの切り替え (保存している表示用の情報で描く)
    val stored by container.accountStore.state.collectAsStateWithLifecycle()
    val accountManager = container.accountManager
    val switcher = remember(stored, account) {
        stored.toDrawerAccounts(
            currentKey = account.account.key.value,
            onSwitch = { key -> accountManager.switchTo(AccountKey(key)) },
            onAdd = onAddAccount,
        )
    }
    TimelineScreen(
        viewModel = viewModel,
        drafts = account.draftStore,
        accounts = switcher,
    )
}

/** この画面の下書き: 詳細のパネルの中ならパネルのもの、それ以外はアカウントのもの。 */
@Composable
private fun draftsHere(account: AccountUiContext): DraftStore =
    if (LocalInDetailPanel.current) account.panelDraftStore else account.draftStore

/** クイック投稿バーの無い画面からの返信: 下書き (この画面のもの) の返信先にして投稿画面で書く。 */
@Composable
private fun replyInComposer(account: AccountUiContext, navigator: Navigator): (Post) -> Unit {
    val inPanel = LocalInDetailPanel.current
    val drafts = draftsHere(account)
    return { post ->
        drafts.open().startReply(post)
        navigator.openComposer(panelDraft = inPanel)
    }
}

/** クイック投稿バーの無い画面からの引用 (ブーストの長押し): 下書きの引用先にして投稿画面で書く。 */
@Composable
private fun quoteInComposer(account: AccountUiContext, navigator: Navigator): (Post) -> Unit {
    val inPanel = LocalInDetailPanel.current
    val drafts = draftsHere(account)
    return { post ->
        drafts.open().startQuote(post)
        navigator.openComposer(panelDraft = inPanel)
    }
}

@Composable
private fun ProfileEntry(container: AppContainer, account: AccountUiContext, key: Route.Profile, navigator: Navigator) {
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    account = account.account,
                    accountId = key.accountId,
                    instanceRepository = container.instanceRepository,
                    emojiRepository = container.emojiRepository,
                    openUploadSource = container::uploadSource,
                    prepareImage = { source, limits ->
                        container.imageReencoder.prepare(
                            source,
                            limits,
                            ImagePrepareOptions(compressOversized = true, stripMetadata = false),
                        )
                    },
                )
            }
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
        onReply = replyInComposer(account, navigator),
        onQuote = quoteInComposer(account, navigator),
    )
}

@Composable
private fun FollowListEntry(account: AccountUiContext, key: Route.FollowList, navigator: Navigator) {
    val viewModel: FollowListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FollowListViewModel(account.account, key.accountId) }
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
private fun HashtagEntry(account: AccountUiContext, key: Route.Hashtag, navigator: Navigator) {
    val viewModel: HashtagViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HashtagViewModel(account.account, key.name) }
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
        onReply = replyInComposer(account, navigator),
        onQuote = quoteInComposer(account, navigator),
    )
}

@Composable
private fun PersonalPostsEntry(account: AccountUiContext, key: Route.PersonalPosts, navigator: Navigator) {
    val viewModel: PersonalPostsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PersonalPostsViewModel(account.account, key.list) }
        },
    )
    PersonalPostsScreen(
        viewModel = viewModel,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = navigator::openPost,
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onReply = replyInComposer(account, navigator),
        onQuote = quoteInComposer(account, navigator),
    )
}

@Composable
private fun SearchEntry(account: AccountUiContext, navigator: Navigator) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(account.account) }
        },
    )
    SearchScreen(
        viewModel = viewModel,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = navigator::openPost,
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onReply = replyInComposer(account, navigator),
        onQuote = quoteInComposer(account, navigator),
    )
}

@Composable
private fun FollowRequestsEntry(account: AccountUiContext, navigator: Navigator) {
    val viewModel: FollowRequestsViewModel = viewModel(
        factory = viewModelFactory { initializer { FollowRequestsViewModel(account.account) } },
    )
    FollowRequestsScreen(viewModel = viewModel, onBack = navigator::back, onUserClick = navigator::openProfile)
}

@Composable
private fun AccountReactionsEntry(
    account: AccountUiContext,
    key: Route.AccountReactions,
    navigator: Navigator,
) {
    val viewModel: AccountReactionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AccountReactionsViewModel(
                    account = account.account,
                    accountId = key.accountId,
                    type = key.type,
                    statusIds = key.statusIds,
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
        onReply = replyInComposer(account, navigator),
        onQuote = quoteInComposer(account, navigator),
    )
}

@Composable
private fun PostEntry(account: AccountUiContext, key: Route.Post, navigator: Navigator) {
    val viewModel: PostViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PostViewModel(
                    account = account.account,
                    statusId = key.statusId,
                )
            }
        },
    )
    PostScreen(
        viewModel = viewModel,
        drafts = draftsHere(account),
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
        onPostClick = { id -> if (id != key.statusId) navigator.openPost(id) },
        onMediaClick = navigator::openMedia,
        onHashtagClick = navigator::openHashtag,
        onReactionCountClick = { post, type ->
            val tab = if (type == PostActionType.Boost) PostReactionsTab.Boosts else PostReactionsTab.Favourites
            navigator.openPostReactions(post, tab)
        },
    )
}

@Composable
private fun PostReactionsEntry(account: AccountUiContext, key: Route.PostReactions, navigator: Navigator) {
    val viewModel: PostReactionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PostReactionsViewModel(account.account, key.statusId) }
        },
    )
    PostReactionsScreen(
        viewModel = viewModel,
        initialTab = key.tab,
        onBack = navigator::back,
        onUserClick = navigator::openProfile,
    )
}

/** 共有された URL を開く途中の画面。引けたら行き先の画面に置き換える ([onResolved])。 */
@Composable
private fun OpenLinkEntry(
    account: AccountUiContext,
    key: Route.OpenLink,
    navigator: Navigator,
    onResolved: (Route) -> Unit,
) {
    val viewModel: OpenLinkViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OpenLinkViewModel(account.account, key.query) }
        },
    )
    OpenLinkScreen(viewModel = viewModel, onBack = navigator::back, onResolved = onResolved)
}

/** 投稿画面 (単独)。投稿できたら閉じる。閉じても下書きは残す。 */
@Composable
private fun ComposerEntry(account: AccountUiContext, key: Route.Composer, navigator: Navigator) {
    val drafts = if (key.panelDraft) account.panelDraftStore else account.draftStore
    val draft by drafts.current.collectAsStateWithLifecycle()
    LaunchedEffect(drafts) {
        drafts.published.collect { navigator.back() }
    }
    draft?.let {
        ComposerScreen(
            session = it,
            onClose = navigator::back,
            onFinish = {
                drafts.finish()
                navigator.back()
            },
            keepDraftOnClose = true,
        )
    }
}

/** 設定のページ。「アカウントの設定」のページは使っているアカウントの設定を、それ以外は端末共通の設定を読み書きする。 */
private fun EntryProviderScope<NavKey>.settingsEntries(
    container: AppContainer,
    navigator: Navigator,
    navigate: (Route) -> Unit,
) {
    val update = container.deviceSettings::launchUpdate
    entry<Route.Settings> {
        val stored by container.accountStore.active.collectAsStateWithLifecycle()
        SettingsTopPage(
            account = stored?.label,
            translationAvailable = container.postTranslations != null,
            onNavigate = navigate,
            onLogout = container.accountManager::logoutActive,
            onRelogin = { navigate(Route.Relogin) },
            onBack = navigator::back,
        )
    }
    entry<Route.SettingsDisplay> {
        WithDeviceSettings(container) { DisplaySettingsPage(it.display, update, onNavigate = navigate, onBack = navigator::back) }
    }
    entry<Route.SettingsAnimation> {
        WithDeviceSettings(container) { AnimationSettingsPage(it.display, update, onBack = navigator::back) }
    }
    entry<Route.SettingsTimelines> {
        WithAccountSettings(container) { settings, updateAccount ->
            TimelineSettingsPage(settings, updateAccount, onBack = navigator::back)
        }
    }
    entry<Route.SettingsPostMenu> {
        WithDeviceSettings(container) { PostMenuSettingsPage(it.hiddenPostMenuItems, update, onBack = navigator::back) }
    }
    entry<Route.SettingsPosting> {
        WithAccountSettings(container) { settings, updateAccount ->
            PostingSettingsPage(settings, updateAccount, onBack = navigator::back)
        }
    }
    entry<Route.SettingsConfirmations> {
        WithDeviceSettings(container) { ConfirmationSettingsPage(it.confirmations, update, onBack = navigator::back) }
    }
    entry<Route.SettingsExperimental> { ExperimentalSettingsPage(onNavigate = navigate, onBack = navigator::back) }
    entry<Route.SettingsTranslation> {
        val translations = container.postTranslations
        if (translations == null) {
            LaunchedEffect(Unit) { navigator.back() }
        } else {
            WithDeviceSettings(container) { TranslationSettingsPage(it.translation, translations, update, onBack = navigator::back) }
        }
    }
    entry<Route.SettingsLicenses> {
        LicensesPage(container.licenseRepository, onOpen = { navigate(Route.SettingsLicense(it)) }, onBack = navigator::back)
    }
    entry<Route.SettingsLicense> { key ->
        LicenseDetailPage(container.licenseRepository, key.id, onBack = navigator::back)
    }
}

/**
 * 今の端末共通の設定で [content] を描く。設定はエントリの中で読む: `NavDisplay` は積まれたエントリ (中身のラムダごと) を
 * 使い回すので、`entryProvider` の外で読んだ値を渡すと、開いたときの値のまま変わらない。
 */
@Composable
private fun WithDeviceSettings(container: AppContainer, content: @Composable (DeviceSettings) -> Unit) {
    val settings by container.deviceSettings.settings.collectAsStateWithLifecycle()
    content(settings ?: DeviceSettings())
}

/**
 * 使っているアカウントの設定と、それを変える関数で [content] を描く ([WithDeviceSettings] と同じくエントリの中で読む)。
 * ログアウト中・読み込み前は何も出さない。
 */
@Composable
private fun WithAccountSettings(
    container: AppContainer,
    content: @Composable (AccountSettings, UpdateAccountSettings) -> Unit,
) {
    val active by container.accountManager.active.collectAsStateWithLifecycle()
    val store = active?.context?.account?.settings ?: return
    val settings by store.settings.collectAsStateWithLifecycle()
    settings?.let { content(it, store::launchUpdate) }
}
