package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.MediaAttachment
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Profile
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.post.PostList
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailLoading
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailMessage
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailScaffold
import es.aria_on_the_planet.quasar.ui.component.scaffold.DetailTitle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onUserClick: (User) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (MediaAttachment) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val profile = state.profile.profile
    val posts = state.posts
    val onVote: (suspend (Post, List<Int>) -> Boolean)? = if (viewModel.canVote) viewModel::vote else null

    DetailScaffold(
        onBack = onBack,
        listState = listState,
        isRefreshing = (state.profile.isLoading || posts.isRefreshing) && profile != null,
        onRefresh = viewModel::refresh,
        // ヘッダ (先頭アイテム) が画面外に出たらトップバーに名前を出す
        revealTitleAfterIndex = 0,
        revealedTitle = profile?.let { { ProfileTitle(it) } },
    ) {
        when {
            profile == null && state.profile.error != null ->
                DetailMessage(stringResource(R.string.profile_error, state.profile.error!!))

            profile == null -> DetailLoading()

            else -> PostList(
                items = posts.items,
                modifier = Modifier.fillMaxSize(),
                state = listState,
                isLoadingMore = posts.isLoadingMore,
                onLoadMore = viewModel::loadMore,
                onUserClick = onUserClick,
                onPostClick = onPostClick,
                onMediaClick = onMediaClick,
                onVote = onVote,
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
                                onPostClick = onPostClick,
                                onMediaClick = onMediaClick,
                                onVote = onVote,
                            )
                        }
                    }
                    if (posts.items.isEmpty()) {
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

@Composable
private fun ProfileTitle(profile: Profile) {
    val locale = LocalConfiguration.current.locales[0]
    DetailTitle(
        name = profile.user.displayName.ifBlank { profile.user.screenName },
        emojis = profile.user.emojis,
        subtitle = stringResource(R.string.profile_posts_count, formatCount(profile.postsCount, locale)),
    )
}
