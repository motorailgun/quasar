package es.aria_on_the_planet.quasar.ui.component.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.list.LoadMoreEffect
import es.aria_on_the_planet.quasar.ui.component.post.UserIcon
import es.aria_on_the_planet.quasar.ui.component.text.EmojiText

/**
 * ユーザーの集合 (フォロー / フォロワー一覧など)。データ取得は行わず、末尾付近までスクロールされたら [onLoadMore] を呼ぶ。
 *
 * @param header 一覧の先頭に置くアイテム (読み込み中・空の表示など)。
 * @param trailing 各行の右端 (フォローボタン等を置く想定)。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserList(
    users: List<User>,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onUserClick: (User) -> Unit = {},
    header: (LazyListScope.() -> Unit)? = null,
    trailing: (@Composable (User) -> Unit)? = null,
) {
    LoadMoreEffect(state, onLoadMore)
    LazyColumn(modifier = modifier, state = state, contentPadding = contentPadding) {
        header?.invoke(this)
        items(users, key = { it.inServerId }, contentType = { "user" }) { user ->
            UserListItem(
                user = user,
                onClick = { onUserClick(user) },
                modifier = Modifier.animateItem(),
                trailing = trailing?.let { { it(user) } },
            )
        }
        if (isLoadingMore) {
            item(key = "loading_more", contentType = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

/** ユーザー 1 件 (アイコン / 表示名 / @acct / プロフィール文)。プロフィール文は 3 行まで。 */
@Composable
fun UserListItem(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        UserIcon(user = user, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    UserName(user, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "@${user.screenName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailing?.invoke()
            }
            if (user.note.isNotBlank()) {
                EmojiText(
                    text = user.note,
                    emojis = user.emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * ユーザーの要約 (アイコン / 太字の表示名 / プロフィール文)。アイコンはタイムラインのポストと同じ大きさで、
 * プロフィール文は控えめな色・小さめの文字。行全体のタップで [onClick]。
 */
@Composable
fun UserSummary(
    user: User,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        UserIcon(user = user)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            UserName(user, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            if (user.note.isNotBlank()) {
                EmojiText(
                    text = user.note,
                    emojis = user.emojis,
                    isHtml = true,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
