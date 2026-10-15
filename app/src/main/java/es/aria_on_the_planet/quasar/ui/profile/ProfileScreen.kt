package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onMediaClick: (MediaAttachment) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // ヘッダが画面外に出たらトップバーに名前を出す
    val headerScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val profile = state.profile.profile

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    AnimatedVisibility(
                        visible = profile != null && headerScrolledAway,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        if (profile != null) ProfileTitle(profile)
                    }
                },
            )
        },
    ) { padding ->
        val posts = state.posts
        val isRefreshing = (state.profile.isLoading || posts.isRefreshing) && profile != null
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            when {
                profile == null && state.profile.error != null ->
                    CenteredMessage(stringResource(R.string.profile_error, state.profile.error!!))

                profile == null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }

                else -> PostList(
                    posts = posts.posts,
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    isLoadingMore = posts.isLoadingMore,
                    onLoadMore = viewModel::loadMore,
                    onUserClick = onUserClick,
                    onMediaClick = onMediaClick,
                    header = {
                        item(key = "profile_header", contentType = "profile_header") {
                            ProfileHeader(profile, followersYouFollow = state.extras.followersYouFollow)
                        }
                        // 通常の一覧にも同じポストが含まれうるので、ピン留めは 1 アイテムにまとめてキー衝突を避ける
                        if (state.extras.pinnedPosts.isNotEmpty()) {
                            item(key = "pinned_posts", contentType = "pinned_posts") {
                                PinnedPosts(
                                    posts = state.extras.pinnedPosts,
                                    onUserClick = onUserClick,
                                    onMediaClick = onMediaClick,
                                )
                            }
                        }
                        if (posts.posts.isEmpty()) {
                            item(key = "posts_status", contentType = "status") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        !posts.loaded || posts.isRefreshing -> LoadingIndicator()
                                        posts.error != null -> Text(stringResource(R.string.timeline_error, posts.error))
                                        else -> Text(
                                            stringResource(R.string.profile_no_posts),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileTitle(profile: Profile) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        EmojiText(
            text = profile.user.displayName.ifBlank { profile.user.screenName },
            emojis = profile.user.emojis,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.profile_posts_count, formatCount(profile.postsCount, locale)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
