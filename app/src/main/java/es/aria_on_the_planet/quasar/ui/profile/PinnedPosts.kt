package es.aria_on_the_planet.quasar.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import es.aria_on_the_planet.quasar.ui.component.media.MediaClick
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.layout.CollapsibleContent
import es.aria_on_the_planet.quasar.ui.component.post.OnPostAction
import es.aria_on_the_planet.quasar.ui.component.post.PostCard

/** 画面高に対して、折りたたみ時に見せるピン留めセクションの割合。 */
private const val CollapsedHeightFraction = 0.3f

/** プロフィール上部のピン留めポスト。各ポストに Pinned ラベルを付け、画面高の 30% を超える分は折りたたむ。 */
@Composable
fun PinnedPosts(
    posts: List<Post>,
    modifier: Modifier = Modifier,
    onUserClick: (User) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    onMediaClick: (MediaClick) -> Unit = {},
    onVote: (suspend (Post, List<Int>) -> Boolean)? = null,
    onAction: OnPostAction? = null,
    onReply: ((Post) -> Unit)? = null,
) {
    if (posts.isEmpty()) return
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val collapsedMaxHeight = with(LocalDensity.current) { (windowHeight * CollapsedHeightFraction).toDp() }
    Column(modifier) {
        CollapsibleContent(collapsedMaxHeight = collapsedMaxHeight) {
            Column {
                posts.forEachIndexed { index, post ->
                    PostCard(
                        post = post,
                        pinned = true,
                        onUserClick = onUserClick,
                        onPostClick = onPostClick,
                        onMediaClick = onMediaClick,
                        onVote = onVote,
                        onAction = onAction,
                        onReply = onReply,
                    )
                    if (index != posts.lastIndex) HorizontalDivider()
                }
            }
        }
        HorizontalDivider()
    }
}
