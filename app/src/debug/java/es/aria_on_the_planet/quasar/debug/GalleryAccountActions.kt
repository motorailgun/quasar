package es.aria_on_the_planet.quasar.debug

import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.ui.component.account.AccountActions
import es.aria_on_the_planet.quasar.ui.component.account.AccountMenuTarget
import kotlinx.coroutines.delay

/**
 * ギャラリーのポストのメニュー用の [AccountActions]。関係は手元だけで持ち (最初は全員フォロー中)、仮の関係の表示が
 * 見えるよう少し待ってから返す。一覧からは消さない。
 */
internal class GalleryAccountActions : AccountActions {
    override val serverUrl: String = "https://gallery.invalid"

    // ギャラリーのどのアカウントとも一致しない (全員を他人として扱う)
    override val myAccountId: String = "gallery-me"

    private val relationships = mutableMapOf<String, Relationship>()

    override suspend fun target(user: User): AccountMenuTarget {
        delay(LoadingMillis)
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
    }
}

/** [action] を行った後の関係 (サーバと同じく、ブロックするとフォローが切れる)。 */
internal fun Relationship.applying(action: AccountAction): Relationship = when (action.type) {
    AccountActionType.Block -> if (action.undo) copy(blocking = false) else copy(blocking = true, following = false, requested = false)
    AccountActionType.Mute -> copy(muting = !action.undo)
    AccountActionType.HideBoosts -> copy(showingReblogs = action.undo)
}
