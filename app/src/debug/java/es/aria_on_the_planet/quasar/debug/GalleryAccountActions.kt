package es.aria_on_the_planet.quasar.debug

import android.content.Context
import android.widget.Toast
import es.aria_on_the_planet.quasar.domain.model.Post
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.ui.component.account.AccountActions
import es.aria_on_the_planet.quasar.ui.component.account.AccountMenuTarget
import es.aria_on_the_planet.quasar.ui.component.post.OwnPostActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ギャラリーのポストのメニュー用の [AccountActions]。関係は手元だけで持ち (最初は全員フォロー中)、仮の関係の表示が
 * 見えるよう少し待ってから返す。一覧からは消さない。固定データの carol (ID `3`) を自分として扱う (自分のポストのメニューを見るため)。
 */
internal class GalleryAccountActions : AccountActions {
    override val serverUrl: String = "https://gallery.invalid"

    override val myAccountId: String = MyAccountId

    private val relationships = mutableMapOf<String, Relationship>()

    override suspend fun target(user: User): AccountMenuTarget {
        delay(LoadingMillis)
        if (user.inServerId == MyAccountId) return AccountMenuTarget.Me
        return AccountMenuTarget.Other(relationshipOf(user))
    }

    override suspend fun perform(user: User, action: AccountAction): Relationship {
        delay(LoadingMillis)
        return relationshipOf(user).applying(action).also { relationships[user.inServerId] = it }
    }

    private fun relationshipOf(user: User): Relationship =
        relationships[user.inServerId] ?: Relationship(following = true, requested = false, followedBy = false)

    private companion object {
        const val LoadingMillis = 500L

        /** 固定データの carol。 */
        const val MyAccountId = "3"
    }
}

/** ギャラリーの自分のポストの編集・削除。データ層を通さず、押されたことを Toast で出すだけ。 */
internal class GalleryOwnPostActions(private val context: Context) : OwnPostActions {
    override val busyStatusId: StateFlow<String?> = MutableStateFlow(null)

    override fun edit(post: Post) {
        Toast.makeText(context, "Edit ${post.statusId}", Toast.LENGTH_SHORT).show()
    }

    override fun delete(post: Post) {
        Toast.makeText(context, "Delete ${post.statusId}", Toast.LENGTH_SHORT).show()
    }
}

/** [action] を行った後の関係 (サーバと同じく、ブロックするとフォローが切れる)。 */
internal fun Relationship.applying(action: AccountAction): Relationship = when (action.type) {
    AccountActionType.Block -> if (action.undo) copy(blocking = false) else copy(blocking = true, following = false, requested = false)
    AccountActionType.Mute -> copy(muting = !action.undo)
    AccountActionType.HideBoosts -> copy(showingReblogs = action.undo)
}
