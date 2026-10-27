package es.aria_on_the_planet.quasar.ui.navigation

import es.aria_on_the_planet.quasar.data.repository.AccountRepository
import es.aria_on_the_planet.quasar.data.session.Session
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.ui.component.account.AccountActions
import es.aria_on_the_planet.quasar.ui.component.account.AccountMenuTarget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** ログイン中の [session] でのアカウントへの操作 ([AccountActions])。`QuasarNavHost` が提供する。 */
class SessionAccountActions(
    private val session: Session,
    private val repository: AccountRepository,
) : AccountActions {
    override val serverUrl: String get() = session.serverUrl

    override val myAccountId: String? get() = repository.cachedMe(session)?.user?.inServerId

    override suspend fun target(user: User): AccountMenuTarget {
        if (repository.fetchMe(session).user.inServerId == user.inServerId) return AccountMenuTarget.Me
        val relationship = repository.cachedRelationship(user.inServerId)?.takeIf { it.isFresh }?.value
            ?: repository.fetchRelationship(session, user.inServerId)
            ?: error("No relationship for the account")
        return AccountMenuTarget.Other(relationship)
    }

    // 押した画面 (ポストの行) が先に消えても送信は止めない
    override suspend fun perform(user: User, action: AccountAction): Relationship = withContext(NonCancellable) {
        val id = user.inServerId
        when (action.type) {
            AccountActionType.Block -> repository.setBlocking(session, id, block = !action.undo)
            AccountActionType.Mute -> repository.setMuting(session, id, mute = !action.undo)
            AccountActionType.HideBoosts -> repository.setShowingReblogs(session, id, show = action.undo)
        }
    }
}
