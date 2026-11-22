package es.aria_on_the_planet.quasar.ui.account

import es.aria_on_the_planet.quasar.data.account.AccountContext
import es.aria_on_the_planet.quasar.domain.model.Relationship
import es.aria_on_the_planet.quasar.domain.model.User
import es.aria_on_the_planet.quasar.ui.component.account.AccountAction
import es.aria_on_the_planet.quasar.ui.component.account.AccountActionType
import es.aria_on_the_planet.quasar.ui.component.account.AccountActions
import es.aria_on_the_planet.quasar.ui.component.account.AccountMenuTarget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** ログイン中の [account] でのアカウントへの操作 ([AccountActions])。[AccountUiContext] が持ち、`QuasarNavHost` が提供する。 */
class SessionAccountActions(
    private val account: AccountContext,
) : AccountActions {
    private val repository = account.accountRepository

    override val serverUrl: String get() = account.session.serverUrl

    override val myAccountId: String? get() = repository.cachedMe()?.user?.inServerId

    override suspend fun target(user: User): AccountMenuTarget {
        if (repository.fetchMe().user.inServerId == user.inServerId) return AccountMenuTarget.Me
        val relationship = repository.cachedRelationship(user.inServerId)?.takeIf { it.isFresh }?.value
            ?: repository.fetchRelationship(user.inServerId)
            ?: error("No relationship for the account")
        return AccountMenuTarget.Other(relationship)
    }

    // 押した画面 (ポストの行) が先に消えても送信は止めない
    override suspend fun perform(user: User, action: AccountAction): Relationship = withContext(NonCancellable) {
        val id = user.inServerId
        when (action.type) {
            AccountActionType.Block -> repository.setBlocking(id, block = !action.undo)
            AccountActionType.Mute -> repository.setMuting(id, mute = !action.undo)
            AccountActionType.HideBoosts -> repository.setShowingReblogs(id, show = action.undo)
        }
    }
}
